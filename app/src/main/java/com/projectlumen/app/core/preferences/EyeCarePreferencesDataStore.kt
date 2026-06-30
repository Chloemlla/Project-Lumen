package com.projectlumen.app.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.projectlumen.app.core.database.entities.AppSettingsEntity
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.eyeCarePreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "eye_care_preferences",
)

data class EyeCarePreferences(
    val hasPersistedValues: Boolean = false,
    val reminderEnabled: Boolean = true,
    val warnIntervalMinutes: Int = 20,
    val restDurationSeconds: Int = 20,
    val preAlertEnabled: Boolean = true,
    val preAlertSeconds: Int = 60,
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
)

class EyeCarePreferencesDataStore(context: Context) {
    private val dataStore = context.applicationContext.eyeCarePreferencesDataStore

    fun observe(): Flow<EyeCarePreferences> {
        return dataStore.data
            .catch { throwable ->
                if (throwable is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw throwable
                }
            }
            .map(::toEyeCarePreferences)
    }

    suspend fun read(): EyeCarePreferences = observe().first()

    suspend fun saveFromSettings(settings: AppSettingsEntity) {
        dataStore.edit { preferences ->
            preferences[Keys.REMINDER_ENABLED] = settings.reminderEnabled
            preferences[Keys.WARN_INTERVAL_MINUTES] = settings.warnIntervalMinutes
            preferences[Keys.REST_DURATION_SECONDS] = settings.restDurationSeconds
            preferences[Keys.PRE_ALERT_ENABLED] = settings.preAlertEnabled
            preferences[Keys.PRE_ALERT_SECONDS] = settings.preAlertSeconds
            preferences[Keys.POMODORO_ENABLED] = settings.pomodoroEnabled
            preferences[Keys.POMODORO_WORK_MINUTES] = settings.pomodoroWorkMinutes
            preferences[Keys.POMODORO_SHORT_BREAK_MINUTES] = settings.pomodoroShortBreakMinutes
            preferences[Keys.POMODORO_LONG_BREAK_MINUTES] = settings.pomodoroLongBreakMinutes
            preferences[Keys.NOTIFICATION_ENABLED] = settings.notificationEnabled
            preferences[Keys.KEEP_ALIVE_ENABLED] = settings.keepAliveEnabled
            preferences[Keys.PROXIMITY_MONITORING_ENABLED] = settings.proximityMonitoringEnabled
            preferences[Keys.PROXIMITY_BASELINE_EYE_DISTANCE_PX] = settings.proximityBaselineEyeDistancePx
            preferences[Keys.PROXIMITY_BASELINE_FACE_WIDTH_PERCENT] = settings.proximityBaselineFaceWidthPercent
            preferences[Keys.PROXIMITY_DISTANCE_MULTIPLIER_PERCENT] = settings.proximityDistanceMultiplierPercent
            preferences[Keys.PROXIMITY_CHECK_INTERVAL_MINUTES] = settings.proximityCheckIntervalMinutes
            preferences[Keys.PROXIMITY_CAPTURE_SECONDS] = settings.proximityCaptureSeconds
            preferences[Keys.PROXIMITY_FACE_THRESHOLD_PERCENT] = settings.proximityFaceThresholdPercent
            preferences[Keys.PROXIMITY_ALERT_COOLDOWN_SECONDS] = settings.proximityAlertCooldownSeconds
            preferences[Keys.BLINK_MONITORING_ENABLED] = settings.blinkMonitoringEnabled
            preferences[Keys.BLINK_NO_BLINK_THRESHOLD_SECONDS] = settings.blinkNoBlinkThresholdSeconds
            preferences[Keys.BLINK_ALERT_COOLDOWN_SECONDS] = settings.blinkAlertCooldownSeconds
            preferences[Keys.AMBIENT_LIGHT_MONITORING_ENABLED] = settings.ambientLightMonitoringEnabled
            preferences[Keys.AMBIENT_LIGHT_LOW_LUX_THRESHOLD] = settings.ambientLightLowLuxThreshold
            preferences[Keys.AUTO_BRIGHTNESS_ENABLED] = settings.autoBrightnessEnabled
            preferences[Keys.AUTO_BRIGHTNESS_MIN_PERCENT] = settings.autoBrightnessMinPercent
            preferences[Keys.AUTO_BRIGHTNESS_MAX_PERCENT] = settings.autoBrightnessMaxPercent
            preferences[Keys.GLOBAL_OVERLAY_ENABLED] = settings.globalOverlayEnabled
            preferences[Keys.OVERLAY_REST_DURATION_SECONDS] = settings.overlayRestDurationSeconds
            preferences[Keys.OVERLAY_STRICT_DISTANCE_PERCENT] = settings.overlayStrictDistancePercent
            preferences[Keys.SHIZUKU_ADVANCED_MODE_ENABLED] = settings.shizukuAdvancedModeEnabled
            preferences[Keys.SHIZUKU_CONTEXT_AWARE_SAMPLING_ENABLED] = settings.shizukuContextAwareSamplingEnabled
            preferences[Keys.SHIZUKU_SERVICE_RECOVERY_ENABLED] = settings.shizukuServiceRecoveryEnabled
        }
    }

    private fun toEyeCarePreferences(preferences: Preferences): EyeCarePreferences {
        return EyeCarePreferences(
            hasPersistedValues = preferences.asMap().isNotEmpty(),
            reminderEnabled = preferences[Keys.REMINDER_ENABLED] ?: true,
            warnIntervalMinutes = preferences[Keys.WARN_INTERVAL_MINUTES] ?: 20,
            restDurationSeconds = preferences[Keys.REST_DURATION_SECONDS] ?: 20,
            preAlertEnabled = preferences[Keys.PRE_ALERT_ENABLED] ?: true,
            preAlertSeconds = preferences[Keys.PRE_ALERT_SECONDS] ?: 60,
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
        )
    }

    private object Keys {
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val WARN_INTERVAL_MINUTES = intPreferencesKey("warn_interval_minutes")
        val REST_DURATION_SECONDS = intPreferencesKey("rest_duration_seconds")
        val PRE_ALERT_ENABLED = booleanPreferencesKey("pre_alert_enabled")
        val PRE_ALERT_SECONDS = intPreferencesKey("pre_alert_seconds")
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
    )
}
