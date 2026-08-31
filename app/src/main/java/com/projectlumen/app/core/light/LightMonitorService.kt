package com.projectlumen.app.core.light

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import com.projectlumen.app.ProjectLumenApplication
import com.projectlumen.app.core.constants.NotificationIds
import com.projectlumen.app.core.database.entities.AppSettingsEntity
import com.projectlumen.app.core.repositories.StatisticsRepository
import com.projectlumen.app.core.services.ForegroundServiceController
import com.projectlumen.app.core.services.ForegroundServiceStartEligibility
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

class LightMonitorService : Service(), SensorEventListener {
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, throwable ->
            runCatching { application as? ProjectLumenApplication }
                .getOrNull()
                ?.recordHandledFailure(throwable)
        },
    )
    private val statisticsRepository by lazy {
        val database = (application as ProjectLumenApplication).database
        StatisticsRepository(database.dailyEyeStatsDao(), database.dailyPomodoroStatsDao())
    }
    private val brightnessPreferences: SharedPreferences by lazy {
        getSharedPreferences(BRIGHTNESS_PREFERENCES_NAME, Context.MODE_PRIVATE)
    }
    private lateinit var sensorManager: SensorManager
    private var sensorRegistered = false
    private var lastHandledAt: Long = 0L

    @Volatile
    private var smoothedLux: Float? = null

    @Volatile
    private var lastAppliedBrightnessPercent: Int = -1

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(SensorManager::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as ProjectLumenApplication
        val promoted = ForegroundServiceController.promote(
            service = this,
            notificationId = NotificationIds.LOW_LIGHT_FOREGROUND,
            notificationProvider = { app.notifications.buildLightMonitorForegroundNotification() },
            foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )
        if (!promoted) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        if (lightSensor == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (!sensorRegistered) {
            sensorRegistered = sensorManager.registerListener(
                this,
                lightSensor,
                SensorManager.SENSOR_DELAY_NORMAL,
            )
        }
        if (!sensorRegistered) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        scope.launch {
            val settings = app.settingsRepository().get()
            if (settings == null || (!settings.ambientLightMonitoringEnabled && !settings.autoBrightnessEnabled)) {
                stopSelf(startId)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        if (sensorRegistered) {
            sensorManager.unregisterListener(this)
            sensorRegistered = false
        }
        restoreSystemBrightnessMode()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_LIGHT) return
        val now = System.currentTimeMillis()
        if (now - lastHandledAt < SAMPLE_INTERVAL_MILLIS) return
        lastHandledAt = now
        val lux = event.values.firstOrNull() ?: return
        scope.launch { handleLux(lux, now) }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private suspend fun handleLux(lux: Float, nowMillis: Long) {
        val app = application as ProjectLumenApplication
        val settings = app.settingsRepository().get() ?: return
        if (!settings.ambientLightMonitoringEnabled && !settings.autoBrightnessEnabled) {
            stopSelf()
            return
        }
        val tooDark = lux < settings.ambientLightLowLuxThreshold
        val runtimeRepository = app.runtimeRepository()
        val runtime = runtimeRepository.get()
        val shouldWarn = settings.ambientLightMonitoringEnabled &&
            tooDark &&
            nowMillis - (runtime?.ambientLastWarningAt ?: 0L) >= LOW_LIGHT_COOLDOWN_MILLIS
        if (settings.autoBrightnessEnabled) {
            applyBrightness(lux, settings)
        } else {
            restoreSystemBrightnessMode()
        }
        if (shouldWarn) {
            app.notifications.showLowLightWarning(lux)
            statisticsRepository.updateEyeStats(settings.statsEnabled, nowMillis) {
                it.copy(lowLightWarningCount = it.lowLightWarningCount + 1)
            }
        }
        if (shouldWarn || tooDark != runtime?.ambientTooDark || isLuxWorthPersisting(lux, runtime?.ambientLastLux ?: 0f)) {
            runtimeRepository.update {
                it.copy(
                    ambientLastLux = lux,
                    ambientTooDark = tooDark,
                    ambientLastWarningAt = if (shouldWarn) nowMillis else it.ambientLastWarningAt,
                    updatedAt = nowMillis,
                )
            }
        }
        if (shouldWarn) {
            runCatching { app.telemetry.uploadCurrentSnapshot(force = true) }
                .onFailure(app::recordHandledFailure)
        }
    }

    private fun isLuxWorthPersisting(lux: Float, persistedLux: Float): Boolean {
        return abs(lux - persistedLux) >= maxOf(LUX_PERSIST_ABSOLUTE_DELTA, persistedLux * LUX_PERSIST_RELATIVE_DELTA)
    }

    private suspend fun applyBrightness(lux: Float, settings: AppSettingsEntity) {
        val min = settings.autoBrightnessMinPercent.coerceIn(1, 100)
        val max = settings.autoBrightnessMaxPercent.coerceIn(min, 100)
        val averaged = smoothedLux?.let { it * LUX_SMOOTHING_RETAIN + lux * (1f - LUX_SMOOTHING_RETAIN) } ?: lux
        smoothedLux = averaged
        // 人眼对照度的感知接近对数，线性映射会让中低照度段亮度跳变过大。
        val ratio = ln(1f + averaged.coerceIn(0f, MAX_MAPPED_LUX)) / ln(1f + MAX_MAPPED_LUX)
        val percent = (min + (max - min) * ratio).roundToInt().coerceIn(1, 100)
        val applied = lastAppliedBrightnessPercent
        if (applied >= 0 && abs(percent - applied) < BRIGHTNESS_STEP_THRESHOLD_PERCENT) return
        val app = application as ProjectLumenApplication
        if (
            settings.shizukuAdvancedModeEnabled &&
            settings.shizukuNativeEyeProtectionEnabled &&
            app.shizuku.applySystemBrightness(
                percent = percent,
                extraDimPercent = extraDimPercentForAutoBrightness(percent, settings),
            )
        ) {
            lastAppliedBrightnessPercent = percent
            return
        }
        if (!Settings.System.canWrite(this)) return
        rememberSystemBrightnessMode()
        val brightness = ((percent / 100f) * 255f).roundToInt().coerceIn(1, 255)
        runCatching {
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, brightness)
        }.onSuccess {
            lastAppliedBrightnessPercent = percent
        }.onFailure(app::recordHandledFailure)
    }

    private fun rememberSystemBrightnessMode() {
        if (brightnessPreferences.contains(KEY_ORIGINAL_BRIGHTNESS_MODE)) return
        val mode = runCatching {
            Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE)
        }.getOrNull() ?: return
        brightnessPreferences.edit().putInt(KEY_ORIGINAL_BRIGHTNESS_MODE, mode).apply()
    }

    private fun restoreSystemBrightnessMode() {
        val original = brightnessPreferences.getInt(KEY_ORIGINAL_BRIGHTNESS_MODE, UNKNOWN_BRIGHTNESS_MODE)
        if (original == UNKNOWN_BRIGHTNESS_MODE) return
        brightnessPreferences.edit().remove(KEY_ORIGINAL_BRIGHTNESS_MODE).apply()
        if (original == Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL) return
        if (!Settings.System.canWrite(this)) return
        val current = runCatching {
            Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE)
        }.getOrNull()
        // 用户可能在此期间自己改回了自动亮度，只恢复仍然是我们写入的手动模式。
        if (current != Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL) return
        runCatching {
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, original)
        }
    }

    private fun extraDimPercentForAutoBrightness(percent: Int, settings: AppSettingsEntity): Int {
        val configuredExtraDim = if (settings.shizukuNativeExtraDimEnabled) {
            settings.shizukuNativeExtraDimPercent.coerceIn(1, 100)
        } else {
            0
        }
        val ultraLowExtraDim = if (percent <= ULTRA_LOW_BRIGHTNESS_THRESHOLD_PERCENT) {
            ((ULTRA_LOW_BRIGHTNESS_THRESHOLD_PERCENT - percent + 1) * 10).coerceIn(10, 100)
        } else {
            0
        }
        return maxOf(configuredExtraDim, ultraLowExtraDim)
    }

    companion object {
        private const val LOW_LIGHT_COOLDOWN_MILLIS = 120_000L
        private const val ULTRA_LOW_BRIGHTNESS_THRESHOLD_PERCENT = 10
        private const val SAMPLE_INTERVAL_MILLIS = 10_000L
        private const val LUX_PERSIST_ABSOLUTE_DELTA = 2f
        private const val LUX_PERSIST_RELATIVE_DELTA = 0.15f
        private const val LUX_SMOOTHING_RETAIN = 0.7f
        private const val MAX_MAPPED_LUX = 500f
        private const val BRIGHTNESS_STEP_THRESHOLD_PERCENT = 5
        private const val BRIGHTNESS_PREFERENCES_NAME = "light_monitor_brightness"
        private const val KEY_ORIGINAL_BRIGHTNESS_MODE = "original_brightness_mode"
        private const val UNKNOWN_BRIGHTNESS_MODE = -1

        fun start(context: Context) {
            ForegroundServiceController.start(
                context = context,
                intent = Intent(context, LightMonitorService::class.java),
                eligibilityCheck = ForegroundServiceStartEligibility::canStartFromForegroundProcess,
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LightMonitorService::class.java))
        }
    }
}
