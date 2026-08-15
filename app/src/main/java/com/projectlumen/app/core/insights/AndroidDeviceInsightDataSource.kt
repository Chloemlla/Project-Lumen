package com.projectlumen.app.core.insights

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import java.time.ZoneId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class AndroidDeviceInsightDataSource(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val zoneId: () -> ZoneId = ZoneId::systemDefault,
) {
    private val appContext = context.applicationContext
    private val usageStatsManager = appContext.getSystemService(UsageStatsManager::class.java)
    private val packageManager = appContext.packageManager

    suspend fun collect(): DeviceInsightsState = withContext(ioDispatcher) {
        val collectedAt = nowMillis()
        val power = collectPowerSnapshot()
        if (!appContext.hasUsageStatsAccess()) {
            return@withContext DeviceInsightsState(
                availability = DeviceUsageAvailability.USAGE_ACCESS_REQUIRED,
                power = power,
                recommendations = DeviceInsightAnalyzer.recommendations(null, power),
                lastUpdatedAtMillis = collectedAt,
            )
        }
        if (usageStatsManager == null) {
            return@withContext DeviceInsightsState(
                availability = DeviceUsageAvailability.RESTRICTED,
                power = power,
                recommendations = DeviceInsightAnalyzer.recommendations(null, power),
                lastUpdatedAtMillis = collectedAt,
                failureReason = "usage_service_unavailable",
            )
        }

        val periodStart = collectedAt - LOOKBACK_MILLIS
        runCatching {
            collectUsage(periodStart, collectedAt)
        }.fold(
            onSuccess = { usage ->
                DeviceInsightsState(
                    availability = if (usage == null) {
                        DeviceUsageAvailability.EMPTY
                    } else {
                        DeviceUsageAvailability.AVAILABLE
                    },
                    usage = usage,
                    power = power,
                    recommendations = DeviceInsightAnalyzer.recommendations(usage, power),
                    lastUpdatedAtMillis = collectedAt,
                )
            },
            onFailure = { error ->
                DeviceInsightsState(
                    availability = DeviceUsageAvailability.RESTRICTED,
                    power = power,
                    recommendations = DeviceInsightAnalyzer.recommendations(null, power),
                    lastUpdatedAtMillis = collectedAt,
                    failureReason = error.javaClass.simpleName,
                )
            },
        )
    }

    private fun collectUsage(periodStart: Long, periodEnd: Long): DeviceUsageSummary? {
        val timeline = queryForegroundTimeline(periodStart, periodEnd)
        if (timeline.intervals.isNotEmpty()) {
            val descriptors = timeline.intervals
                .asSequence()
                .map(ForegroundInterval::packageName)
                .distinct()
                .associateWith(::resolveDescriptor)
            return DeviceInsightAnalyzer.summarizeUsage(
                intervals = timeline.intervals,
                descriptors = descriptors,
                periodStartMillis = periodStart,
                periodEndMillis = periodEnd,
                zoneId = zoneId(),
                appSwitchCount = timeline.appSwitchCount,
            )
        }
        return queryAggregateFallback(periodStart, periodEnd)
    }

    private fun queryForegroundTimeline(periodStart: Long, periodEnd: Long): ForegroundTimeline {
        val events = usageStatsManager?.queryEvents(periodStart, periodEnd) ?: return ForegroundTimeline()
        val activeComponents = mutableMapOf<String, MutableSet<String>>()
        val startedAt = mutableMapOf<String, Long>()
        val intervals = mutableListOf<ForegroundInterval>()
        var lastForegroundPackage: String? = null
        var appSwitchCount = 0
        val event = UsageEvents.Event()

        fun closePackage(packageName: String, timestamp: Long) {
            val start = startedAt.remove(packageName) ?: return
            activeComponents.remove(packageName)
            if (timestamp > start) {
                intervals += ForegroundInterval(packageName, start, timestamp)
            }
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val timestamp = event.timeStamp.coerceIn(periodStart, periodEnd)
            val packageName = event.packageName?.takeIf(String::isNotBlank)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                event.eventType == UsageEvents.Event.SCREEN_NON_INTERACTIVE
            ) {
                startedAt.keys.toList().forEach { closePackage(it, timestamp) }
                continue
            }
            if (packageName == null || packageName == SYSTEM_PACKAGE) continue
            val componentKey = event.className.orEmpty()
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    val active = activeComponents.getOrPut(packageName) { mutableSetOf() }
                    if (active.isEmpty()) {
                        startedAt[packageName] = timestamp
                        if (lastForegroundPackage != null && lastForegroundPackage != packageName) {
                            appSwitchCount += 1
                        }
                        lastForegroundPackage = packageName
                    }
                    active += componentKey
                }
                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    val active = activeComponents[packageName] ?: continue
                    active -= componentKey
                    if (active.isEmpty()) closePackage(packageName, timestamp)
                }
            }
        }
        startedAt.keys.toList().forEach { closePackage(it, periodEnd) }
        return ForegroundTimeline(intervals = intervals, appSwitchCount = appSwitchCount)
    }

    @Suppress("DEPRECATION")
    private fun queryAggregateFallback(periodStart: Long, periodEnd: Long): DeviceUsageSummary? {
        val stats = usageStatsManager
            ?.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, periodStart, periodEnd)
            .orEmpty()
            .filter { it.totalTimeInForeground > 0L }
        if (stats.isEmpty()) return null
        val periodDuration = periodEnd - periodStart
        val topApps = stats
            .groupBy { it.packageName }
            .mapValues { (_, entries) -> entries.sumOf { it.totalTimeInForeground } }
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
            .take(5)
            .map { (packageName, duration) ->
                val descriptor = resolveDescriptor(packageName)
                AppUsageSummary(
                    label = descriptor.label,
                    category = descriptor.category,
                    foregroundMillis = duration.coerceAtMost(periodDuration),
                )
            }
        return DeviceUsageSummary(
            periodStartMillis = periodStart,
            periodEndMillis = periodEnd,
            totalForegroundMillis = stats.sumOf { it.totalTimeInForeground }.coerceAtMost(periodDuration),
            longestContinuousSessionMillis = 0L,
            lateNightForegroundMillis = 0L,
            appSwitchCount = 0,
            topApps = topApps,
            quality = UsageDataQuality.AGGREGATED_FALLBACK,
        )
    }

    @Suppress("DEPRECATION")
    private fun resolveDescriptor(packageName: String): AppUsageDescriptor {
        val info = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                packageManager.getApplicationInfo(packageName, 0)
            }
        }.getOrNull()
        val label = info
            ?.let { runCatching { packageManager.getApplicationLabel(it).toString() }.getOrNull() }
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.take(MAX_LABEL_LENGTH)
            ?: packageName.substringAfterLast('.').ifBlank { "Unknown app" }
        return AppUsageDescriptor(label = label, category = info.toUsageCategory())
    }

    private fun ApplicationInfo?.toUsageCategory(): AppUsageCategory = when (this?.category) {
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> AppUsageCategory.PRODUCTIVITY
        ApplicationInfo.CATEGORY_SOCIAL -> AppUsageCategory.SOCIAL
        ApplicationInfo.CATEGORY_VIDEO -> AppUsageCategory.VIDEO
        ApplicationInfo.CATEGORY_GAME -> AppUsageCategory.GAME
        ApplicationInfo.CATEGORY_NEWS -> AppUsageCategory.READING
        ApplicationInfo.CATEGORY_MAPS -> AppUsageCategory.NAVIGATION
        ApplicationInfo.CATEGORY_AUDIO -> AppUsageCategory.AUDIO
        else -> AppUsageCategory.OTHER
    }

    @Suppress("DEPRECATION")
    private fun collectPowerSnapshot(): DevicePowerSnapshot {
        val batteryIntent = runCatching {
            appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        val batteryManager = appContext.getSystemService(BatteryManager::class.java)
        val powerManager = appContext.getSystemService(PowerManager::class.java)
        val activityManager = appContext.getSystemService(ActivityManager::class.java)
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val levelPercent = if (level >= 0 && scale > 0) {
            ((level * 100f) / scale).toInt().coerceIn(0, 100)
        } else {
            batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                ?.takeIf { it in 0..100 }
        }
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            ?: BatteryManager.BATTERY_STATUS_UNKNOWN
        val temperature = batteryIntent
            ?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            ?.takeIf { it != Int.MIN_VALUE }
            ?.div(10f)
            ?.takeIf { it in -20f..90f }
        val chargeTime = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            batteryManager?.computeChargeTimeRemaining()?.takeIf { it > 0L }
        } else {
            null
        }
        return DevicePowerSnapshot(
            levelPercent = levelPercent,
            chargeState = status.toChargeState(),
            temperatureCelsius = temperature,
            powerSaveMode = powerManager?.isPowerSaveMode == true,
            thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                powerManager?.currentThermalStatus
            } else {
                null
            },
            appBackgroundRestricted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                activityManager?.isBackgroundRestricted == true
            } else {
                false
            },
            batteryOptimizationExempt = runCatching {
                powerManager?.isIgnoringBatteryOptimizations(appContext.packageName) == true
            }.getOrDefault(false),
            chargeTimeRemainingMillis = chargeTime,
        )
    }

    private fun Int.toChargeState(): BatteryChargeState = when (this) {
        BatteryManager.BATTERY_STATUS_CHARGING -> BatteryChargeState.CHARGING
        BatteryManager.BATTERY_STATUS_FULL -> BatteryChargeState.FULL
        BatteryManager.BATTERY_STATUS_DISCHARGING -> BatteryChargeState.DISCHARGING
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> BatteryChargeState.NOT_CHARGING
        else -> BatteryChargeState.UNKNOWN
    }

    private data class ForegroundTimeline(
        val intervals: List<ForegroundInterval> = emptyList(),
        val appSwitchCount: Int = 0,
    )

    private companion object {
        private const val LOOKBACK_MILLIS = 24L * 60L * 60L * 1_000L
        private const val MAX_LABEL_LENGTH = 80
        private const val SYSTEM_PACKAGE = "android"
    }
}

@Suppress("DEPRECATION")
internal fun Context.hasUsageStatsAccess(): Boolean {
    val appOps = getSystemService(AppOpsManager::class.java) ?: return false
    val mode = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName,
            )
        }
    }.getOrDefault(AppOpsManager.MODE_ERRORED)
    return mode == AppOpsManager.MODE_ALLOWED
}
