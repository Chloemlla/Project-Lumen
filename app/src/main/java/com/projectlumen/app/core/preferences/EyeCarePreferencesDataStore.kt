package com.projectlumen.app.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.projectlumen.app.core.database.entities.AppSettingsEntity
import com.projectlumen.app.core.mmkv.ProjectLumenMmkv
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.eyeCarePreferencesLegacyDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "eye_care_preferences",
)

private const val STORE_ID = "eye_care_preferences"
private const val KEY_MMKV_MIGRATION_COMPLETE = "__mmkv_migration_complete"

data class EyeCarePreferences(
    val hasPersistedValues: Boolean = false,
    val reminderEnabled: Boolean = true,
    val warnIntervalMinutes: Int = 20,
    val restDurationSeconds: Int = 20,
    val preAlertEnabled: Boolean = true,
    val preAlertSeconds: Int = 60,
    val useDynamicColors: Boolean = true,
    val pomodoroEnabled: Boolean = true,
    val pomodoroWorkMinutes: Int = 25,
    val pomodoroShortBreakMinutes: Int = 5,
    val pomodoroLongBreakMinutes: Int = 15,
    val notificationEnabled: Boolean = true,
    val keepAliveEnabled: Boolean = true,
    val proximityMonitoringEnabled: Boolean = false,
    val proximityBaselineEyeDistancePx: Float = 0f,
    val proximityBaselineFaceWidthPercent: Int = 0,
    val proximityDistanceMultiplierPercent: Int = 130,
    val proximityCheckIntervalMinutes: Int = 3,
    val proximityCaptureSeconds: Int = 2,
    val proximityFaceThresholdPercent: Int = 38,
    val proximityAlertCooldownSeconds: Int = 120,
    val blinkMonitoringEnabled: Boolean = false,
    val blinkNoBlinkThresholdSeconds: Int = 10,
    val blinkAlertCooldownSeconds: Int = 60,
    val ambientLightMonitoringEnabled: Boolean = false,
    val ambientLightLowLuxThreshold: Int = 10,
    val autoBrightnessEnabled: Boolean = false,
    val autoBrightnessMinPercent: Int = 35,
    val autoBrightnessMaxPercent: Int = 85,
    val globalOverlayEnabled: Boolean = true,
    val overlayRestDurationSeconds: Int = 20,
    val overlayStrictDistancePercent: Int = 160,
    val shizukuAdvancedModeEnabled: Boolean = false,
    val shizukuContextAwareSamplingEnabled: Boolean = true,
    val shizukuServiceRecoveryEnabled: Boolean = true,
    val shizukuScreenOffGuardEnabled: Boolean = true,
    val shizukuLowBatteryGuardEnabled: Boolean = true,
    val shizukuPowerSaveGuardEnabled: Boolean = true,
    val shizukuDndGuardEnabled: Boolean = false,
    val shizukuThermalGuardEnabled: Boolean = true,
    val shizukuCameraPrivacyGuardEnabled: Boolean = true,
    val shizukuNativeEyeProtectionEnabled: Boolean = false,
    val shizukuNativeColorTemperatureKelvin: Int = 4200,
    val shizukuNativeBrightnessPercent: Int = 35,
    val shizukuNativeExtraDimEnabled: Boolean = false,
    val shizukuNativeExtraDimPercent: Int = 25,
)

class EyeCarePreferencesDataStore(context: Context) {
    private val appContext = context.applicationContext
    private val legacyDataStore = appContext.eyeCarePreferencesLegacyDataStore
    private val mmkv by lazy { ProjectLumenMmkv.mmkvWithId(STORE_ID) }
    private val migrationLock = Mutex()
    private val state by lazy { MutableStateFlow(readFromMmkv()) }

    fun observe(): Flow<EyeCarePreferences> {
        return flow {
            ensureLegacyMigrated()
            emitAll(state)
        }
    }

    suspend fun read(): EyeCarePreferences {
        ensureLegacyMigrated()
        return state.value
    }

    suspend fun saveFromSettings(settings: AppSettingsEntity) {
        ensureLegacyMigrated()
        writeToMmkv(settings.toEyeCarePreferences())
    }

