package com.projectlumen.app.core.debug

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.projectlumen.app.ProjectLumenApplication
import com.projectlumen.app.R
import com.projectlumen.app.core.constants.NotificationIds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.sqrt

class DeveloperDebugOverlayService : Service(), SensorEventListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var sensorManager: SensorManager
    private var overlayView: LinearLayout? = null
    private var statsText: TextView? = null
    private var sensorText: TextView? = null
    private var privacyText: TextView? = null
    private var previewImage: ImageView? = null
    private var sensorsRegistered = false
    private var overlayTicking = false
    private var lastRuntimeWriteAt = 0L
    private var lastLux = 0f
    private var lastPitch = 0f
    private var lastRoll = 0f
    private var lastYaw = 0f
    private var lastAccelerationMagnitude = 0f

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(SensorManager::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as ProjectLumenApplication
        recordServiceStart(app, flags)
        ServiceCompat.startForeground(
            this,
            NotificationIds.DEVELOPER_DEBUG_FOREGROUND,
            app.notifications.buildDeveloperDebugForegroundNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )
        if (intent?.action == ACTION_SIMULATE_LOW_MEMORY) {
            simulateLowMemory(app)
            return START_STICKY
        }
        registerSensors()
        if (!overlayTicking) tickOverlay()
        return START_STICKY
    }

    override fun onDestroy() {
        recordServiceStop()
        overlayTicking = false
        handler.removeCallbacksAndMessages(null)
        removeOverlay()
        sensorManager.unregisterListener(this)
        DeveloperDebugFrameStore.clear()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        val app = application as? ProjectLumenApplication
        if (app != null) {
            scope.launch {
                val now = System.currentTimeMillis()
                app.database.runtimeStateDao().get()?.let {
                    app.database.runtimeStateDao().upsert(
                        it.copy(
                            foregroundServiceLastTaskRemovedAt = now,
                            updatedAt = now,
                        ),
                    )
                }
            }
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onTrimMemory(level: Int) {
        if (level >= TRIM_MEMORY_RUNNING_CRITICAL) {
            DeveloperDebugFrameStore.clear()
        }
        super.onTrimMemory(level)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_LIGHT -> lastLux = event.values.firstOrNull() ?: lastLux
            Sensor.TYPE_ACCELEROMETER -> handleAccelerometer(event.values)
            Sensor.TYPE_ROTATION_VECTOR -> handleRotationVector(event.values)
        }
        val now = System.currentTimeMillis()
        if (now - lastRuntimeWriteAt >= 1_000L) {
            lastRuntimeWriteAt = now
            writeSensorRuntime(now)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun registerSensors() {
        if (sensorsRegistered) return
        listOf(
            Sensor.TYPE_LIGHT,
            Sensor.TYPE_ACCELEROMETER,
            Sensor.TYPE_ROTATION_VECTOR,
        ).mapNotNull(sensorManager::getDefaultSensor).forEach { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
        sensorsRegistered = true
    }

    private fun tickOverlay() {
        overlayTicking = true
        scope.launch {
            val settings = (application as ProjectLumenApplication).database.appSettingsDao().get()
            handler.post {
                if (settings?.developerDebugOverlayEnabled == true && Settings.canDrawOverlays(this@DeveloperDebugOverlayService)) {
                    ensureOverlay()
                    renderOverlay(settings.developerDebugPreviewEnabled)
                } else {
                    removeOverlay()
                }
            }
        }
        if (overlayTicking) {
            handler.postDelayed(::tickOverlay, 750L)
        }
    }

    private fun ensureOverlay() {
        if (overlayView != null) return
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 16, 18, 16)
            setBackgroundColor(Color.argb(218, 12, 18, 24))
        }
        statsText = debugText(size = 12f).also(container::addView)
        sensorText = debugText(size = 12f).also(container::addView)
        previewImage = ImageView(this).apply {
            adjustViewBounds = true
            maxWidth = dp(220)
            maxHeight = dp(160)
            visibility = View.GONE
        }.also(container::addView)
        privacyText = debugText(size = 11f).apply {
            setTypeface(typeface, Typeface.BOLD)
            text = getString(R.string.developer_privacy_warning)
        }.also(container::addView)

        val params = WindowManager.LayoutParams(
            dp(300),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(12)
            y = dp(72)
        }
        getSystemService(WindowManager::class.java).addView(container, params)
        overlayView = container
    }

    private fun renderOverlay(showPreview: Boolean) {
        val frame = DeveloperDebugFrameStore.latest()
        val sample = frame?.sample
        statsText?.text = buildString {
            append("FPS: ")
            append(frame?.fps?.let { "%.1f".format(it) } ?: "0.0")
            append("  AI: ")
            append(sample?.inferenceMillis ?: 0L)
            append("ms  Camera: ")
            append(sample?.cameraLatencyMillis ?: 0L)
            append("ms\nCurrent Width: ")
            append(sample?.faceWidthPx ?: 0)
            append(" px  Eye: ")
            append(sample?.eyeDistancePx?.roundToInt() ?: 0)
            append(" px")
        }
        sensorText?.text = "Lux: ${"%.1f".format(lastLux)}  Pitch/Roll/Yaw: " +
            "${lastPitch.roundToInt()}/${lastRoll.roundToInt()}/${lastYaw.roundToInt()} deg"
        previewImage?.visibility = if (showPreview && frame?.thumbnail != null) View.VISIBLE else View.GONE
        if (showPreview) {
            previewImage?.setImageBitmap(frame?.thumbnail)
        } else {
            previewImage?.setImageDrawable(null)
        }
        privacyText?.visibility = if (showPreview) View.VISIBLE else View.GONE
    }

    private fun removeOverlay() {
        val view = overlayView ?: return
        overlayView = null
        runCatching { getSystemService(WindowManager::class.java).removeView(view) }
        statsText = null
        sensorText = null
        privacyText = null
        previewImage = null
    }

    private fun debugText(size: Float): TextView {
        return TextView(this).apply {
            textSize = size
            setTextColor(Color.WHITE)
            includeFontPadding = false
            setPadding(0, 0, 0, 8)
        }
    }

    private fun handleAccelerometer(values: FloatArray) {
        val x = values.getOrNull(0) ?: 0f
        val y = values.getOrNull(1) ?: 0f
        val z = values.getOrNull(2) ?: 0f
        lastAccelerationMagnitude = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
        lastPitch = Math.toDegrees(kotlin.math.atan2((-x).toDouble(), sqrt((y * y + z * z).toDouble()))).toFloat()
        lastRoll = Math.toDegrees(kotlin.math.atan2(y.toDouble(), z.toDouble())).toFloat()
    }

    private fun handleRotationVector(values: FloatArray) {
        val rotationMatrix = FloatArray(9)
        val orientation = FloatArray(3)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, values)
        SensorManager.getOrientation(rotationMatrix, orientation)
        lastYaw = Math.toDegrees(orientation[0].toDouble()).toFloat()
        lastPitch = Math.toDegrees(orientation[1].toDouble()).toFloat()
        lastRoll = Math.toDegrees(orientation[2].toDouble()).toFloat()
    }

    private fun writeSensorRuntime(nowMillis: Long) {
        val app = application as ProjectLumenApplication
        scope.launch {
            app.database.runtimeStateDao().get()?.let {
                app.database.runtimeStateDao().upsert(
                    it.copy(
                        ambientLastLux = lastLux,
                        sensorPitchDegrees = lastPitch,
                        sensorRollDegrees = lastRoll,
                        sensorYawDegrees = lastYaw,
                        sensorLastAccelerationMagnitude = lastAccelerationMagnitude,
                        updatedAt = nowMillis,
                    ),
                )
            }
        }
    }

    private fun simulateLowMemory(app: ProjectLumenApplication) {
        DeveloperDebugFrameStore.clear()
        onTrimMemory(TRIM_MEMORY_RUNNING_CRITICAL)
        scope.launch {
            val now = System.currentTimeMillis()
            app.database.runtimeStateDao().get()?.let {
                app.database.runtimeStateDao().upsert(
                    it.copy(
                        developerLastLowMemorySimulatedAt = now,
                        updatedAt = now,
                    ),
                )
            }
        }
    }

    private fun recordServiceStart(app: ProjectLumenApplication, flags: Int) {
        scope.launch {
            val now = System.currentTimeMillis()
            val restarted = flags and (START_FLAG_REDELIVERY or START_FLAG_RETRY) != 0
            app.database.runtimeStateDao().get()?.let {
                app.database.runtimeStateDao().upsert(
                    it.copy(
                        foregroundServiceStartedAt = now,
                        foregroundServiceStoppedAt = 0L,
                        foregroundServiceLastStickyRestartAt = if (restarted) now else it.foregroundServiceLastStickyRestartAt,
                        updatedAt = now,
                    ),
                )
            }
        }
    }

    private fun recordServiceStop() {
        val app = application as? ProjectLumenApplication ?: return
        CoroutineScope(Dispatchers.IO).launch {
            val now = System.currentTimeMillis()
            app.database.runtimeStateDao().get()?.let {
                app.database.runtimeStateDao().upsert(
                    it.copy(
                        foregroundServiceStoppedAt = now,
                        updatedAt = now,
                    ),
                )
            }
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).roundToInt()
    }

    companion object {
        private const val ACTION_SIMULATE_LOW_MEMORY = "com.projectlumen.app.DEVELOPER_SIMULATE_LOW_MEMORY"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, DeveloperDebugOverlayService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DeveloperDebugOverlayService::class.java))
        }

        fun simulateLowMemory(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, DeveloperDebugOverlayService::class.java).setAction(ACTION_SIMULATE_LOW_MEMORY),
            )
        }
    }
}
