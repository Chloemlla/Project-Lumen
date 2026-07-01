package com.projectlumen.app.core.shizuku

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import com.projectlumen.app.core.database.entities.AppSettingsEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.util.concurrent.TimeUnit

class ShizukuCapabilityManager(
    private val context: Context,
) {
    private val _state = MutableStateFlow(ShizukuCapabilityState())
    val state = _state.asStateFlow()
    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
        if (requestCode == PERMISSION_REQUEST_CODE) {
            refreshState()
        }
    }

    init {
        runCatching { Shizuku.addRequestPermissionResultListener(permissionResultListener) }
        refreshState()
    }

    fun refreshState() {
        _state.value = queryState()
    }

    fun requestPermission() {
        val current = queryState()
        _state.value = current
        if (!current.binderAvailable || current.permissionGranted) return
        if (!current.permissionRequestable) return
        runCatching { Shizuku.requestPermission(PERMISSION_REQUEST_CODE) }
            .onFailure { throwable -> _state.value = queryState(error = throwable.message.orEmpty()) }
    }

    suspend fun refreshForegroundContext(): ShizukuForegroundContext? = withContext(Dispatchers.IO) {
        val baseState = queryState()
        if (!baseState.ready) {
            _state.value = baseState
            return@withContext null
        }
        val foregroundContext = latestForegroundContext()
        _state.value = baseState.copy(
            foregroundPackage = foregroundContext?.packageName.orEmpty(),
            foregroundActivity = foregroundContext?.activityName.orEmpty(),
            foregroundCategory = foregroundContext?.category.orEmpty(),
            foregroundShouldDeferSampling = foregroundContext?.shouldDeferSampling == true,
            lastCheckedAt = System.currentTimeMillis(),
            lastError = if (foregroundContext == null) "Foreground context unavailable." else "",
        )
        foregroundContext
    }

    suspend fun refreshSystemContext(settings: AppSettingsEntity): ShizukuSystemContext? = withContext(Dispatchers.IO) {
        val baseState = queryState()
        if (!baseState.ready) {
            _state.value = baseState
            return@withContext null
        }
        val systemContext = latestSystemContext(settings)
        _state.value = baseState.copy(
            deviceInteractive = systemContext.deviceInteractive,
            batteryLevelPercent = systemContext.batteryLevelPercent,
            lowBatteryActive = systemContext.lowBatteryActive,
            powerSaveActive = systemContext.powerSaveActive,
            dndActive = systemContext.dndActive,
            thermalStatus = systemContext.thermalStatus,
            cameraPrivacyEnabled = systemContext.cameraPrivacyEnabled,
            systemShouldDeferSampling = systemContext.shouldDeferSampling,
            lastCheckedAt = System.currentTimeMillis(),
            lastError = "",
        )
        systemContext
    }

    suspend fun shouldDeferSampling(settings: AppSettingsEntity): Boolean {
        if (!settings.shizukuAdvancedModeEnabled) {
            return false
        }
        var shouldDefer = false
        if (settings.shizukuContextAwareSamplingEnabled) {
            shouldDefer = refreshForegroundContext()?.shouldDeferSampling == true
        }
        if (settings.hasEnabledShizukuSystemGuard()) {
            shouldDefer = refreshSystemContext(settings)?.shouldDeferSampling == true || shouldDefer
        }
        return shouldDefer
    }

    fun isReady(): Boolean {
        val current = queryState()
        _state.value = current
        return current.ready
    }

    private fun queryState(error: String = ""): ShizukuCapabilityState {
        val binderAvailable = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!binderAvailable) {
            return ShizukuCapabilityState(
                binderAvailable = false,
                permissionRequestable = false,
                lastCheckedAt = System.currentTimeMillis(),
                lastError = error,
            )
        }
        val permissionGranted = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        val permissionRequestable = !permissionGranted && runCatching {
            !Shizuku.shouldShowRequestPermissionRationale()
        }.getOrDefault(false)
        return ShizukuCapabilityState(
            binderAvailable = true,
            permissionGranted = permissionGranted,
            permissionRequestable = permissionRequestable,
            serverVersion = runCatching { Shizuku.getVersion() }.getOrDefault(0),
            serverUid = runCatching { Shizuku.getUid() }.getOrDefault(0),
            foregroundPackage = _state.value.foregroundPackage,
            foregroundActivity = _state.value.foregroundActivity,
            foregroundCategory = _state.value.foregroundCategory,
            foregroundShouldDeferSampling = _state.value.foregroundShouldDeferSampling,
            deviceInteractive = _state.value.deviceInteractive,
            batteryLevelPercent = _state.value.batteryLevelPercent,
            lowBatteryActive = _state.value.lowBatteryActive,
            powerSaveActive = _state.value.powerSaveActive,
            dndActive = _state.value.dndActive,
            thermalStatus = _state.value.thermalStatus,
            cameraPrivacyEnabled = _state.value.cameraPrivacyEnabled,
            systemShouldDeferSampling = _state.value.systemShouldDeferSampling,
            lastCheckedAt = System.currentTimeMillis(),
            lastError = error,
        )
    }

    private fun latestForegroundContext(): ShizukuForegroundContext? {
        val usageStats = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val nowMillis = System.currentTimeMillis()
        val events = runCatching {
            usageStats.queryEvents(nowMillis - FOREGROUND_QUERY_WINDOW_MILLIS, nowMillis)
        }.getOrNull() ?: return null
        val event = UsageEvents.Event()
        var packageName = ""
        var activityName = ""
        var latestEventAt = 0L
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType != USAGE_EVENT_ACTIVITY_RESUMED) continue
            if (event.timeStamp < latestEventAt) continue
            packageName = event.packageName.orEmpty()
            activityName = event.className.orEmpty()
            latestEventAt = event.timeStamp
        }
        if (packageName.isBlank()) return null
        val category = classifyForegroundContext(packageName, activityName)
        return ShizukuForegroundContext(
            packageName = packageName,
            activityName = activityName,
            category = category,
            shouldDeferSampling = category != CATEGORY_NORMAL,
        )
    }

    private fun latestSystemContext(settings: AppSettingsEntity): ShizukuSystemContext {
        val deviceInteractive = parseDeviceInteractive(runPrivilegedShell("dumpsys power"))
        val batterySnapshot = parseBatterySnapshot(runPrivilegedShell("dumpsys battery"))
        val powerSaveActive = readSettingsInt("global", "low_power") == 1 ||
            parseEnabledSignal(runPrivilegedShell("cmd power get-mode"))
        val dndActive = (readSettingsInt("global", "zen_mode") ?: 0) > 0
        val thermalStatus = parseThermalStatus(runPrivilegedShell("dumpsys thermalservice"))
        val cameraPrivacyEnabled = parseEnabledSignal(runPrivilegedShell("cmd sensorprivacy is-enabled camera"))
        val shouldDeferSampling =
            (settings.shizukuScreenOffGuardEnabled && !deviceInteractive) ||
                (settings.shizukuLowBatteryGuardEnabled && batterySnapshot.lowBatteryActive) ||
                (settings.shizukuPowerSaveGuardEnabled && powerSaveActive) ||
                (settings.shizukuDndGuardEnabled && dndActive) ||
                (settings.shizukuThermalGuardEnabled && thermalStatus >= THERMAL_STATUS_MODERATE) ||
                (settings.shizukuCameraPrivacyGuardEnabled && cameraPrivacyEnabled)
        return ShizukuSystemContext(
            deviceInteractive = deviceInteractive,
            batteryLevelPercent = batterySnapshot.levelPercent,
            lowBatteryActive = batterySnapshot.lowBatteryActive,
            powerSaveActive = powerSaveActive,
            dndActive = dndActive,
            thermalStatus = thermalStatus,
            cameraPrivacyEnabled = cameraPrivacyEnabled,
            shouldDeferSampling = shouldDeferSampling,
        )
    }

    private fun readSettingsInt(namespace: String, key: String): Int? {
        return runPrivilegedShell("settings get $namespace $key")
            ?.lineSequence()
            ?.map { it.trim() }
            ?.firstOrNull { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            ?.toIntOrNull()
    }

    private fun runPrivilegedShell(command: String): String? {
        return runCatching {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            val completed = process.waitFor(COMMAND_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            if (!completed) process.destroy()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val error = process.errorStream.bufferedReader().use { it.readText() }
            "$output\n$error".trim().take(MAX_COMMAND_OUTPUT_CHARS)
        }.getOrNull()
    }

    private fun parseDeviceInteractive(output: String?): Boolean {
        val value = output.orEmpty().lowercase()
        return when {
            "mwakefulness=asleep" in value -> false
            "mwakefulness=dozing" in value -> false
            "display power: state=off" in value -> false
            "minteractive=false" in value -> false
            "mwakefulness=awake" in value -> true
            "display power: state=on" in value -> true
            "minteractive=true" in value -> true
            else -> true
        }
    }

    private fun parseBatterySnapshot(output: String?): BatterySnapshot {
        val value = output.orEmpty()
        val level = Regex("""level:\s*(\d+)""")
            .find(value)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: -1
        val lower = value.lowercase()
        val powered = "ac powered: true" in lower ||
            "usb powered: true" in lower ||
            "wireless powered: true" in lower ||
            "dock powered: true" in lower
        return BatterySnapshot(
            levelPercent = level,
            lowBatteryActive = level in 0..LOW_BATTERY_THRESHOLD_PERCENT && !powered,
        )
    }

    private fun parseThermalStatus(output: String?): Int {
        val value = output.orEmpty()
        val numericStatus = listOf(
            Regex("""mStatus\s*=\s*(\d+)"""),
            Regex("""Thermal Status:\s*(\d+)""", RegexOption.IGNORE_CASE),
            Regex("""status\s*=\s*(\d+)""", RegexOption.IGNORE_CASE),
        ).firstNotNullOfOrNull { regex ->
            regex.find(value)?.groupValues?.getOrNull(1)?.toIntOrNull()
        }
        if (numericStatus != null) return numericStatus
        val lower = value.lowercase()
        return when {
            "critical" in lower -> 4
            "severe" in lower -> 3
            "moderate" in lower -> 2
            "light" in lower -> 1
            else -> 0
        }
    }

    private fun parseEnabledSignal(output: String?): Boolean {
        val value = output.orEmpty().lowercase()
        if (value.isBlank()) return false
        if ("unknown" in value || "unsupported" in value || "usage:" in value || "error" in value) return false
        if (Regex("""\bfalse\b|\bdisabled\b|\boff\b""").containsMatchIn(value)) return false
        return Regex("""\btrue\b|\benabled\b|\b1\b""").containsMatchIn(value)
    }

    private data class BatterySnapshot(
        val levelPercent: Int,
        val lowBatteryActive: Boolean,
    )

    private fun classifyForegroundContext(packageName: String, activityName: String): String {
        val combined = "$packageName $activityName".lowercase()
        return when {
            sensitiveCallHints.any { combined.contains(it) } -> CATEGORY_COMMUNICATION
            sensitiveCameraHints.any { combined.contains(it) } -> CATEGORY_CAMERA
            sensitiveMediaHints.any { combined.contains(it) } -> CATEGORY_MEDIA
            sensitiveGameHints.any { combined.contains(it) } -> CATEGORY_GAME
            else -> CATEGORY_NORMAL
        }
    }

    private companion object {
        private const val PERMISSION_REQUEST_CODE = 42017
        private const val FOREGROUND_QUERY_WINDOW_MILLIS = 10 * 60 * 1000L
        private const val USAGE_EVENT_ACTIVITY_RESUMED = 1
        private const val CATEGORY_NORMAL = "normal"
        private const val CATEGORY_CAMERA = "camera"
        private const val CATEGORY_COMMUNICATION = "communication"
        private const val CATEGORY_MEDIA = "media"
        private const val CATEGORY_GAME = "game"
        private const val COMMAND_TIMEOUT_MILLIS = 1_500L
        private const val MAX_COMMAND_OUTPUT_CHARS = 16_384
        private const val LOW_BATTERY_THRESHOLD_PERCENT = 15
        private const val THERMAL_STATUS_MODERATE = 2

        private val sensitiveCameraHints = listOf("camera", "camerax", "scanner", "barcode", "qr")
        private val sensitiveCallHints = listOf("call", "voip", "meeting", "conference", "telecom", "zoom", "meet")
        private val sensitiveMediaHints = listOf("player", "video", "fullscreen", "youtube", "netflix", "bilibili", "tiktok")
        private val sensitiveGameHints = listOf("game", "unity", "unreal", "tmgp", "mihoyo", "hoyoverse", "netease")
    }
}

private fun AppSettingsEntity.hasEnabledShizukuSystemGuard(): Boolean {
    return shizukuScreenOffGuardEnabled ||
        shizukuLowBatteryGuardEnabled ||
        shizukuPowerSaveGuardEnabled ||
        shizukuDndGuardEnabled ||
        shizukuThermalGuardEnabled ||
        shizukuCameraPrivacyGuardEnabled
}
