package com.projectlumen.app.core.shizuku

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.Parcel
import android.os.PowerManager
import com.projectlumen.app.core.database.entities.AppSettingsEntity
import com.projectlumen.app.core.mmkv.ProjectLumenMmkv
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import kotlin.math.roundToInt

class ShizukuCapabilityManager(
    private val context: Context,
) {
    private val _state = MutableStateFlow(ShizukuCapabilityState())
    val state = _state.asStateFlow()
    private val commandMutex = Mutex()
    private val shellScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var shellServiceBinder: IBinder? = null

    @Volatile
    private var pendingShellServiceBinder: CompletableDeferred<IBinder>? = null

    private val nativeAdjustmentStore: MMKV? by lazy {
        runCatching { ProjectLumenMmkv.mmkvWithId(NATIVE_ADJUSTMENT_STORE_ID) }.getOrNull()
    }

    private val shellServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            shellServiceBinder = service
            pendingShellServiceBinder?.complete(service)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            shellServiceBinder = null
        }
    }
    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
        if (requestCode == PERMISSION_REQUEST_CODE) {
            refreshState()
        }
    }

    init {
        runCatching { Shizuku.addRequestPermissionResultListener(permissionResultListener) }
        runCatching { Shizuku.addBinderReceivedListenerSticky { refreshState() } }
        runCatching {
            Shizuku.addBinderDeadListener {
                shellServiceBinder = null
                refreshState()
            }
        }
        restorePersistedNativeAdjustments()
        refreshState()
    }

    fun refreshState() {
        _state.update { queryState(it) }
    }

    fun requestPermission() {
        val current = _state.updateAndGet { queryState(it) }
        if (!current.binderAvailable || current.permissionGranted) return
        if (!current.permissionRequestable) return
        runCatching { Shizuku.requestPermission(PERMISSION_REQUEST_CODE) }
            .onFailure { throwable -> _state.update { queryState(it, throwable.message.orEmpty()) } }
    }

    suspend fun refreshForegroundContext(): ShizukuForegroundContext? = withContext(Dispatchers.IO) {
        val baseState = _state.updateAndGet { queryState(it) }
        if (!baseState.ready) {
            return@withContext null
        }
        val foregroundContext = latestForegroundContext()
        _state.update {
            it.copy(
                foregroundPackage = foregroundContext?.packageName.orEmpty(),
                foregroundActivity = foregroundContext?.activityName.orEmpty(),
                foregroundCategory = foregroundContext?.category.orEmpty(),
                foregroundShouldDeferSampling = foregroundContext?.shouldDeferSampling == true,
                lastCheckedAt = System.currentTimeMillis(),
                lastError = if (foregroundContext == null) "Foreground context unavailable." else "",
            )
        }
        foregroundContext
    }

    suspend fun refreshSystemContext(settings: AppSettingsEntity): ShizukuSystemContext? = withContext(Dispatchers.IO) {
        val baseState = _state.updateAndGet { queryState(it) }
        if (!baseState.ready) {
            return@withContext null
        }
        val systemContext = latestSystemContext(settings)
        _state.update {
            it.copy(
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
        }
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

    suspend fun collectDeviceDiagnostics(includeUserApps: Boolean): ShizukuDeviceDiagnostics =
        withContext(Dispatchers.IO) {
            commandMutex.withLock { collectDeviceDiagnosticsLocked(includeUserApps) }
        }

    private suspend fun collectDeviceDiagnosticsLocked(includeUserApps: Boolean): ShizukuDeviceDiagnostics {
        val currentState = _state.updateAndGet { queryState(it) }
        if (!currentState.ready) {
            return ShizukuDeviceDiagnostics(
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
        return ShizukuDeviceDiagnostics(
            collectedAt = System.currentTimeMillis(),
            shizukuReady = true,
            shizukuServerVersion = currentState.serverVersion,
            shizukuServerUid = currentState.serverUid,
            userAppCount = installedApps.size,
            userAppsTruncated = installedApps.size > MAX_DIAGNOSTIC_USER_APPS,
            userApps = installedApps.take(MAX_DIAGNOSTIC_USER_APPS),
        )
    }

    suspend fun listNetworkControllableApps(): List<ShizukuNetworkApp> = withContext(Dispatchers.IO) {
        commandMutex.withLock { listNetworkControllableAppsLocked() }
    }

    private suspend fun listNetworkControllableAppsLocked(): List<ShizukuNetworkApp> {
        val currentState = _state.updateAndGet { queryState(it) }
        if (!currentState.ready) return emptyList()
        val restrictedUids = latestRestrictBackgroundDenylist()
        // Primary source: in-process PackageManager. This is reliable across OEM ROMs and does
        // not depend on the shell `pm list packages -U` output format, which varies by device and
        // is the reason the list previously came back empty on some builds.
        val packageManagerApps = latestInstalledAppsFromPackageManager()
        // Supplement with elevated shell enumeration so packages hidden from our direct query
        // (e.g. other users/profiles or apps not visible even with QUERY_ALL_PACKAGES) still show.
        val shellSystemApps = latestInstalledApps(SYSTEM_APP_LIST_COMMAND, ShizukuNetworkAppTypes.SYSTEM)
        val shellUserApps = latestInstalledApps(USER_APP_LIST_COMMAND, ShizukuNetworkAppTypes.USER)
        return (packageManagerApps + shellUserApps + shellSystemApps)
            .filter { it.uid > 0 && it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .map { app -> app.copy(restrictedByUidPolicy = restrictedUids.contains(app.uid)) }
            .sortedWith(compareBy<ShizukuNetworkApp> { it.appType != ShizukuNetworkAppTypes.USER }.thenBy { it.packageName })
    }

    private fun latestInstalledAppsFromPackageManager(): List<ShizukuNetworkApp> {
        val packageManager = context.packageManager
        val installedApps = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getInstalledApplications(
                    PackageManager.ApplicationInfoFlags.of(0L),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstalledApplications(0)
            }
        }.getOrElse { return emptyList() }
        return installedApps
            .asSequence()
            .filter { it.uid > 0 }
            .map { info ->
                val isSystemApp = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                    (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                ShizukuNetworkApp(
                    packageName = sanitizePackageToken(info.packageName.orEmpty()),
                    uid = info.uid,
                    appType = if (isSystemApp) ShizukuNetworkAppTypes.SYSTEM else ShizukuNetworkAppTypes.USER,
                )
            }
            .filter { ANDROID_PACKAGE_NAME_REGEX.matches(it.packageName) }
            .distinctBy { it.packageName }
            .toList()
    }

    suspend fun restrictAppNetwork(app: ShizukuNetworkApp): ShizukuNetworkPolicyResult = withContext(Dispatchers.IO) {
        commandMutex.withLock {
            applyAppNetworkPolicy(
                packageName = app.packageName,
                uid = app.uid,
                appType = app.appType,
                restrict = true,
                previousNetworkRestricted = false,
                previousDelegatedGuardApplied = false,
            )
        }
    }

    suspend fun restoreAppNetwork(
        packageName: String,
        uid: Int,
        appType: String,
        previousNetworkRestricted: Boolean,
        previousDelegatedGuardApplied: Boolean,
    ): ShizukuNetworkPolicyResult = withContext(Dispatchers.IO) {
        commandMutex.withLock {
            applyAppNetworkPolicy(
                packageName = packageName,
                uid = uid,
                appType = appType,
                restrict = false,
                previousNetworkRestricted = previousNetworkRestricted,
                previousDelegatedGuardApplied = previousDelegatedGuardApplied,
            )
        }
    }

    suspend fun applyNativeEyeProtection(settings: AppSettingsEntity, smooth: Boolean = true): Boolean =
        withContext(Dispatchers.IO) {
            commandMutex.withLock { applyNativeEyeProtectionLocked(settings, smooth) }
        }

    private suspend fun applyNativeEyeProtectionLocked(settings: AppSettingsEntity, smooth: Boolean): Boolean {
        val currentState = _state.updateAndGet { queryState(it) }
        val shouldEnable = settings.shizukuAdvancedModeEnabled && settings.shizukuNativeEyeProtectionEnabled
        if (!shouldEnable) {
            if (!currentState.nativeEyeProtectionApplied) {
                _state.update { it.copy(nativeEyeProtectionApplied = false, lastError = "") }
                return true
            }
            if (!currentState.ready) {
                _state.update {
                    it.copy(
                        lastError = "Shizuku authorization is required to disable native eye protection.",
                    )
                }
                return false
            }
            val cleared = clearNativeDisplayAdjustments()
            if (!cleared) {
                // The adjustments are still on the device, so the state must keep saying so
                // instead of reporting a clean shutdown the user can see is false.
                _state.update {
                    queryState(it, "Unable to disable every native eye protection setting.")
                }
                return false
            }
            persistNativeAdjustments(
                _state.updateAndGet {
                    queryState(it).copy(
                        nativeEyeProtectionApplied = false,
                        nativeColorTemperatureKelvin = 0,
                        nativeBrightnessPercent = 0,
                        nativeExtraDimEnabled = false,
                        nativeExtraDimPercent = 0,
                    )
                },
            )
            return true
        }

        if (!currentState.ready) {
            _state.update {
                it.copy(lastError = "Shizuku authorization is required for native eye protection.")
            }
            return false
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
        persistNativeAdjustments(
            _state.updateAndGet { previous ->
                queryState(
                    previous,
                    if (applied) "" else "Some native display settings were not accepted by this device.",
                ).copy(
                    // Even a partially applied target leaves changes on the device, so the
                    // disable path must still be told there is something to undo.
                    nativeEyeProtectionApplied = true,
                    nativeColorTemperatureKelvin = if (applied) {
                        target.colorTemperatureKelvin
                    } else {
                        previous.nativeColorTemperatureKelvin
                    },
                    nativeBrightnessPercent = if (applied) {
                        target.brightnessPercent
                    } else {
                        previous.nativeBrightnessPercent
                    },
                    nativeExtraDimEnabled = if (applied) {
                        target.extraDimEnabled
                    } else {
                        previous.nativeExtraDimEnabled
                    },
                    nativeExtraDimPercent = when {
                        !applied -> previous.nativeExtraDimPercent
                        target.extraDimEnabled -> target.extraDimPercent
                        else -> 0
                    },
                )
            },
        )
        return applied
    }

    suspend fun applySystemBrightness(
        percent: Int,
        extraDimPercent: Int? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        commandMutex.withLock { applySystemBrightnessLocked(percent, extraDimPercent) }
    }

    private suspend fun applySystemBrightnessLocked(percent: Int, extraDimPercent: Int?): Boolean {
        val currentState = _state.updateAndGet { queryState(it) }
        if (!currentState.ready) {
            _state.update {
                it.copy(lastError = "Shizuku authorization is required to adjust system brightness.")
            }
            return false
        }
        val normalizedPercent = percent.coerceIn(1, 100)
        val normalizedExtraDimPercent = extraDimPercent?.coerceIn(0, 100)
        val brightness = percentToSystemBrightness(normalizedPercent)
        rememberOriginalDisplaySettings()
        val modeResult = executeShellCommand("settings put system screen_brightness_mode 0")
        val brightnessResult = executeShellCommand("settings put system screen_brightness $brightness")
        val extraDimApplied = normalizedExtraDimPercent?.let {
            setExtraDim(enabled = it > 0, percent = it)
        } ?: true
        val applied = brightnessResult.success && extraDimApplied
        persistNativeAdjustments(
            _state.updateAndGet { previous ->
                queryState(
                    previous,
                    when {
                        applied -> ""
                        !brightnessResult.success -> brightnessResult.failureReason(
                            modeResult.failureReason("System brightness command failed."),
                        )
                        normalizedExtraDimPercent != null && !extraDimApplied -> "Extra Dim command failed."
                        else -> "System brightness command failed."
                    },
                ).copy(
                    nativeBrightnessPercent = if (brightnessResult.success) {
                        normalizedPercent
                    } else {
                        previous.nativeBrightnessPercent
                    },
                    nativeExtraDimEnabled = when (normalizedExtraDimPercent) {
                        null -> previous.nativeExtraDimEnabled
                        else -> extraDimApplied && normalizedExtraDimPercent > 0
                    },
                    nativeExtraDimPercent = when (normalizedExtraDimPercent) {
                        null -> previous.nativeExtraDimPercent
                        else -> if (extraDimApplied) normalizedExtraDimPercent else previous.nativeExtraDimPercent
                    },
                )
            },
        )
        return applied
    }

    fun isReady(): Boolean = _state.updateAndGet { queryState(it) }.ready

    private fun queryState(
        previous: ShizukuCapabilityState,
        error: String = "",
    ): ShizukuCapabilityState {
        val binderAvailable = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!binderAvailable) {
            // The native* fields record what this app changed on the device, which stays true
            // while Shizuku is offline; only connectivity degrades.
            return previous.copy(
                binderAvailable = false,
                permissionGranted = false,
                permissionRequestable = false,
                serverVersion = 0,
                serverUid = 0,
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
        return previous.copy(
            binderAvailable = true,
            permissionGranted = permissionGranted,
            permissionRequestable = permissionRequestable,
            serverVersion = runCatching { Shizuku.getVersion() }.getOrDefault(0),
            serverUid = runCatching { Shizuku.getUid() }.getOrDefault(0),
            lastCheckedAt = System.currentTimeMillis(),
            lastError = error,
        )
    }

    private fun restorePersistedNativeAdjustments() {
        val store = nativeAdjustmentStore ?: return
        if (!runCatching { store.decodeBool(KEY_NATIVE_APPLIED, false) }.getOrDefault(false)) return
        _state.update {
            it.copy(
                nativeEyeProtectionApplied = true,
                nativeColorTemperatureKelvin = store.decodeInt(KEY_NATIVE_COLOR_TEMPERATURE, 0),
                nativeBrightnessPercent = store.decodeInt(KEY_NATIVE_BRIGHTNESS_PERCENT, 0),
                nativeExtraDimEnabled = store.decodeBool(KEY_NATIVE_EXTRA_DIM_ENABLED, false),
                nativeExtraDimPercent = store.decodeInt(KEY_NATIVE_EXTRA_DIM_PERCENT, 0),
            )
        }
    }

    private fun persistNativeAdjustments(state: ShizukuCapabilityState) {
        val store = nativeAdjustmentStore ?: return
        runCatching {
            store.encode(KEY_NATIVE_APPLIED, state.nativeEyeProtectionApplied)
            store.encode(KEY_NATIVE_COLOR_TEMPERATURE, state.nativeColorTemperatureKelvin)
            store.encode(KEY_NATIVE_BRIGHTNESS_PERCENT, state.nativeBrightnessPercent)
            store.encode(KEY_NATIVE_EXTRA_DIM_ENABLED, state.nativeExtraDimEnabled)
            store.encode(KEY_NATIVE_EXTRA_DIM_PERCENT, state.nativeExtraDimPercent)
        }
    }

    private suspend fun applyNativeEyeProtectionTarget(target: NativeEyeProtectionTarget, smooth: Boolean): Boolean {
        val start = if (smooth) readCurrentNativeEyeProtectionTarget(target) else target
        val steps = if (smooth) SMOOTH_TRANSITION_STEPS else 1
        var lastFrameApplied = false
        var lastAppliedFrame: NativeEyeProtectionTarget? = null
        rememberOriginalDisplaySettings()
        executeShellCommand("settings put system screen_brightness_mode 0")
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
            if (frame != lastAppliedFrame) {
                val nightDisplayApplied = setNightDisplay(frame.colorTemperatureKelvin)
                val brightnessApplied = setSystemBrightness(frame.brightnessPercent)
                val extraDimApplied = setExtraDim(
                    enabled = frame.extraDimEnabled && frame.extraDimPercent > 0,
                    percent = frame.extraDimPercent,
                )
                lastFrameApplied = nightDisplayApplied && brightnessApplied && extraDimApplied
                lastAppliedFrame = frame
            }
            if (step < steps) {
                delay(SMOOTH_TRANSITION_MILLIS / steps)
            }
        }
        return lastFrameApplied
    }

    private suspend fun readCurrentNativeEyeProtectionTarget(fallback: NativeEyeProtectionTarget): NativeEyeProtectionTarget {
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

    private suspend fun clearNativeDisplayAdjustments(): Boolean {
        val nightDisplayApplied = listOf(
            executeShellCommand("cmd color_display set-night-display-activated false"),
            executeShellCommand("settings put secure night_display_activated 0"),
        ).any { it.success }
        val extraDimApplied = setExtraDim(enabled = false, percent = 0)
        val brightnessRestored = restoreOriginalDisplaySettings()
        return nightDisplayApplied && extraDimApplied && brightnessRestored
    }

    /**
     * Captures the display settings this app is about to overwrite so they can be handed back;
     * without them the device would stay locked to our brightness after the feature is turned off.
     */
    private suspend fun rememberOriginalDisplaySettings() {
        val store = nativeAdjustmentStore ?: return
        if (runCatching { store.decodeInt(KEY_ORIGINAL_BRIGHTNESS, -1) }.getOrDefault(-1) >= 0) return
        val brightness = readIntSetting("system", "screen_brightness", -1)
        val mode = readIntSetting("system", "screen_brightness_mode", -1)
        if (brightness < 0) return
        runCatching {
            store.encode(KEY_ORIGINAL_BRIGHTNESS, brightness)
            store.encode(KEY_ORIGINAL_BRIGHTNESS_MODE, mode)
        }
    }

    private suspend fun restoreOriginalDisplaySettings(): Boolean {
        val store = nativeAdjustmentStore
        val brightness = runCatching { store?.decodeInt(KEY_ORIGINAL_BRIGHTNESS, -1) }.getOrNull() ?: -1
        val mode = runCatching { store?.decodeInt(KEY_ORIGINAL_BRIGHTNESS_MODE, -1) }.getOrNull() ?: -1
        val brightnessRestored = if (brightness > 0) {
            executeShellCommand("settings put system screen_brightness $brightness").success
        } else {
            true
        }
        val modeRestored = executeShellCommand(
            "settings put system screen_brightness_mode ${if (mode >= 0) mode else 1}",
        ).success
        if (brightnessRestored && modeRestored) {
            runCatching {
                store?.encode(KEY_ORIGINAL_BRIGHTNESS, -1)
                store?.encode(KEY_ORIGINAL_BRIGHTNESS_MODE, -1)
            }
        }
        return brightnessRestored && modeRestored
    }

    private suspend fun setNightDisplay(colorTemperatureKelvin: Int): Boolean {
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

    private suspend fun setSystemBrightness(percent: Int): Boolean {
        val normalizedPercent = percent.coerceIn(1, 100)
        val brightness = percentToSystemBrightness(normalizedPercent)
        return executeShellCommand("settings put system screen_brightness $brightness").success
    }

    private suspend fun setExtraDim(enabled: Boolean, percent: Int): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return !enabled
        }
        val normalizedPercent = if (enabled) percent.coerceIn(1, 100) else 0
        val levelApplied = executeShellCommand("settings put secure reduce_bright_colors_level $normalizedPercent").success
        val activationApplied = executeShellCommand(
            "settings put secure reduce_bright_colors_activated ${if (enabled) 1 else 0}",
        ).success
        executeShellCommand(
            "settings put secure reduce_bright_colors_persist_across_reboots ${if (enabled) 1 else 0}",
        )
        return activationApplied && (!enabled || levelApplied)
    }

    private suspend fun readIntSetting(namespace: String, key: String, fallback: Int): Int {
        val result = executeShellCommand("settings get $namespace $key")
        return result.output.trim().toIntOrNull() ?: fallback
    }

    private suspend fun executeShellCommand(command: String): ShellCommandResult {
        val binder = shellServiceBinder()
            ?: return ShellCommandResult(
                exitCode = -1,
                output = "",
                error = "Shizuku shell user service is unavailable.",
            )
        // binder.transact() blocks with no local deadline, so it runs on a coroutine the timeout
        // can abandon instead of pinning the caller until the remote side gives up.
        val call = shellScope.async { transactShellCommand(binder, command) }
        val result = withTimeoutOrNull(SHELL_COMMAND_TIMEOUT_MILLIS) { call.await() }
        if (result != null) return result
        call.cancel()
        return ShellCommandResult(
            exitCode = SHELL_TIMEOUT_EXIT_CODE,
            output = "",
            error = "Shizuku shell command timed out.",
        )
    }

    private fun transactShellCommand(binder: IBinder, command: String): ShellCommandResult {
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

    /** Callers reach this only while holding [commandMutex], so one bind attempt runs at a time. */
    private suspend fun shellServiceBinder(): IBinder? {
        shellServiceBinder?.takeIf { it.isBinderAlive }?.let { return it }
        val pending = CompletableDeferred<IBinder>()
        pendingShellServiceBinder = pending
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
        if (!bound) {
            pendingShellServiceBinder = null
            return null
        }
        val connected = withTimeoutOrNull(SHELL_SERVICE_BIND_TIMEOUT_MILLIS) { pending.await() }
        pendingShellServiceBinder = null
        return (connected ?: shellServiceBinder)?.takeIf { it.isBinderAlive }
    }

    private fun ShellCommandResult.failureReason(fallback: String): String {
        val combined = "$error\n$output".lowercase()
        return when {
            exitCode == SHELL_TIMEOUT_EXIT_CODE -> "The Shizuku command did not finish in time."
            combined.contains("permission denied") || combined.contains("securityexception") ->
                "Shizuku authorization is no longer valid; grant it again."
            combined.contains("unknown command") ||
                combined.contains("bad argument") ||
                combined.contains("unknown appop") ->
                "This device does not support the required system command."
            error.isNotBlank() -> error.take(MAX_COMMAND_FIELD_LENGTH)
            else -> fallback
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
            // Android 13+ requires an explicit export flag even for sticky broadcast queries.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(
                    null,
                    IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                    Context.RECEIVER_NOT_EXPORTED,
                )
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            }
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

    private suspend fun latestInstalledUserApps(): List<ShizukuInstalledApp> {
        val result = executeShellCommand(USER_APP_LIST_COMMAND)
        if (!result.success) return emptyList()
        return result.output
            .lineSequence()
            .mapNotNull { parseInstalledAppLine(it) }
            .distinctBy { it.packageName }
            .sortedBy { it.packageName }
            .toList()
    }

    private suspend fun latestInstalledApps(command: String, appType: String): List<ShizukuNetworkApp> {
        val result = executeShellCommand(command)
        if (!result.success) return emptyList()
        return result.output
            .lineSequence()
            .mapNotNull { parseNetworkAppLine(it, appType) }
            .distinctBy { it.packageName }
            .toList()
    }

    private suspend fun latestRestrictBackgroundDenylist(): Set<Int> {
        val result = executeShellCommand("cmd netpolicy list restrict-background-blacklist")
        if (!result.success) return emptySet()
        return parseRestrictBackgroundUids(result.output)
    }

    /**
     * `cmd netpolicy` prints one UID per line on AOSP but OEM builds add prose; matching every
     * 3-digit run anywhere in the output turned version numbers into restricted UIDs.
     */
    private fun parseRestrictBackgroundUids(output: String): Set<Int> {
        return output.lineSequence()
            .mapNotNull { line ->
                val trimmed = line.trim()
                when {
                    trimmed.isEmpty() -> null
                    trimmed.all { it in '0'..'9' } -> trimmed.toIntOrNull()
                    else -> UID_FIELD_REGEX.find(trimmed)?.groupValues?.getOrNull(1)?.toIntOrNull()
                }
            }
            .filter { it > 0 }
            .toSet()
    }

    private suspend fun applyAppNetworkPolicy(
        packageName: String,
        uid: Int,
        appType: String,
        restrict: Boolean,
        previousNetworkRestricted: Boolean,
        previousDelegatedGuardApplied: Boolean,
    ): ShizukuNetworkPolicyResult {
        val currentState = _state.updateAndGet { queryState(it) }
        val normalizedPackageName = sanitizePackageToken(packageName)
        if (!currentState.ready) {
            return networkPolicyResult(
                packageName = normalizedPackageName,
                uid = uid,
                appType = appType,
                networkRestricted = previousNetworkRestricted,
                uidPolicyApplied = previousNetworkRestricted,
                delegatedGuardAttempted = false,
                delegatedGuardApplied = previousDelegatedGuardApplied,
                output = "",
                error = "Shizuku authorization is required for app network controls.",
            )
        }
        if (!ANDROID_PACKAGE_NAME_REGEX.matches(normalizedPackageName) || uid <= 0) {
            return networkPolicyResult(
                packageName = normalizedPackageName,
                uid = uid,
                appType = appType,
                networkRestricted = previousNetworkRestricted,
                uidPolicyApplied = previousNetworkRestricted,
                delegatedGuardAttempted = false,
                delegatedGuardApplied = previousDelegatedGuardApplied,
                output = "",
                error = "A valid package name and UID are required.",
            )
        }
        val policyCommand = if (restrict) {
            "cmd netpolicy add restrict-background-blacklist $uid"
        } else {
            "cmd netpolicy remove restrict-background-blacklist $uid"
        }
        val uidPolicyResult = executeShellCommand(policyCommand)
        val shouldAttemptDelegatedGuard = restrict || previousDelegatedGuardApplied
        val delegatedGuardResult = if (shouldAttemptDelegatedGuard) {
            setDelegatedNetworkGuard(normalizedPackageName, restrict)
        } else {
            DelegatedNetworkGuardResult(attempted = false, applied = false, output = "", error = "")
        }
        val uidPolicySucceeded = uidPolicyResult.success || (!restrict && uidPolicyResult.isAlreadyNotRestricted())
        val output = listOf(uidPolicyResult.output, delegatedGuardResult.output)
            .filter { it.isNotBlank() }
            .joinToString("\n")
        val errors = shizukuNetworkPolicyErrors(
            uidPolicySucceeded = uidPolicySucceeded,
            uidPolicyError = uidPolicyResult.error,
            delegatedGuardAttempted = delegatedGuardResult.attempted,
            delegatedGuardApplied = delegatedGuardResult.applied,
            delegatedGuardError = delegatedGuardResult.error,
        )
        val restrictionState = resolveShizukuNetworkRestrictionState(
            restrict = restrict,
            previousNetworkRestricted = previousNetworkRestricted,
            previousDelegatedGuardApplied = previousDelegatedGuardApplied,
            uidPolicyCommandSucceeded = uidPolicySucceeded,
            delegatedGuardCommandSucceeded = delegatedGuardResult.applied,
        )
        _state.update { queryState(it, errors) }
        return networkPolicyResult(
            packageName = normalizedPackageName,
            uid = uid,
            appType = appType,
            networkRestricted = restrictionState.networkRestricted,
            uidPolicyApplied = restrictionState.networkRestricted,
            delegatedGuardAttempted = delegatedGuardResult.attempted,
            delegatedGuardApplied = restrictionState.delegatedGuardApplied,
            output = output,
            error = errors,
        )
    }

    private suspend fun setDelegatedNetworkGuard(packageName: String, restrict: Boolean): DelegatedNetworkGuardResult {
        val mode = if (restrict) "ignore" else "allow"
        val commands = listOf(
            "cmd appops set $packageName android:internet $mode",
            "cmd appops set $packageName INTERNET $mode",
        )
        val results = commands.map { executeShellCommand(it) }
        val applied = results.any { it.success }
        return DelegatedNetworkGuardResult(
            attempted = true,
            applied = applied,
            output = results.joinToString("\n") { it.output }.trim(),
            error = if (applied) "" else results.joinToString("\n") { it.error.ifBlank { it.output } }.trim(),
        )
    }

    private fun ShellCommandResult.isAlreadyNotRestricted(): Boolean {
        val combined = "${output}\n${error}".lowercase()
        return combined.contains("not blacklisted") ||
            combined.contains("not denylisted") ||
            combined.contains("not in blacklist") ||
            combined.contains("not in denylist")
    }

    private fun networkPolicyResult(
        packageName: String,
        uid: Int,
        appType: String,
        networkRestricted: Boolean,
        uidPolicyApplied: Boolean,
        delegatedGuardAttempted: Boolean,
        delegatedGuardApplied: Boolean,
        output: String,
        error: String,
    ): ShizukuNetworkPolicyResult {
        return ShizukuNetworkPolicyResult(
            packageName = packageName,
            uid = uid,
            appType = appType,
            networkRestricted = networkRestricted,
            uidPolicyApplied = uidPolicyApplied,
            delegatedGuardAttempted = delegatedGuardAttempted,
            delegatedGuardApplied = delegatedGuardApplied,
            output = output.take(MAX_COMMAND_FIELD_LENGTH),
            error = error.take(MAX_COMMAND_FIELD_LENGTH),
        )
    }

    private fun parseNetworkAppLine(line: String, appType: String): ShizukuNetworkApp? {
        val installedApp = parseInstalledAppLine(line) ?: return null
        val uid = installedApp.uid ?: return null
        return ShizukuNetworkApp(
            packageName = installedApp.packageName,
            uid = uid,
            appType = appType,
        )
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
        val trimmed = value.trim()
        // Truncating instead of rejecting could turn an over-long name into a different, valid
        // package name and point the command at the wrong app.
        return if (trimmed.length > MAX_PACKAGE_FIELD_LENGTH) "" else trimmed
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
        private const val SHELL_COMMAND_TIMEOUT_MILLIS = 12_000L
        private const val SHELL_TIMEOUT_EXIT_CODE = 124
        private const val SHIZUKU_SHELL_SERVICE_TAG = "project_lumen_shell_v1"
        private const val SHIZUKU_SHELL_SERVICE_VERSION = 1
        private const val NATIVE_ADJUSTMENT_STORE_ID = "project_lumen_shizuku_display"
        private const val KEY_NATIVE_APPLIED = "native_eye_protection_applied"
        private const val KEY_NATIVE_COLOR_TEMPERATURE = "native_color_temperature_kelvin"
        private const val KEY_NATIVE_BRIGHTNESS_PERCENT = "native_brightness_percent"
        private const val KEY_NATIVE_EXTRA_DIM_ENABLED = "native_extra_dim_enabled"
        private const val KEY_NATIVE_EXTRA_DIM_PERCENT = "native_extra_dim_percent"
        private const val KEY_ORIGINAL_BRIGHTNESS = "original_screen_brightness"
        private const val KEY_ORIGINAL_BRIGHTNESS_MODE = "original_screen_brightness_mode"
        private const val MAX_DIAGNOSTIC_USER_APPS = 150
        private const val MAX_PACKAGE_FIELD_LENGTH = 160
        private const val MAX_COMMAND_FIELD_LENGTH = 1_000
        private const val USER_APP_LIST_COMMAND =
            "cmd package list packages -3 -i -U --show-versioncode 2>/dev/null || " +
                "pm list packages -3 -i -U --show-versioncode 2>/dev/null || " +
                "cmd package list packages -3 -i -U 2>/dev/null || " +
                "pm list packages -3 -i -U"
        private const val SYSTEM_APP_LIST_COMMAND =
            "cmd package list packages -s -i -U --show-versioncode 2>/dev/null || " +
                "pm list packages -s -i -U --show-versioncode 2>/dev/null || " +
                "cmd package list packages -s -i -U 2>/dev/null || " +
                "pm list packages -s -i -U"

        private val sensitiveCameraHints = listOf("camera", "camerax", "scanner", "barcode", "qr")
        private val sensitiveCallHints = listOf("call", "voip", "meeting", "conference", "telecom", "zoom", "meet")
        private val sensitiveMediaHints = listOf("player", "video", "fullscreen", "youtube", "netflix", "bilibili", "tiktok")
        private val sensitiveGameHints = listOf("game", "unity", "unreal", "tmgp", "mihoyo", "hoyoverse", "netease")
        private val PACKAGE_LIST_TOKEN_SPLIT_REGEX = Regex("\\s+")
        private val ANDROID_PACKAGE_NAME_REGEX = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")
        private val UID_FIELD_REGEX = Regex("(?:^|\\s)uid[=:](\\d+)", RegexOption.IGNORE_CASE)
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
