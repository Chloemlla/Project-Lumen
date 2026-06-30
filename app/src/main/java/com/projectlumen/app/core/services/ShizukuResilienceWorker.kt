package com.projectlumen.app.core.services

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.projectlumen.app.ProjectLumenApplication
import com.projectlumen.app.core.enums.ActiveEngine
import com.projectlumen.app.core.repositories.SettingsRepository
import java.util.concurrent.TimeUnit

class ShizukuResilienceWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val app = applicationContext as ProjectLumenApplication
        val settings = SettingsRepository(app.database.appSettingsDao(), app.eyeCarePreferences).get()
            ?: return Result.success()
        if (!settings.shizukuAdvancedModeEnabled || !settings.shizukuServiceRecoveryEnabled) {
            cancel(applicationContext)
            return Result.success()
        }
        if (!app.shizuku.isReady()) {
            enqueue(applicationContext)
            return Result.success()
        }

        val runtime = app.database.runtimeStateDao().get()
        if (settings.keepAliveEnabled && runtime != null && runtime.activeEngine != ActiveEngine.IDLE.name) {
            app.startTimerService()
            app.notifications.syncRuntimeAlarms(settings, runtime)
            if (settings.notificationEnabled) {
                app.notifications.showOngoingStatus(runtime)
            }
        }
        if (hasCameraPermission(applicationContext) && (settings.proximityMonitoringEnabled || settings.blinkMonitoringEnabled)) {
            app.scheduleProximityMonitoring()
        }
        if (settings.ambientLightMonitoringEnabled || settings.autoBrightnessEnabled) {
            app.startLightMonitoring()
        }
        enqueue(applicationContext)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK = "project-lumen-shizuku-resilience"

        fun enqueue(context: Context, delayMinutes: Long = 15L) {
            val request = OneTimeWorkRequestBuilder<ShizukuResilienceWorker>()
                .setInitialDelay(delayMinutes.coerceAtLeast(1L), TimeUnit.MINUTES)
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

        private fun hasCameraPermission(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        }
    }
}
