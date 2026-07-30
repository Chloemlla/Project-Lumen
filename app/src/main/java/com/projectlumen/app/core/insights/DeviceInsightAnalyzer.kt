package com.projectlumen.app.core.insights

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min

internal object DeviceInsightAnalyzer {
    private const val CONTINUOUS_SESSION_GAP_MILLIS = 2L * 60L * 1_000L
    private const val HIGH_EXPOSURE_MILLIS = 4L * 60L * 60L * 1_000L
    private const val LONG_SESSION_MILLIS = 45L * 60L * 1_000L
    private const val LATE_NIGHT_MILLIS = 30L * 60L * 1_000L
    private const val DOMINANT_APP_MILLIS = 90L * 60L * 1_000L

    fun summarizeUsage(
        intervals: List<ForegroundInterval>,
        descriptors: Map<String, AppUsageDescriptor>,
        periodStartMillis: Long,
        periodEndMillis: Long,
        zoneId: ZoneId,
        appSwitchCount: Int,
    ): DeviceUsageSummary {
        val clipped = intervals.mapNotNull { interval ->
            val start = max(interval.startMillis, periodStartMillis)
            val end = min(interval.endMillis, periodEndMillis)
            if (end > start) interval.copy(startMillis = start, endMillis = end) else null
        }
        val mergedExposure = mergeIntervals(clipped, maxGapMillis = 0L)
        val continuousSessions = mergeIntervals(clipped, maxGapMillis = CONTINUOUS_SESSION_GAP_MILLIS)
        val appDurations = clipped
            .groupBy(ForegroundInterval::packageName)
            .mapValues { (_, appIntervals) ->
                mergeIntervals(appIntervals, maxGapMillis = 0L).sumOf { it.endMillis - it.startMillis }
            }
        val topApps = appDurations.entries
            .asSequence()
            .filter { it.value > 0L }
            .sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
            .take(5)
            .map { (packageName, duration) ->
                val descriptor = descriptors[packageName] ?: AppUsageDescriptor(
                    label = packageName.substringAfterLast('.').ifBlank { "Unknown app" },
                    category = AppUsageCategory.OTHER,
                )
                AppUsageSummary(
                    label = descriptor.label,
                    category = descriptor.category,
                    foregroundMillis = duration,
                )
            }
            .toList()
        return DeviceUsageSummary(
            periodStartMillis = periodStartMillis,
            periodEndMillis = periodEndMillis,
            totalForegroundMillis = mergedExposure.sumOf { it.endMillis - it.startMillis },
            longestContinuousSessionMillis = continuousSessions.maxOfOrNull { it.endMillis - it.startMillis } ?: 0L,
            lateNightForegroundMillis = mergedExposure.sumOf { interval ->
                lateNightOverlapMillis(interval.startMillis, interval.endMillis, zoneId)
            },
            appSwitchCount = appSwitchCount.coerceAtLeast(0),
            topApps = topApps,
            quality = UsageDataQuality.EVENT_TIMELINE,
        )
    }

