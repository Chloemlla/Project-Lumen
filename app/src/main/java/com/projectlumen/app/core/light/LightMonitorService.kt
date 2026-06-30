package com.projectlumen.app.core.light

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.projectlumen.app.ProjectLumenApplication
import com.projectlumen.app.core.constants.NotificationIds
import com.projectlumen.app.core.database.entities.DailyEyeStatsEntity
import com.projectlumen.app.core.time.todayKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class LightMonitorService : Service(), SensorEventListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var sensorManager: SensorManager
    private var lastHandledAt: Long = 0L

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(SensorManager::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as ProjectLumenApplication
        ServiceCompat.startForeground(
            this,
            NotificationIds.LOW_LIGHT_FOREGROUND,
            app.notifications.buildLightMonitorForegroundNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )
        val lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        if (lightSensor == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
        return START_STICKY
    }

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_LIGHT) return
        val now = System.currentTimeMillis()
        if (now - lastHandledAt < 2_000L) return
        lastHandledAt = now
        val lux = event.values.firstOrNull() ?: return
        scope.launch { handleLux(lux, now) }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private suspend fun handleLux(lux: Float, nowMillis: Long) {
        val app = application as ProjectLumenApplication
        val db = app.database
        val settings = db.appSettingsDao().get() ?: return
        if (!settings.ambientLightMonitoringEnabled && !settings.autoBrightnessEnabled) {
            stopSelf()
            return
        }
        val tooDark = lux < settings.ambientLightLowLuxThreshold
        val runtime = db.runtimeStateDao().get()
        val shouldWarn = settings.ambientLightMonitoringEnabled &&
            tooDark &&
            nowMillis - (runtime?.ambientLastWarningAt ?: 0L) >= LOW_LIGHT_COOLDOWN_MILLIS
        if (settings.autoBrightnessEnabled) {
            applyBrightness(lux, settings.autoBrightnessMinPercent, settings.autoBrightnessMaxPercent)
        }
        if (shouldWarn) {
            app.notifications.showLowLightWarning(lux)
            incrementLowLightStats(app, nowMillis)
        }
        runtime?.let {
            db.runtimeStateDao().upsert(
                it.copy(
                    ambientLastLux = lux,
                    ambientTooDark = tooDark,
                    ambientLastWarningAt = if (shouldWarn) nowMillis else it.ambientLastWarningAt,
                    updatedAt = nowMillis,
                ),
            )
        }
        if (shouldWarn) {
            runCatching { app.telemetry.uploadCurrentSnapshot(force = true) }
        }
    }

    private fun applyBrightness(lux: Float, minPercent: Int, maxPercent: Int) {
        if (!Settings.System.canWrite(this)) return
        val min = minPercent.coerceIn(5, 100)
        val max = maxPercent.coerceIn(min, 100)
        val ratio = (lux.coerceIn(0f, 500f) / 500f)
        val percent = (min + (max - min) * ratio).roundToInt().coerceIn(5, 100)
        val brightness = ((percent / 100f) * 255f).roundToInt().coerceIn(1, 255)
        runCatching {
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, brightness)
        }
    }

    private suspend fun incrementLowLightStats(app: ProjectLumenApplication, nowMillis: Long) {
        if (app.database.appSettingsDao().get()?.statsEnabled == false) return
        val date = todayKey(nowMillis)
        val dao = app.database.dailyEyeStatsDao()
        val current = dao.get(date) ?: DailyEyeStatsEntity(statDate = date)
        dao.upsert(current.copy(lowLightWarningCount = current.lowLightWarningCount + 1, updatedAt = nowMillis))
    }

    companion object {
        private const val LOW_LIGHT_COOLDOWN_MILLIS = 120_000L

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, LightMonitorService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LightMonitorService::class.java))
        }
    }
}
