package com.projectlumen.app.core.proximity

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.projectlumen.app.ProjectLumenApplication
import com.projectlumen.app.core.repositories.SettingsRepository
import java.util.concurrent.TimeUnit

class ProximityDetectionWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val calibrate = inputData.getBoolean(KEY_CALIBRATE, false)
        val app = applicationContext as ProjectLumenApplication
        val settings = SettingsRepository(app.database.appSettingsDao(), app.eyeCarePreferences).get()
        val monitoringEnabled = settings?.proximityMonitoringEnabled == true || settings?.blinkMonitoringEnabled == true
        val timeTriggerAllowed = settings?.developerModeEnabled != true || settings.developerTimeTriggerEnabled
        val gateAllowed = settings == null || calibrate || ProximityTriggerGate(applicationContext).canRun(settings)
        if (calibrate || (monitoringEnabled && timeTriggerAllowed && gateAllowed)) {
            ProximityDetectionService.start(applicationContext, calibrate)
        }
        if (!calibrate && settings != null && monitoringEnabled && timeTriggerAllowed) {
            enqueueNext(applicationContext, delaySeconds = settings.proximityIntervalSeconds())
        }
        return Result.success()
    }

    companion object {
        private const val UNIQUE_SAMPLE_WORK = "project-lumen-proximity-sample"
        private const val UNIQUE_CALIBRATION_WORK = "project-lumen-proximity-calibration"
        private const val KEY_CALIBRATE = "calibrate"

        fun enqueueNext(context: Context, delaySeconds: Int? = null, delayMinutes: Int = 0) {
            val delay = delaySeconds ?: delayMinutes.coerceAtLeast(0) * 60
            val request = OneTimeWorkRequestBuilder<ProximityDetectionWorker>()
                .setInitialDelay(delay.coerceAtLeast(0).toLong(), TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_SAMPLE_WORK,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        fun enqueueCalibration(context: Context) {
            val request = OneTimeWorkRequestBuilder<ProximityDetectionWorker>()
                .setInputData(workDataOf(KEY_CALIBRATE to true))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_CALIBRATION_WORK,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_SAMPLE_WORK)
        }

        private fun com.projectlumen.app.core.database.entities.AppSettingsEntity.proximityIntervalSeconds(): Int {
            return if (developerModeEnabled) {
                developerTickIntervalSeconds.coerceIn(10, 30 * 60)
            } else {
                proximityCheckIntervalMinutes.coerceAtLeast(1) * 60
            }
        }
    }
}