    private fun toEyeCarePreferences(preferences: Preferences): EyeCarePreferences {
        return EyeCarePreferences(
            hasPersistedValues = preferences.asMap().isNotEmpty(),
            reminderEnabled = preferences[Keys.REMINDER_ENABLED] ?: true,
            warnIntervalMinutes = preferences[Keys.WARN_INTERVAL_MINUTES] ?: 20,
            restDurationSeconds = preferences[Keys.REST_DURATION_SECONDS] ?: 20,
            preAlertEnabled = preferences[Keys.PRE_ALERT_ENABLED] ?: true,
            preAlertSeconds = preferences[Keys.PRE_ALERT_SECONDS] ?: 60,
            useDynamicColors = preferences[Keys.USE_DYNAMIC_COLORS] ?: true,
            pomodoroEnabled = preferences[Keys.POMODORO_ENABLED] ?: true,
            pomodoroWorkMinutes = preferences[Keys.POMODORO_WORK_MINUTES] ?: 25,
            pomodoroShortBreakMinutes = preferences[Keys.POMODORO_SHORT_BREAK_MINUTES] ?: 5,
            pomodoroLongBreakMinutes = preferences[Keys.POMODORO_LONG_BREAK_MINUTES] ?: 15,
            notificationEnabled = preferences[Keys.NOTIFICATION_ENABLED] ?: true,
            keepAliveEnabled = preferences[Keys.KEEP_ALIVE_ENABLED] ?: true,
            proximityMonitoringEnabled = preferences[Keys.PROXIMITY_MONITORING_ENABLED] ?: false,
            proximityBaselineEyeDistancePx = preferences[Keys.PROXIMITY_BASELINE_EYE_DISTANCE_PX] ?: 0f,
            proximityBaselineFaceWidthPercent = preferences[Keys.PROXIMITY_BASELINE_FACE_WIDTH_PERCENT] ?: 0,
            proximityDistanceMultiplierPercent = preferences[Keys.PROXIMITY_DISTANCE_MULTIPLIER_PERCENT] ?: 130,
            proximityCheckIntervalMinutes = preferences[Keys.PROXIMITY_CHECK_INTERVAL_MINUTES] ?: 3,
            proximityCaptureSeconds = preferences[Keys.PROXIMITY_CAPTURE_SECONDS] ?: 2,
            proximityFaceThresholdPercent = preferences[Keys.PROXIMITY_FACE_THRESHOLD_PERCENT] ?: 38,
            proximityAlertCooldownSeconds = preferences[Keys.PROXIMITY_ALERT_COOLDOWN_SECONDS] ?: 120,
            blinkMonitoringEnabled = preferences[Keys.BLINK_MONITORING_ENABLED] ?: false,
            blinkNoBlinkThresholdSeconds = preferences[Keys.BLINK_NO_BLINK_THRESHOLD_SECONDS] ?: 10,
            blinkAlertCooldownSeconds = preferences[Keys.BLINK_ALERT_COOLDOWN_SECONDS] ?: 60,
            ambientLightMonitoringEnabled = preferences[Keys.AMBIENT_LIGHT_MONITORING_ENABLED] ?: false,
            ambientLightLowLuxThreshold = preferences[Keys.AMBIENT_LIGHT_LOW_LUX_THRESHOLD] ?: 10,
            autoBrightnessEnabled = preferences[Keys.AUTO_BRIGHTNESS_ENABLED] ?: false,
            autoBrightnessMinPercent = preferences[Keys.AUTO_BRIGHTNESS_MIN_PERCENT] ?: 35,
            autoBrightnessMaxPercent = preferences[Keys.AUTO_BRIGHTNESS_MAX_PERCENT] ?: 85,
            globalOverlayEnabled = preferences[Keys.GLOBAL_OVERLAY_ENABLED] ?: true,
            overlayRestDurationSeconds = preferences[Keys.OVERLAY_REST_DURATION_SECONDS] ?: 20,
            overlayStrictDistancePercent = preferences[Keys.OVERLAY_STRICT_DISTANCE_PERCENT] ?: 160,
            shizukuAdvancedModeEnabled = preferences[Keys.SHIZUKU_ADVANCED_MODE_ENABLED] ?: false,
            shizukuContextAwareSamplingEnabled = preferences[Keys.SHIZUKU_CONTEXT_AWARE_SAMPLING_ENABLED] ?: true,
            shizukuServiceRecoveryEnabled = preferences[Keys.SHIZUKU_SERVICE_RECOVERY_ENABLED] ?: true,
            shizukuScreenOffGuardEnabled = preferences[Keys.SHIZUKU_SCREEN_OFF_GUARD_ENABLED] ?: true,
            shizukuLowBatteryGuardEnabled = preferences[Keys.SHIZUKU_LOW_BATTERY_GUARD_ENABLED] ?: true,
            shizukuPowerSaveGuardEnabled = preferences[Keys.SHIZUKU_POWER_SAVE_GUARD_ENABLED] ?: true,
            shizukuDndGuardEnabled = preferences[Keys.SHIZUKU_DND_GUARD_ENABLED] ?: false,
            shizukuThermalGuardEnabled = preferences[Keys.SHIZUKU_THERMAL_GUARD_ENABLED] ?: true,
            shizukuCameraPrivacyGuardEnabled = preferences[Keys.SHIZUKU_CAMERA_PRIVACY_GUARD_ENABLED] ?: true,
            shizukuNativeEyeProtectionEnabled = preferences[Keys.SHIZUKU_NATIVE_EYE_PROTECTION_ENABLED] ?: false,
            shizukuNativeColorTemperatureKelvin = preferences[Keys.SHIZUKU_NATIVE_COLOR_TEMPERATURE_KELVIN] ?: 4200,
            shizukuNativeBrightnessPercent = preferences[Keys.SHIZUKU_NATIVE_BRIGHTNESS_PERCENT] ?: 35,
            shizukuNativeExtraDimEnabled = preferences[Keys.SHIZUKU_NATIVE_EXTRA_DIM_ENABLED] ?: false,
            shizukuNativeExtraDimPercent = preferences[Keys.SHIZUKU_NATIVE_EXTRA_DIM_PERCENT] ?: 25,
        )
    }

