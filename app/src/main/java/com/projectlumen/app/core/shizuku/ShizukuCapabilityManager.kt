package com.projectlumen.app.core.shizuku

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.Parcel
import android.os.PowerManager
import android.os.SystemClock
import com.projectlumen.app.core.database.entities.AppSettingsEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import kotlin.math.roundToInt

class ShizukuCapabilityManager(
    private val context: Context,
) {
    private val _state = MutableStateFlow(ShizukuCapabilityState())
    val state = _state.asStateFlow()
    private val shellServiceLock = java.lang.Object()
    @Volatile
    private var shellServiceBinder: IBinder? = null
    private val shellServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            synchronized(shellServiceLock) {
                shellServiceBinder = service
                shellServiceLock.notifyAll()
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            synchronized(shellServiceLock) {
                shellServiceBinder = null
                shellServiceLock.notifyAll()
            }
        }
    }
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

    suspend fun collectDeviceDiagnostics(includeUserApps: Boolean): ShizukuDeviceDiagnostics = withContext(Dispatchers.IO) {
        val currentState = queryState()
        _state.value = currentState
        if (!currentState.ready) {
            return@withContext ShizukuDeviceDiagnostics(
                collectedAt = System.currentTimeMillis(),
                shizukuReady = false,
                shizukuServerVersion = currentState.serverVersion,
                shizukuServerUid = currentState.serverUid,
                userAppCount = 0,
                userAppsTruncated = false,
                userApps = emptyList(),
            )
        }
        val installedApps = if (includeUserApps) latestInstalledUserApps() else emptyList()
        ShizukuDeviceDiagnostics(
            collectedAt = System.currentTimeMillis(),
            shizukuReady = true,
            shizukuServerVersion = currentState.serverVersion,
            shizukuServerUid = currentState.serverUid,
            userAppCount = installedApps.size,
            userAppsTruncated = installedApps.size > MAX_DIAGNOSTIC_USER_APPS,
            userApps = installedApps.take(MAX_DIAGNOSTIC_USER_APPS),
        )
    }

    suspend fun applyNativeEyeProtection(settings: AppSettingsEntity, smooth: Boolean = true): Boolean = withContext(Dispatchers.IO) {
        val currentState = queryState()
        val shouldEnable = settings.shizukuAdvancedModeEnabled && settings.shizukuNativeEyeProtectionEnabled
        if (!shouldEnable) {
            if (!_state.value.nativeEyeProtectionApplied) {
                _state.value = currentState.copy(
                    nativeEyeProtectionApplied = false,
                    lastError = "",
                )
                return@withContext true
            }
            if (!currentState.ready) {
                _state.value = currentState.copy(
                    lastError = "Shizuku authorization is required to disable native eye protection.",
                )
                return@withContext false
            }
            val cleared = clearNativeDisplayAdjustments()
            _state.value = queryState(if (cleared) "" else "Unable to disable every native eye protection setting.").copy(
                nativeEyeProtectionApplied = false,
                nativeColorTemperatureKelvin = 0,
                nativeBrightnessPercent = 0,
                nativeExtraDimEnabled = false,
                nativeExtraDimPercent = 0,
            )
            return@withContext cleared
        }

        if (!currentState.ready) {
            _state.value = currentState.copy(
                lastError = "Shizuku authorization is required for native eye protection.",
            )
            return@withContext false
        }

        val target = NativeEyeProtectionTarget(
            colorTemperatureKelvin = settings.shizukuNativeColorTemperatureKelvin.coerceIn(
                MIN_COLOR_TEMPERATURE_KELVIN,
                MAX_COLOR_TEMPERATURE_KELVIN,
            ),
            brightnessPercent = settings.shizukuNativeBrightnessPercent.coerceIn(1, 100),
            extraDimEnabled = settings.shizukuNativeExtraDimEnabled,
            extraDimPercent = settings.shizukuNativeExtraDimPercent.coerceIn(0, 100),
        )
        val applied = applyNativeEyeProtectionTarget(target, smooth)
        _state.value = queryState(if (applied) "" else "Some native display settings were not accepted by this device.").copy(
            nativeEyeProtectionApplied = applied,
            nativeColorTemperatureKelvin = if (applied) target.colorTemperatureKelvin else _state.value.nativeColorTemperatureKelvin,
            nativeBrightnessPercent = if (applied) target.brightnessPercent else _state.value.nativeBrightnessPercent,
            nativeExtraDimEnabled = applied && target.extraDimEnabled,
            nativeExtraDimPercent = if (applied && target.extraDimEnabled) target.extraDimPercent else 0,
        )
        applied
    }

    suspend fun applySystemBrightness(
        percent: Int,
        extraDimPercent: Int? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        val currentState = queryState()
        if (!currentState.ready) {
            _state.value = currentState.copy(
                lastError = "Shizuku authorization is required to adjust system brightness.",
            )
            return@withContext false
        }
        val normalizedPercent = percent.coerceIn(1, 100)
        val normalizedExtraDimPercent = extraDimPercent?.coerceIn(0, 100)
        val brightness = percentToSystemBrightness(normalizedPercent)
        val modeResult = executeShellCommand("settings put system screen_brightness_mode 0")
        val brightnessResult = executeShellCommand("settings put system screen_brightness $brightness")
        val extraDimApplied = normalizedExtraDimPercent?.let {
            setExtraDim(enabled = it > 0, percent = it)
        } ?: true
        val applied = brightnessResult.success && extraDimApplied
        _state.value = queryState(
            when {
                applied -> ""
                !brightnessResult.success -> brightnessResult.error.ifBlank {
                    modeResult.error.ifBlank { "System brightness command failed." }
                }
                normalizedExtraDimPercent != null && !extraDimApplied -> "Extra Dim command failed."
                else -> "System brightness command failed."
            },
        ).copy(
            nativeEyeProtectionApplied = _state.value.nativeEyeProtectionApplied,
            nativeColorTemperatureKelvin = _state.value.nativeColorTemperatureKelvin,
            nativeBrightnessPercent = if (brightnessResult.success) {
                normalizedPercent
            } else {
                _state.value.nativeBrightnessPercent
            },
            nativeExtraDimEnabled = when (normalizedExtraDimPercent) {
                null -> _state.value.nativeExtraDimEnabled
                else -> extraDimApplied && normalizedExtraDimPercent > 0
            },
            nativeExtraDimPercent = when (normalizedExtraDimPercent) {
                null -> _state.value.nativeExtraDimPercent
                else -> if (extraDimApplied) normalizedExtraDimPercent else _state.value.nativeExtraDimPercent
            },
        )
        applied
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
            nativeEyeProtectionApplied = _state.value.nativeEyeProtectionApplied,
            nativeColorTemperatureKelvin = _state.value.nativeColorTemperatureKelvin,
            nativeBrightnessPercent = _state.value.nativeBrightnessPercent,
            nativeExtraDimEnabled = _state.value.nativeExtraDimEnabled,
            nativeExtraDimPercent = _state.value.nativeExtraDimPercent,
            lastCheckedAt = System.currentTimeMillis(),
            lastError = error,
        )
    }

    private suspend fun applyNativeEyeProtectionTarget(target: NativeEyeProtectionTarget, smooth: Boolean): Boolean {
        val start = if (smooth) readCurrentNativeEyeProtectionTarget(target) else target
        val steps = if (smooth) SMOOTH_TRANSITION_STEPS else 1
        var lastFrameApplied = false
        for (step in 1..steps) {
            val fraction = step / steps.toFloat()
            val frame = NativeEyeProtectionTarget(
                colorTemperatureKelvin = interpolate(start.colorTemperatureKelvin, target.colorTemperatureKelvin, fraction),
                brightnessPercent = interpolate(start.brightnessPercent, target.brightnessPercent, fraction),
                extraDimEnabled = target.extraDimEnabled || start.extraDimEnabled,
                extraDimPercent = interpolate(
                    if (start.extraDimEnabled) start.extraDimPercent else 0,
                    if (target.extraDimEnabled) target.extraDimPercent else 0,
                    fraction,
                ),
            )
            val nightDisplayApplied = setNightDisplay(frame.colorTemperatureKelvin)
            val brightnessApplied = setSystemBrightness(frame.brightnessPercent)
            val extraDimApplied = setExtraDim(
                enabled = frame.extraDimEnabled && frame.extraDimPercent > 0,
                percent = frame.extraDimPercent,
            )
            lastFrameApplied = nightDisplayApplied && brightnessApplied && extraDimApplied
            if (step < steps) {
                delay(SMOOTH_TRANSITION_MILLIS / steps)
            }
        }
        return lastFrameApplied
    }

    private fun readCurrentNativeEyeProtectionTarget(fallback: NativeEyeProtectionTarget): NativeEyeProtectionTarget {
        val currentState = _state.value
        if (currentState.nativeEyeProtectionApplied) {
            return NativeEyeProtectionTarget(
                colorTemperatureKelvin = currentState.nativeColorTemperatureKelvin
                    .takeIf { it > 0 }
                    ?: fallback.colorTemperatureKelvin,
                brightnessPercent = currentState.nativeBrightnessPercent
                    .takeIf { it > 0 }
                    ?: fallback.brightnessPercent,
                extraDimEnabled = currentState.nativeExtraDimEnabled,
                extraDimPercent = currentState.nativeExtraDimPercent,
            )
        }
        val nightDisplayActive = readIntSetting("secure", "night_display_activated", 0) == 1
        val currentColorTemperature = if (nightDisplayActive) {
            readIntSetting("secure", "night_display_color_temperature", MAX_COLOR_TEMPERATURE_KELVIN)
        } else {
            MAX_COLOR_TEMPERATURE_KELVIN
        }.coerceIn(MIN_COLOR_TEMPERATURE_KELVIN, MAX_COLOR_TEMPERATURE_KELVIN)
        val currentBrightness = systemBrightnessToPercent(
            readIntSetting("system", "screen_brightness", percentToSystemBrightness(fallback.brightnessPercent)),
        )
        val extraDimActive = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            readIntSetting("secure", "reduce_bright_colors_activated", 0) == 1
        val extraDimLevel = if (extraDimActive) {
            readIntSetting("secure", "reduce_bright_colors_level", fallback.extraDimPercent).coerceIn(0, 100)
        } else {
            0
        }
        return NativeEyeProtectionTarget(
            colorTemperatureKelvin = currentColorTemperature,
            brightnessPercent = currentBrightness,
            extraDimEnabled = extraDimActive,
            extraDimPercent = extraDimLevel,
        )
    }

    private fun clearNativeDisplayAdjustments(): Boolean {
        val nightDisplayApplied = listOf(
            executeShellCommand("cmd color_display set-night-display-activated false"),
            executeShellCommand("settings put secure night_display_activated 0"),
        ).any { it.success }
        val extraDimApplied = setExtraDim(enabled = false, percent = 0)
        return nightDisplayApplied && extraDimApplied
    }

    private fun setNightDisplay(colorTemperatureKelvin: Int): Boolean {
        val normalizedTemperature = colorTemperatureKelvin.coerceIn(
            MIN_COLOR_TEMPERATURE_KELVIN,
            MAX_COLOR_TEMPERATURE_KELVIN,
        )
        val temperatureApplied = listOf(
            executeShellCommand("cmd color_display set-night-display-color-temperature $normalizedTemperature"),
            executeShellCommand("settings put secure night_display_color_temperature $normalizedTemperature"),
        ).any { it.success }
        val activationApplied = listOf(
            executeShellCommand("cmd color_display set-night-display-activated true"),
            executeShellCommand("settings put secure night_display_activated 1"),
        ).any { it.success }
        return temperatureApplied && activationApplied
    }

    private fun setSystemBrightness(percent: Int): Boolean {
        val normalizedPercent = percent.coerceIn(1, 100)
        val brightness = percentToSystemBrightness(normalizedPercent)
        executeShellCommand("settings put system screen_brightness_mode 0")
        return executeShellCommand("settings put system screen_brightness $brightness").success
    }

    private fun setExtraDim(enabled: Boolean, percent: Int): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return !enabled
        }
        val normalizedPercent = if (enabled) percent.coerceIn(1, 100) else 0
        val levelApplied = executeShellCommand("settings put secure reduce_bright_colors_level $normalizedPercent").success
        val activationApplied = executeShellCommand(
            "settings put secure reduce_bright_colors_activated ${if (enabled) 1 else 0}",
        ).success
        if (enabled) {
            executeShellCommand("settings put secure reduce_bright_colors_persist_across_reboots 1")
        }
        return activationApplied && (!enabled || levelApplied)
    }

    private fun readIntSetting(namespace: String, key: String, fallback: Int): Int {
        val result = executeShellCommand("settings get $namespace $key")
        return result.output.trim().toIntOrNull() ?: fallback
    }

    private fun executeShellCommand(command: String): ShellCommandResult {
        val binder = shellServiceBinder()
            ?: return ShellCommandResult(
                exitCode = -1,
                output = "",
                error = "Shizuku shell user service is unavailable.",
            )
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return runCatching {
            data.writeInterfaceToken(ShizukuShellUserService.DESCRIPTOR)
            data.writeString(command)
            val transacted = binder.transact(ShizukuShellUserService.TRANSACTION_EXEC, data, reply, 0)
            if (!transacted) {
                error("Shizuku shell user service rejected the command.")
            }
            reply.readException()
            ShellCommandResult(
                exitCode = reply.readInt(),
                output = reply.readString().orEmpty(),
                error = reply.readString().orEmpty(),
            )
        }.getOrElse { throwable ->
            shellServiceBinder = null
            ShellCommandResult(
                exitCode = -1,
                output = "",
                error = throwable.message.orEmpty().ifBlank { throwable.javaClass.simpleName },
            )
        }.also {
            data.recycle()
            reply.recycle()
        }
    }

    private fun shellServiceBinder(): IBinder? {
        shellServiceBinder?.takeIf { it.isBinderAlive }?.let { return it }
        synchronized(shellServiceLock) {
            shellServiceBinder?.takeIf { it.isBinderAlive }?.let { return it }
            shellServiceBinder = null
            val bound = runCatching {
                Shizuku.bindUserService(
                    Shizuku.UserServiceArgs(
                        ComponentName(context.packageName, ShizukuShellUserService::class.java.name),
                    )
                        .daemon(false)
                        .processNameSuffix("shizuku-shell")
                        .tag(SHIZUKU_SHELL_SERVICE_TAG)
                        .version(SHIZUKU_SHELL_SERVICE_VERSION),
                    shellServiceConnection,
                )
            }.isSuccess
            if (!bound) return null

            val deadline = SystemClock.uptimeMillis() + SHELL_SERVICE_BIND_TIMEOUT_MILLIS
            while (shellServiceBinder == null && SystemClock.uptimeMillis() < deadline) {
                val remaining = deadline - SystemClock.uptimeMillis()
                if (remaining > 0) {
                    runCatching { shellServiceLock.wait(remaining) }
                }
            }
            return shellServiceBinder?.takeIf { it.isBinderAlive }
        }
    }

    private fun interpolate(start: Int, end: Int, fraction: Float): Int {
        return (start + (end - start) * fraction).roundToInt()
    }

    private fun percentToSystemBrightness(percent: Int): Int {
        return ((percent.coerceIn(1, 100) / 100f) * 255f).roundToInt().coerceIn(1, 255)
    }

    private fun systemBrightnessToPercent(brightness: Int): Int {
        return ((brightness.coerceIn(1, 255) / 255f) * 100f).roundToInt().coerceIn(1, 100)
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
        val powerManager = context.getSystemService(PowerManager::class.java)
        val deviceInteractive = powerManager?.isInteractive ?: true
        val batterySnapshot = latestBatterySnapshot()
        val powerSaveActive = powerManager?.isPowerSaveMode == true
        val dndActive = latestDndActive()
        val thermalStatus = latestThermalStatus(powerManager)
        val cameraPrivacyEnabled = latestCameraPrivacyEnabled()
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

    private fun latestBatterySnapshot(): BatterySnapshot {
        val intent = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val levelPercent = if (level >= 0 && scale > 0) {
            ((level * 100f) / scale).toInt().coerceIn(0, 100)
        } else {
            -1
        }
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            ?: BatteryManager.BATTERY_STATUS_UNKNOWN
        val powered = plugged != 0 ||
            status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        return BatterySnapshot(
            levelPercent = levelPercent,
            lowBatteryActive = levelPercent in 0..LOW_BATTERY_THRESHOLD_PERCENT && !powered,
        )
    }

    private fun latestDndActive(): Boolean {
        val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return false
        return runCatching {
            val filter = notificationManager.currentInterruptionFilter
            filter == NotificationManager.INTERRUPTION_FILTER_PRIORITY ||
                filter == NotificationManager.INTERRUPTION_FILTER_ALARMS ||
                filter == NotificationManager.INTERRUPTION_FILTER_NONE
        }.getOrDefault(false)
    }

    private fun latestThermalStatus(powerManager: PowerManager?): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            powerManager?.currentThermalStatus ?: 0
        } else {
            0
        }
    }

    private fun latestCameraPrivacyEnabled(): Boolean {
        return false
    }

    private fun latestInstalledUserApps(): List<ShizukuInstalledApp> {
        val result = executeShellCommand(USER_APP_LIST_COMMAND)
        if (!result.success) return emptyList()
        return result.output
            .lineSequence()
            .mapNotNull { parseInstalledAppLine(it) }
            .distinctBy { it.packageName }
            .sortedBy { it.packageName }
            .toList()
    }

    private fun parseInstalledAppLine(line: String): ShizukuInstalledApp? {
        var packageName = ""
        var installerPackageName = ""
        var versionCode: Long? = null
        var uid: Int? = null
        PACKAGE_LIST_TOKEN_SPLIT_REGEX.split(line.trim())
            .asSequence()
            .filter { it.isNotBlank() }
            .forEach { token ->
                when {
                    token.startsWith("package:") -> {
                        packageName = token
                            .removePrefix("package:")
                            .substringAfterLast("=")
                    }
                    token.startsWith("installer=") -> {
                        installerPackageName = token.substringAfter("=")
                    }
                    token.startsWith("installerPackageName=") -> {
                        installerPackageName = token.substringAfter("=")
                    }
                    token.startsWith("versionCode:") -> {
                        versionCode = token.substringAfter(":").toLongOrNull()
                    }
                    token.startsWith("versionCode=") -> {
                        versionCode = token.substringAfter("=").toLongOrNull()
                    }
                    token.startsWith("uid:") -> {
                        uid = token.substringAfter(":").toIntOrNull()
                    }
                    token.startsWith("uid=") -> {
                        uid = token.substringAfter("=").toIntOrNull()
                    }
                }
            }
        val normalizedPackageName = sanitizePackageToken(packageName)
        if (!ANDROID_PACKAGE_NAME_REGEX.matches(normalizedPackageName)) return null
        return ShizukuInstalledApp(
            packageName = normalizedPackageName,
            installerPackageName = sanitizePackageToken(installerPackageName).takeIf { it != "null" }.orEmpty(),
            versionCode = versionCode?.coerceAtLeast(0L),
            uid = uid?.coerceAtLeast(0),
        )
    }

    private fun sanitizePackageToken(value: String): String {
        return value.trim().take(MAX_PACKAGE_FIELD_LENGTH)
    }

    private data class BatterySnapshot(
        val levelPercent: Int,
        val lowBatteryActive: Boolean,
    )

    private data class NativeEyeProtectionTarget(
        val colorTemperatureKelvin: Int,
        val brightnessPercent: Int,
        val extraDimEnabled: Boolean,
        val extraDimPercent: Int,
    )

    private data class ShellCommandResult(
        val exitCode: Int,
        val output: String,
        val error: String,
    ) {
        val success: Boolean
            get() = exitCode == 0
    }

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
        private const val LOW_BATTERY_THRESHOLD_PERCENT = 15
        private const val THERMAL_STATUS_MODERATE = 2
        private const val MIN_COLOR_TEMPERATURE_KELVIN = 1800
        private const val MAX_COLOR_TEMPERATURE_KELVIN = 6500
        private const val SMOOTH_TRANSITION_MILLIS = 5_000L
        private const val SMOOTH_TRANSITION_STEPS = 10
        private const val SHELL_SERVICE_BIND_TIMEOUT_MILLIS = 5_000L
        private const val SHIZUKU_SHELL_SERVICE_TAG = "project_lumen_shell_v1"
        private const val SHIZUKU_SHELL_SERVICE_VERSION = 1
        private const val MAX_DIAGNOSTIC_USER_APPS = 150
        private const val MAX_PACKAGE_FIELD_LENGTH = 160
        private const val USER_APP_LIST_COMMAND =
            "cmd package list packages -3 -i -U --show-versioncode 2>/dev/null || " +
                "pm list packages -3 -i -U --show-versioncode 2>/dev/null || " +
                "cmd package list packages -3 -i -U 2>/dev/null || " +
                "pm list packages -3 -i -U"

        private val sensitiveCameraHints = listOf("camera", "camerax", "scanner", "barcode", "qr")
        private val sensitiveCallHints = listOf("call", "voip", "meeting", "conference", "telecom", "zoom", "meet")
        private val sensitiveMediaHints = listOf("player", "video", "fullscreen", "youtube", "netflix", "bilibili", "tiktok")
        private val sensitiveGameHints = listOf("game", "unity", "unreal", "tmgp", "mihoyo", "hoyoverse", "netease")
        private val PACKAGE_LIST_TOKEN_SPLIT_REGEX = Regex("\\s+")
        private val ANDROID_PACKAGE_NAME_REGEX = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")
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