    fun recommendations(
        usage: DeviceUsageSummary?,
        power: DevicePowerSnapshot,
    ): List<DeviceInsightRecommendation> {
        val result = buildList {
            if (usage != null) {
                if (usage.totalForegroundMillis >= HIGH_EXPOSURE_MILLIS) {
                    add(
                        DeviceInsightRecommendation(
                            kind = DeviceInsightRecommendationKind.HIGH_DAILY_EXPOSURE,
                            priority = InsightPriority.IMPORTANT,
                            value = usage.totalForegroundMillis,
                        ),
                    )
                }
                if (usage.longestContinuousSessionMillis >= LONG_SESSION_MILLIS) {
                    add(
                        DeviceInsightRecommendation(
                            kind = DeviceInsightRecommendationKind.LONG_CONTINUOUS_USE,
                            priority = InsightPriority.IMPORTANT,
                            value = usage.longestContinuousSessionMillis,
                        ),
                    )
                }
                if (usage.lateNightForegroundMillis >= LATE_NIGHT_MILLIS) {
                    add(
                        DeviceInsightRecommendation(
                            kind = DeviceInsightRecommendationKind.LATE_NIGHT_USE,
                            priority = InsightPriority.ATTENTION,
                            value = usage.lateNightForegroundMillis,
                        ),
                    )
                }
                if (usage.appSwitchCount >= 80 && usage.totalForegroundMillis >= 60L * 60L * 1_000L) {
                    add(
                        DeviceInsightRecommendation(
                            kind = DeviceInsightRecommendationKind.FREQUENT_APP_SWITCHING,
                            priority = InsightPriority.ATTENTION,
                            value = usage.appSwitchCount.toLong(),
                        ),
                    )
                }
                usage.topApps.firstOrNull { app ->
                    app.foregroundMillis >= DOMINANT_APP_MILLIS && app.category in VISUALLY_INTENSE_CATEGORIES
                }?.let { app ->
                    add(
                        DeviceInsightRecommendation(
                            kind = DeviceInsightRecommendationKind.DOMINANT_VISUAL_APP,
                            priority = InsightPriority.ATTENTION,
                            value = app.foregroundMillis,
                            subject = app.label,
                        ),
                    )
                }
            }
            val thermalConcern = (power.temperatureCelsius ?: 0f) >= 40f || (power.thermalStatus ?: 0) >= 3
            if (thermalConcern) {
                add(
                    DeviceInsightRecommendation(
                        kind = DeviceInsightRecommendationKind.DEVICE_WARM,
                        priority = InsightPriority.IMPORTANT,
                        value = ((power.temperatureCelsius ?: 0f) * 10).toLong(),
                    ),
                )
            }
            if ((power.levelPercent ?: 101) <= 20 && power.chargeState !in CHARGING_STATES) {
                add(
                    DeviceInsightRecommendation(
                        kind = DeviceInsightRecommendationKind.LOW_BATTERY,
                        priority = InsightPriority.INFO,
                        value = power.levelPercent?.toLong() ?: 0L,
                    ),
                )
            }
            if (power.powerSaveMode || power.appBackgroundRestricted) {
                add(
                    DeviceInsightRecommendation(
                        kind = DeviceInsightRecommendationKind.POWER_RESTRICTION,
                        priority = InsightPriority.ATTENTION,
                    ),
                )
            }
        }
        return if (result.isEmpty() && usage != null) {
            listOf(
                DeviceInsightRecommendation(
                    kind = DeviceInsightRecommendationKind.STABLE_PATTERN,
                    priority = InsightPriority.INFO,
                ),
            )
        } else {
            result.distinctBy(DeviceInsightRecommendation::kind)
        }
    }

    private fun mergeIntervals(
        intervals: List<ForegroundInterval>,
        maxGapMillis: Long,
    ): List<ForegroundInterval> {
        val sorted = intervals
            .asSequence()
            .filter { it.endMillis > it.startMillis }
            .sortedWith(compareBy<ForegroundInterval> { it.startMillis }.thenBy { it.endMillis })
            .toList()
        if (sorted.isEmpty()) return emptyList()
        val merged = mutableListOf<ForegroundInterval>()
        var current = sorted.first()
        for (next in sorted.drop(1)) {
            if (next.startMillis <= current.endMillis + maxGapMillis) {
                current = current.copy(endMillis = max(current.endMillis, next.endMillis))
            } else {
                merged += current
                current = next
            }
        }
        merged += current
        return merged
    }

    private fun lateNightOverlapMillis(startMillis: Long, endMillis: Long, zoneId: ZoneId): Long {
        if (endMillis <= startMillis) return 0L
        val firstDate = Instant.ofEpochMilli(startMillis).atZone(zoneId).toLocalDate().minusDays(1)
        val lastDate = Instant.ofEpochMilli(endMillis).atZone(zoneId).toLocalDate()
        var date = firstDate
        var total = 0L
        while (!date.isAfter(lastDate)) {
            val nightStart = date.atTime(LocalTime.of(22, 0)).atZone(zoneId).toInstant().toEpochMilli()
            val nightEnd = date.plusDays(1).atTime(LocalTime.of(6, 0)).atZone(zoneId).toInstant().toEpochMilli()
            total += (min(endMillis, nightEnd) - max(startMillis, nightStart)).coerceAtLeast(0L)
            date = date.plusDays(1)
        }
        return total
    }

    private val CHARGING_STATES = setOf(BatteryChargeState.CHARGING, BatteryChargeState.FULL)
    private val VISUALLY_INTENSE_CATEGORIES = setOf(
        AppUsageCategory.SOCIAL,
        AppUsageCategory.VIDEO,
        AppUsageCategory.GAME,
        AppUsageCategory.READING,
    )
}