    private suspend fun ensureLegacyMigrated() {
        if (mmkv.decodeBool(KEY_MMKV_MIGRATION_COMPLETE, false)) return
        migrationLock.withLock {
            if (mmkv.decodeBool(KEY_MMKV_MIGRATION_COMPLETE, false)) return
            if (!hasMmkvPersistedValues()) {
                val legacyPreferences = legacyDataStore.data
                    .catch { throwable ->
                        if (throwable is IOException) {
                            emit(emptyPreferences())
                        } else {
                            throw throwable
                        }
                    }
                    .first()
                if (legacyPreferences.asMap().isNotEmpty()) {
                    writeToMmkv(toEyeCarePreferences(legacyPreferences))
                    return
                }
            }
            mmkv.encode(KEY_MMKV_MIGRATION_COMPLETE, true)
            state.value = readFromMmkv()
        }
    }

    private fun hasMmkvPersistedValues(): Boolean {
        return mmkv.allKeys()?.any { key -> key != KEY_MMKV_MIGRATION_COMPLETE } == true
    }

    private fun readFromMmkv(): EyeCarePreferences {
        return EyeCarePreferences(
            hasPersistedValues = hasMmkvPersistedValues(),
            reminderEnabled = mmkv.decodeBool(Keys.REMINDER_ENABLED.name, true),
            warnIntervalMinutes = mmkv.decodeInt(Keys.WARN_INTERVAL_MINUTES.name, 20),
            restDurationSeconds = mmkv.decodeInt(Keys.REST_DURATION_SECONDS.name, 20),
            preAlertEnabled = mmkv.decodeBool(Keys.PRE_ALERT_ENABLED.name, true),
            preAlertSeconds = mmkv.decodeInt(Keys.PRE_ALERT_SECONDS.name, 60),
            useDynamicColors = mmkv.decodeBool(Keys.USE_DYNAMIC_COLORS.name, true),
            pomodoroEnabled = mmkv.decodeBool(Keys.POMODORO_ENABLED.name, true),
            pomodoroWorkMinutes = mmkv.decodeInt(Keys.POMODORO_WORK_MINUTES.name, 25),
            pomodoroShortBreakMinutes = mmkv.decodeInt(Keys.POMODORO_SHORT_BREAK_MINUTES.name, 5),
            pomodoroLongBreakMinutes = mmkv.decodeInt(Keys.POMODORO_LONG_BREAK_MINUTES.name, 15),
            notificationEnabled = mmkv.decodeBool(Keys.NOTIFICATION_ENABLED.name, true),
            keepAliveEnabled = mmkv.decodeBool(Keys.KEEP_ALIVE_ENABLED.name, true),
            proximityMonitoringEnabled = mmkv.decodeBool(Keys.PROXIMITY_MONITORING_ENABLED.name, false),
            proximityBaselineEyeDistancePx = mmkv.decodeFloat(Keys.PROXIMITY_BASELINE_EYE_DISTANCE_PX.name, 0f),
            proximityBaselineFaceWidthPercent = mmkv.decodeInt(Keys.PROXIMITY_BASELINE_FACE_WIDTH_PERCENT.name, 0),
            proximityDistanceMultiplierPercent = mmkv.decodeInt(Keys.PROXIMITY_DISTANCE_MULTIPLIER_PERCENT.name, 130),
            proximityCheckIntervalMinutes = mmkv.decodeInt(Keys.PROXIMITY_CHECK_INTERVAL_MINUTES.name, 3),
            proximityCaptureSeconds = mmkv.decodeInt(Keys.PROXIMITY_CAPTURE_SECONDS.name, 2),
            proximityFaceThresholdPercent = mmkv.decodeInt(Keys.PROXIMITY_FACE_THRESHOLD_PERCENT.name, 38),
            proximityAlertCooldownSeconds = mmkv.decodeInt(Keys.PROXIMITY_ALERT_COOLDOWN_SECONDS.name, 120),
            blinkMonitoringEnabled = mmkv.decodeBool(Keys.BLINK_MONITORING_ENABLED.name, false),
            blinkNoBlinkThresholdSeconds = mmkv.decodeInt(Keys.BLINK_NO_BLINK_THRESHOLD_SECONDS.name, 10),
            blinkAlertCooldownSeconds = mmkv.decodeInt(Keys.BLINK_ALERT_COOLDOWN_SECONDS.name, 60),
            ambientLightMonitoringEnabled = mmkv.decodeBool(Keys.AMBIENT_LIGHT_MONITORING_ENABLED.name, false),
            ambientLightLowLuxThreshold = mmkv.decodeInt(Keys.AMBIENT_LIGHT_LOW_LUX_THRESHOLD.name, 10),
            autoBrightnessEnabled = mmkv.decodeBool(Keys.AUTO_BRIGHTNESS_ENABLED.name, false),
            autoBrightnessMinPercent = mmkv.decodeInt(Keys.AUTO_BRIGHTNESS_MIN_PERCENT.name, 35),
            autoBrightnessMaxPercent = mmkv.decodeInt(Keys.AUTO_BRIGHTNESS_MAX_PERCENT.name, 85),
            globalOverlayEnabled = mmkv.decodeBool(Keys.GLOBAL_OVERLAY_ENABLED.name, true),
            overlayRestDurationSeconds = mmkv.decodeInt(Keys.OVERLAY_REST_DURATION_SECONDS.name, 20),
            overlayStrictDistancePercent = mmkv.decodeInt(Keys.OVERLAY_STRICT_DISTANCE_PERCENT.name, 160),
            shizukuAdvancedModeEnabled = mmkv.decodeBool(Keys.SHIZUKU_ADVANCED_MODE_ENABLED.name, false),
            shizukuContextAwareSamplingEnabled = mmkv.decodeBool(Keys.SHIZUKU_CONTEXT_AWARE_SAMPLING_ENABLED.name, true),
            shizukuServiceRecoveryEnabled = mmkv.decodeBool(Keys.SHIZUKU_SERVICE_RECOVERY_ENABLED.name, true),
            shizukuScreenOffGuardEnabled = mmkv.decodeBool(Keys.SHIZUKU_SCREEN_OFF_GUARD_ENABLED.name, true),
            shizukuLowBatteryGuardEnabled = mmkv.decodeBool(Keys.SHIZUKU_LOW_BATTERY_GUARD_ENABLED.name, true),
            shizukuPowerSaveGuardEnabled = mmkv.decodeBool(Keys.SHIZUKU_POWER_SAVE_GUARD_ENABLED.name, true),
            shizukuDndGuardEnabled = mmkv.decodeBool(Keys.SHIZUKU_DND_GUARD_ENABLED.name, false),
            shizukuThermalGuardEnabled = mmkv.decodeBool(Keys.SHIZUKU_THERMAL_GUARD_ENABLED.name, true),
            shizukuCameraPrivacyGuardEnabled = mmkv.decodeBool(Keys.SHIZUKU_CAMERA_PRIVACY_GUARD_ENABLED.name, true),
            shizukuNativeEyeProtectionEnabled = mmkv.decodeBool(Keys.SHIZUKU_NATIVE_EYE_PROTECTION_ENABLED.name, false),
            shizukuNativeColorTemperatureKelvin = mmkv.decodeInt(Keys.SHIZUKU_NATIVE_COLOR_TEMPERATURE_KELVIN.name, 4200),
            shizukuNativeBrightnessPercent = mmkv.decodeInt(Keys.SHIZUKU_NATIVE_BRIGHTNESS_PERCENT.name, 35),
            shizukuNativeExtraDimEnabled = mmkv.decodeBool(Keys.SHIZUKU_NATIVE_EXTRA_DIM_ENABLED.name, false),
            shizukuNativeExtraDimPercent = mmkv.decodeInt(Keys.SHIZUKU_NATIVE_EXTRA_DIM_PERCENT.name, 25),
        )
    }

