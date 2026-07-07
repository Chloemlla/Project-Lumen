package com.projectlumen.app.core.services

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.projectlumen.app.ProjectLumenApplication
import com.projectlumen.app.core.enums.ActiveEngine
import com.projectlumen.app.core.proximity.ProximityCameraForegroundEligibility
import java.util.concurrent.TimeUnit

class ShizukuResilienceWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val app = applicationContext as ProjectLumenApplication
        val settings = app.settingsRepository().get()
            ?: return Result.success()
        val shouldRun = settings.shizukuAdvancedModeEnabled &&
            (settings.shizukuServiceRecoveryEnabled || settings.shizukuNativeEyeProtectionEnabled)
        if (!shouldRun) {
            cancel(applicationContext)
            return Result.success()
        }
        if (!app.shizuku.isReady()) {
            enqueue(
                context = applicationContext,
                delayMinutes = if (settings.shizukuNativeEyeProtectionEnabled) 1L else 15L,
            )
            return Result.success()
        }

        if (settings.shizukuNativeEyeProtectionEnabled) {
            app.shizuku.applyNativeEyeProtection(settings, smooth = false)
        }

        if (settings.shizukuServiceRecoveryEnabled) {
            val runtime = app.runtimeRepository().get()
            if (settings.keepAliveEnabled && runtime != null && runtime.activeEngine != ActiveEngine.IDLE.name) {
                app.startTimerService()
                app.notifications.syncRuntimeAlarms(settings, runtime)
                if (settings.notificationEnabled) {
                    app.notifications.showOngoingStatus(runtime)
                }
            }
            if (
                (settings.proximityMonitoringEnabled || settings.blinkMonitoringEnabled) &&
                ProximityCameraForegroundEligibility.canStartCameraForegroundService(applicationContext)
            ) {
                app.scheduleProximityMonitoring()
            }
            if (settings.ambientLightMonitoringEnabled || settings.autoBrightnessEnabled) {
                app.startLightMonitoring()
            }
        }
        enqueue(applicationContext)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK = "project-lumen-shizuku-resilience"

        fun enqueue(context: Context, delayMinutes: Long = 15L) {
            val request = OneTimeWorkRequestBuilder<ShizukuResilienceWorker>()
                .setInitialDelay(delayMinutes.coerceAtLeast(0L), TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK)
        }
    }
}
