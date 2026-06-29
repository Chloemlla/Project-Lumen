package com.projectlumen.app.core.proximity

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
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

class ProximityDetectionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as ProjectLumenApplication
        ServiceCompat.startForeground(
            this,
            NotificationIds.PROXIMITY_FOREGROUND,
            app.notifications.buildProximityForegroundNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            } else {
                0
            },
        )
        val calibrate = intent?.getBooleanExtra(EXTRA_CALIBRATE, false) == true
        scope.launch {
            runCatching { runDetection(app, calibrate) }
                .onFailure { clearActiveState(app) }
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun runDetection(app: ProjectLumenApplication, calibrate: Boolean) {
        val db = app.database
        val settings = db.appSettingsDao().get() ?: return
        if (!calibrate && !settings.proximityMonitoringEnabled) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return

        val now = System.currentTimeMillis()
        db.runtimeStateDao().get()?.let {
            db.runtimeStateDao().upsert(it.copy(proximityMonitoringActive = true, updatedAt = now))
        }

        val sample = ProximityCameraSampler(this).captureFaceDistance()
        val latestSettings = if (calibrate && sample != null && sample.eyeDistancePx > 0f) {
            val updated = settings.copy(
                proximityBaselineEyeDistancePx = sample.eyeDistancePx,
                updatedAt = System.currentTimeMillis(),
            )
            db.appSettingsDao().upsert(updated)
            updated
        } else {
            settings
        }

        val ratioPercent = if (sample != null && latestSettings.proximityBaselineEyeDistancePx > 0f && sample.eyeDistancePx > 0f) {
            ((sample.eyeDistancePx / latestSettings.proximityBaselineEyeDistancePx) * 100f).roundToInt()
        } else {
            sample?.faceWidthPercent ?: 0
        }
        val tooClose = sample != null && (
            if (latestSettings.proximityBaselineEyeDistancePx > 0f && sample.eyeDistancePx > 0f) {
                sample.eyeDistancePx > latestSettings.proximityBaselineEyeDistancePx *
                    (latestSettings.proximityDistanceMultiplierPercent / 100f)
            } else {
                sample.faceWidthPercent >= latestSettings.proximityFaceThresholdPercent
            }
        )

        val runtime = db.runtimeStateDao().get()
        val lastWarningAt = runtime?.proximityLastWarningAt ?: 0L
        val shouldWarn = tooClose &&
            now - lastWarningAt >= latestSettings.proximityAlertCooldownSeconds * 1000L
        if (tooClose) {
            incrementEyeStats(app, now) {
                it.copy(
                    proximityWarningCount = it.proximityWarningCount + if (shouldWarn) 1 else 0,
                    proximityCloseSeconds = it.proximityCloseSeconds + latestSettings.proximityCaptureSeconds.coerceAtLeast(1),
                )
            }
        }
        if (shouldWarn) app.notifications.showProximityWarning(ratioPercent)

        db.runtimeStateDao().get()?.let {
            db.runtimeStateDao().upsert(
                it.copy(
                    proximityMonitoringActive = false,
                    proximityTooClose = tooClose,
                    proximityLastFaceAt = if (sample != null) now else it.proximityLastFaceAt,
                    proximityCloseStartedAt = if (tooClose && !it.proximityTooClose) now else if (tooClose) it.proximityCloseStartedAt else 0L,
                    proximityCloseTickAt = if (tooClose) now else 0L,
                    proximityLastWarningAt = if (shouldWarn) now else it.proximityLastWarningAt,
                    proximityLastRatioPercent = ratioPercent,
                    updatedAt = now,
                ),
            )
        }
    }

    private suspend fun incrementEyeStats(
        app: ProjectLumenApplication,
        nowMillis: Long,
        transform: (DailyEyeStatsEntity) -> DailyEyeStatsEntity,
    ) {
        if (app.database.appSettingsDao().get()?.statsEnabled == false) return
        val date = todayKey(nowMillis)
        val dao = app.database.dailyEyeStatsDao()
        val current = dao.get(date) ?: DailyEyeStatsEntity(statDate = date)
        dao.upsert(transform(current).copy(updatedAt = nowMillis))
    }

    private suspend fun clearActiveState(app: ProjectLumenApplication) {
        val now = System.currentTimeMillis()
        app.database.runtimeStateDao().get()?.let {
            app.database.runtimeStateDao().upsert(
                it.copy(
                    proximityMonitoringActive = false,
                    proximityTooClose = false,
                    updatedAt = now,
                ),
            )
        }
    }

    companion object {
        private const val EXTRA_CALIBRATE = "calibrate"

        fun start(context: Context, calibrate: Boolean) {
            val intent = Intent(context, ProximityDetectionService::class.java)
                .putExtra(EXTRA_CALIBRATE, calibrate)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