    private fun writeToMmkv(preferences: EyeCarePreferences) {
        mmkv.encode(Keys.REMINDER_ENABLED.name, preferences.reminderEnabled)
        mmkv.encode(Keys.WARN_INTERVAL_MINUTES.name, preferences.warnIntervalMinutes)
        mmkv.encode(Keys.REST_DURATION_SECONDS.name, preferences.restDurationSeconds)
        mmkv.encode(Keys.PRE_ALERT_ENABLED.name, preferences.preAlertEnabled)
        mmkv.encode(Keys.PRE_ALERT_SECONDS.name, preferences.preAlertSeconds)
        mmkv.encode(Keys.USE_DYNAMIC_COLORS.name, preferences.useDynamicColors)
        mmkv.encode(Keys.POMODORO_ENABLED.name, preferences.pomodoroEnabled)
        mmkv.encode(Keys.POMODORO_WORK_MINUTES.name, preferences.pomodoroWorkMinutes)
        mmkv.encode(Keys.POMODORO_SHORT_BREAK_MINUTES.name, preferences.pomodoroShortBreakMinutes)
        mmkv.encode(Keys.POMODORO_LONG_BREAK_MINUTES.name, preferences.pomodoroLongBreakMinutes)
        mmkv.encode(Keys.NOTIFICATION_ENABLED.name, preferences.notificationEnabled)
        mmkv.encode(Keys.KEEP_ALIVE_ENABLED.name, preferences.keepAliveEnabled)
        mmkv.encode(Keys.PROXIMITY_MONITORING_ENABLED.name, preferences.proximityMonitoringEnabled)
        mmkv.encode(Keys.PROXIMITY_BASELINE_EYE_DISTANCE_PX.name, preferences.proximityBaselineEyeDistancePx)
        mmkv.encode(Keys.PROXIMITY_BASELINE_FACE_WIDTH_PERCENT.name, preferences.proximityBaselineFaceWidthPercent)
        mmkv.encode(Keys.PROXIMITY_DISTANCE_MULTIPLIER_PERCENT.name, preferences.proximityDistanceMultiplierPercent)
        mmkv.encode(Keys.PROXIMITY_CHECK_INTERVAL_MINUTES.name, preferences.proximityCheckIntervalMinutes)
        mmkv.encode(Keys.PROXIMITY_CAPTURE_SECONDS.name, preferences.proximityCaptureSeconds)
        mmkv.encode(Keys.PROXIMITY_FACE_THRESHOLD_PERCENT.name, preferences.proximityFaceThresholdPercent)
        mmkv.encode(Keys.PROXIMITY_ALERT_COOLDOWN_SECONDS.name, preferences.proximityAlertCooldownSeconds)
        mmkv.encode(Keys.BLINK_MONITORING_ENABLED.name, preferences.blinkMonitoringEnabled)
        mmkv.encode(Keys.BLINK_NO_BLINK_THRESHOLD_SECONDS.name, preferences.blinkNoBlinkThresholdSeconds)
        mmkv.encode(Keys.BLINK_ALERT_COOLDOWN_SECONDS.name, preferences.blinkAlertCooldownSeconds)
        mmkv.encode(Keys.AMBIENT_LIGHT_MONITORING_ENABLED.name, preferences.ambientLightMonitoringEnabled)
        mmkv.encode(Keys.AMBIENT_LIGHT_LOW_LUX_THRESHOLD.name, preferences.ambientLightLowLuxThreshold)
        mmkv.encode(Keys.AUTO_BRIGHTNESS_ENABLED.name, preferences.autoBrightnessEnabled)
        mmkv.encode(Keys.AUTO_BRIGHTNESS_MIN_PERCENT.name, preferences.autoBrightnessMinPercent)
        mmkv.encode(Keys.AUTO_BRIGHTNESS_MAX_PERCENT.name, preferences.autoBrightnessMaxPercent)
        mmkv.encode(Keys.GLOBAL_OVERLAY_ENABLED.name, preferences.globalOverlayEnabled)
        mmkv.encode(Keys.OVERLAY_REST_DURATION_SECONDS.name, preferences.overlayRestDurationSeconds)
        mmkv.encode(Keys.OVERLAY_STRICT_DISTANCE_PERCENT.name, preferences.overlayStrictDistancePercent)
        mmkv.encode(Keys.SHIZUKU_ADVANCED_MODE_ENABLED.name, preferences.shizukuAdvancedModeEnabled)
        mmkv.encode(Keys.SHIZUKU_CONTEXT_AWARE_SAMPLING_ENABLED.name, preferences.shizukuContextAwareSamplingEnabled)
        mmkv.encode(Keys.SHIZUKU_SERVICE_RECOVERY_ENABLED.name, preferences.shizukuServiceRecoveryEnabled)
        mmkv.encode(Keys.SHIZUKU_SCREEN_OFF_GUARD_ENABLED.name, preferences.shizukuScreenOffGuardEnabled)
        mmkv.encode(Keys.SHIZUKU_LOW_BATTERY_GUARD_ENABLED.name, preferences.shizukuLowBatteryGuardEnabled)
        mmkv.encode(Keys.SHIZUKU_POWER_SAVE_GUARD_ENABLED.name, preferences.shizukuPowerSaveGuardEnabled)
        mmkv.encode(Keys.SHIZUKU_DND_GUARD_ENABLED.name, preferences.shizukuDndGuardEnabled)
        mmkv.encode(Keys.SHIZUKU_THERMAL_GUARD_ENABLED.name, preferences.shizukuThermalGuardEnabled)
        mmkv.encode(Keys.SHIZUKU_CAMERA_PRIVACY_GUARD_ENABLED.name, preferences.shizukuCameraPrivacyGuardEnabled)
        mmkv.encode(Keys.SHIZUKU_NATIVE_EYE_PROTECTION_ENABLED.name, preferences.shizukuNativeEyeProtectionEnabled)
        mmkv.encode(Keys.SHIZUKU_NATIVE_COLOR_TEMPERATURE_KELVIN.name, preferences.shizukuNativeColorTemperatureKelvin)
        mmkv.encode(Keys.SHIZUKU_NATIVE_BRIGHTNESS_PERCENT.name, preferences.shizukuNativeBrightnessPercent)
        mmkv.encode(Keys.SHIZUKU_NATIVE_EXTRA_DIM_ENABLED.name, preferences.shizukuNativeExtraDimEnabled)
        mmkv.encode(Keys.SHIZUKU_NATIVE_EXTRA_DIM_PERCENT.name, preferences.shizukuNativeExtraDimPercent)
        mmkv.encode(KEY_MMKV_MIGRATION_COMPLETE, true)
        state.value = readFromMmkv()
    }

    private fun AppSettingsEntity.toEyeCarePreferences(): EyeCarePreferences {
        return EyeCarePreferences(
            hasPersistedValues = true,
            reminderEnabled = reminderEnabled,
            warnIntervalMinutes = warnIntervalMinutes,
            restDurationSeconds = restDurationSeconds,
            preAlertEnabled = preAlertEnabled,
            preAlertSeconds = preAlertSeconds,
            useDynamicColors = useDynamicColors,
            pomodoroEnabled = pomodoroEnabled,
            pomodoroWorkMinutes = pomodoroWorkMinutes,
            pomodoroShortBreakMinutes = pomodoroShortBreakMinutes,
            pomodoroLongBreakMinutes = pomodoroLongBreakMinutes,
            notificationEnabled = notificationEnabled,
            keepAliveEnabled = keepAliveEnabled,
            proximityMonitoringEnabled = proximityMonitoringEnabled,
            proximityBaselineEyeDistancePx = proximityBaselineEyeDistancePx,
            proximityBaselineFaceWidthPercent = proximityBaselineFaceWidthPercent,
            proximityDistanceMultiplierPercent = proximityDistanceMultiplierPercent,
            proximityCheckIntervalMinutes = proximityCheckIntervalMinutes,
            proximityCaptureSeconds = proximityCaptureSeconds,
            proximityFaceThresholdPercent = proximityFaceThresholdPercent,
            proximityAlertCooldownSeconds = proximityAlertCooldownSeconds,
            blinkMonitoringEnabled = blinkMonitoringEnabled,
            blinkNoBlinkThresholdSeconds = blinkNoBlinkThresholdSeconds,
            blinkAlertCooldownSeconds = blinkAlertCooldownSeconds,
            ambientLightMonitoringEnabled = ambientLightMonitoringEnabled,
            ambientLightLowLuxThreshold = ambientLightLowLuxThreshold,
            autoBrightnessEnabled = autoBrightnessEnabled,
            autoBrightnessMinPercent = autoBrightnessMinPercent,
            autoBrightnessMaxPercent = autoBrightnessMaxPercent,
            globalOverlayEnabled = globalOverlayEnabled,
            overlayRestDurationSeconds = overlayRestDurationSeconds,
            overlayStrictDistancePercent = overlayStrictDistancePercent,
            shizukuAdvancedModeEnabled = shizukuAdvancedModeEnabled,
            shizukuContextAwareSamplingEnabled = shizukuContextAwareSamplingEnabled,
            shizukuServiceRecoveryEnabled = shizukuServiceRecoveryEnabled,
            shizukuScreenOffGuardEnabled = shizukuScreenOffGuardEnabled,
            shizukuLowBatteryGuardEnabled = shizukuLowBatteryGuardEnabled,
            shizukuPowerSaveGuardEnabled = shizukuPowerSaveGuardEnabled,
            shizukuDndGuardEnabled = shizukuDndGuardEnabled,
            shizukuThermalGuardEnabled = shizukuThermalGuardEnabled,
            shizukuCameraPrivacyGuardEnabled = shizukuCameraPrivacyGuardEnabled,
            shizukuNativeEyeProtectionEnabled = shizukuNativeEyeProtectionEnabled,
            shizukuNativeColorTemperatureKelvin = shizukuNativeColorTemperatureKelvin,
            shizukuNativeBrightnessPercent = shizukuNativeBrightnessPercent,
            shizukuNativeExtraDimEnabled = shizukuNativeExtraDimEnabled,
            shizukuNativeExtraDimPercent = shizukuNativeExtraDimPercent,
        )
    }

    private object Keys {
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val WARN_INTERVAL_MINUTES = intPreferencesKey("warn_interval_minutes")
        val REST_DURATION_SECONDS = intPreferencesKey("rest_duration_seconds")
        val PRE_ALERT_ENABLED = booleanPreferencesKey("pre_alert_enabled")
        val PRE_ALERT_SECONDS = intPreferencesKey("pre_alert_seconds")
        val USE_DYNAMIC_COLORS = booleanPreferencesKey("use_dynamic_colors")
        val POMODORO_ENABLED = booleanPreferencesKey("pomodoro_enabled")
        val POMODORO_WORK_MINUTES = intPreferencesKey("pomodoro_work_minutes")
        val POMODORO_SHORT_BREAK_MINUTES = intPreferencesKey("pomodoro_short_break_minutes")
        val POMODORO_LONG_BREAK_MINUTES = intPreferencesKey("pomodoro_long_break_minutes")
        val NOTIFICATION_ENABLED = booleanPreferencesKey("notification_enabled")
        val KEEP_ALIVE_ENABLED = booleanPreferencesKey("keep_alive_enabled")
        val PROXIMITY_MONITORING_ENABLED = booleanPreferencesKey("proximity_monitoring_enabled")
        val PROXIMITY_BASELINE_EYE_DISTANCE_PX = floatPreferencesKey("proximity_baseline_eye_distance_px")
        val PROXIMITY_BASELINE_FACE_WIDTH_PERCENT = intPreferencesKey("proximity_baseline_face_width_percent")
        val PROXIMITY_DISTANCE_MULTIPLIER_PERCENT = intPreferencesKey("proximity_distance_multiplier_percent")
        val PROXIMITY_CHECK_INTERVAL_MINUTES = intPreferencesKey("proximity_check_interval_minutes")
        val PROXIMITY_CAPTURE_SECONDS = intPreferencesKey("proximity_capture_seconds")
        val PROXIMITY_FACE_THRESHOLD_PERCENT = intPreferencesKey("proximity_face_threshold_percent")
        val PROXIMITY_ALERT_COOLDOWN_SECONDS = intPreferencesKey("proximity_alert_cooldown_seconds")
        val BLINK_MONITORING_ENABLED = booleanPreferencesKey("blink_monitoring_enabled")
        val BLINK_NO_BLINK_THRESHOLD_SECONDS = intPreferencesKey("blink_no_blink_threshold_seconds")
        val BLINK_ALERT_COOLDOWN_SECONDS = intPreferencesKey("blink_alert_cooldown_seconds")
        val AMBIENT_LIGHT_MONITORING_ENABLED = booleanPreferencesKey("ambient_light_monitoring_enabled")
        val AMBIENT_LIGHT_LOW_LUX_THRESHOLD = intPreferencesKey("ambient_light_low_lux_threshold")
        val AUTO_BRIGHTNESS_ENABLED = booleanPreferencesKey("auto_brightness_enabled")
        val AUTO_BRIGHTNESS_MIN_PERCENT = intPreferencesKey("auto_brightness_min_percent")
        val AUTO_BRIGHTNESS_MAX_PERCENT = intPreferencesKey("auto_brightness_max_percent")
        val GLOBAL_OVERLAY_ENABLED = booleanPreferencesKey("global_overlay_enabled")
        val OVERLAY_REST_DURATION_SECONDS = intPreferencesKey("overlay_rest_duration_seconds")
        val OVERLAY_STRICT_DISTANCE_PERCENT = intPreferencesKey("overlay_strict_distance_percent")
        val SHIZUKU_ADVANCED_MODE_ENABLED = booleanPreferencesKey("shizuku_advanced_mode_enabled")
        val SHIZUKU_CONTEXT_AWARE_SAMPLING_ENABLED = booleanPreferencesKey("shizuku_context_aware_sampling_enabled")
        val SHIZUKU_SERVICE_RECOVERY_ENABLED = booleanPreferencesKey("shizuku_service_recovery_enabled")
        val SHIZUKU_SCREEN_OFF_GUARD_ENABLED = booleanPreferencesKey("shizuku_screen_off_guard_enabled")
        val SHIZUKU_LOW_BATTERY_GUARD_ENABLED = booleanPreferencesKey("shizuku_low_battery_guard_enabled")
        val SHIZUKU_POWER_SAVE_GUARD_ENABLED = booleanPreferencesKey("shizuku_power_save_guard_enabled")
        val SHIZUKU_DND_GUARD_ENABLED = booleanPreferencesKey("shizuku_dnd_guard_enabled")
        val SHIZUKU_THERMAL_GUARD_ENABLED = booleanPreferencesKey("shizuku_thermal_guard_enabled")
        val SHIZUKU_CAMERA_PRIVACY_GUARD_ENABLED = booleanPreferencesKey("shizuku_camera_privacy_guard_enabled")
        val SHIZUKU_NATIVE_EYE_PROTECTION_ENABLED = booleanPreferencesKey("shizuku_native_eye_protection_enabled")
        val SHIZUKU_NATIVE_COLOR_TEMPERATURE_KELVIN = intPreferencesKey("shizuku_native_color_temperature_kelvin")
        val SHIZUKU_NATIVE_BRIGHTNESS_PERCENT = intPreferencesKey("shizuku_native_brightness_percent")
        val SHIZUKU_NATIVE_EXTRA_DIM_ENABLED = booleanPreferencesKey("shizuku_native_extra_dim_enabled")
        val SHIZUKU_NATIVE_EXTRA_DIM_PERCENT = intPreferencesKey("shizuku_native_extra_dim_percent")
    }
}

fun AppSettingsEntity.withEyeCarePreferences(preferences: EyeCarePreferences): AppSettingsEntity {
    if (!preferences.hasPersistedValues) return this
    return copy(
        reminderEnabled = preferences.reminderEnabled,
        warnIntervalMinutes = preferences.warnIntervalMinutes,
        restDurationSeconds = preferences.restDurationSeconds,
        preAlertEnabled = preferences.preAlertEnabled,
        preAlertSeconds = preferences.preAlertSeconds,
        useDynamicColors = preferences.useDynamicColors,
        pomodoroEnabled = preferences.pomodoroEnabled,
        pomodoroWorkMinutes = preferences.pomodoroWorkMinutes,
        pomodoroShortBreakMinutes = preferences.pomodoroShortBreakMinutes,
        pomodoroLongBreakMinutes = preferences.pomodoroLongBreakMinutes,
        notificationEnabled = preferences.notificationEnabled,
        keepAliveEnabled = preferences.keepAliveEnabled,
        proximityMonitoringEnabled = preferences.proximityMonitoringEnabled,
        proximityBaselineEyeDistancePx = preferences.proximityBaselineEyeDistancePx,
        proximityBaselineFaceWidthPercent = preferences.proximityBaselineFaceWidthPercent,
        proximityDistanceMultiplierPercent = preferences.proximityDistanceMultiplierPercent,
        proximityCheckIntervalMinutes = preferences.proximityCheckIntervalMinutes,
        proximityCaptureSeconds = preferences.proximityCaptureSeconds,
        proximityFaceThresholdPercent = preferences.proximityFaceThresholdPercent,
        proximityAlertCooldownSeconds = preferences.proximityAlertCooldownSeconds,
        blinkMonitoringEnabled = preferences.blinkMonitoringEnabled,
        blinkNoBlinkThresholdSeconds = preferences.blinkNoBlinkThresholdSeconds,
        blinkAlertCooldownSeconds = preferences.blinkAlertCooldownSeconds,
        ambientLightMonitoringEnabled = preferences.ambientLightMonitoringEnabled,
        ambientLightLowLuxThreshold = preferences.ambientLightLowLuxThreshold,
        autoBrightnessEnabled = preferences.autoBrightnessEnabled,
        autoBrightnessMinPercent = preferences.autoBrightnessMinPercent,
        autoBrightnessMaxPercent = preferences.autoBrightnessMaxPercent,
        globalOverlayEnabled = preferences.globalOverlayEnabled,
        overlayRestDurationSeconds = preferences.overlayRestDurationSeconds,
        overlayStrictDistancePercent = preferences.overlayStrictDistancePercent,
        shizukuAdvancedModeEnabled = preferences.shizukuAdvancedModeEnabled,
        shizukuContextAwareSamplingEnabled = preferences.shizukuContextAwareSamplingEnabled,
        shizukuServiceRecoveryEnabled = preferences.shizukuServiceRecoveryEnabled,
        shizukuScreenOffGuardEnabled = preferences.shizukuScreenOffGuardEnabled,
        shizukuLowBatteryGuardEnabled = preferences.shizukuLowBatteryGuardEnabled,
        shizukuPowerSaveGuardEnabled = preferences.shizukuPowerSaveGuardEnabled,
        shizukuDndGuardEnabled = preferences.shizukuDndGuardEnabled,
        shizukuThermalGuardEnabled = preferences.shizukuThermalGuardEnabled,
        shizukuCameraPrivacyGuardEnabled = preferences.shizukuCameraPrivacyGuardEnabled,
        shizukuNativeEyeProtectionEnabled = preferences.shizukuNativeEyeProtectionEnabled,
        shizukuNativeColorTemperatureKelvin = preferences.shizukuNativeColorTemperatureKelvin,
        shizukuNativeBrightnessPercent = preferences.shizukuNativeBrightnessPercent,
        shizukuNativeExtraDimEnabled = preferences.shizukuNativeExtraDimEnabled,
        shizukuNativeExtraDimPercent = preferences.shizukuNativeExtraDimPercent,
    )
}
