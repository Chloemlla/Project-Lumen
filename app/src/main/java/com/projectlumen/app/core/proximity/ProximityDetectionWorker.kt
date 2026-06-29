package com.projectlumen.app.core.proximity

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.projectlumen.app.ProjectLumenApplication
import java.util.concurrent.TimeUnit

class ProximityDetectionWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val calibrate = inputData.getBoolean(KEY_CALIBRATE, false)
        val app = applicationContext as ProjectLumenApplication
        val settings = app.database.appSettingsDao().get()
        if (calibrate || settings?.proximityMonitoringEnabled == true) {
            ProximityDetectionService.start(applicationContext, calibrate)
        }
        if (!calibrate && settings?.proximityMonitoringEnabled == true) {
            enqueueNext(applicationContext, settings.proximityCheckIntervalMinutes)
        }
        return Result.success()
    }

    companion object {
        private const val UNIQUE_SAMPLE_WORK = "project-lumen-proximity-sample"
        private const val UNIQUE_CALIBRATION_WORK = "project-lumen-proximity-calibration"
        private const val KEY_CALIBRATE = "calibrate"

        fun enqueueNext(context: Context, delayMinutes: Int = 0) {
            val request = OneTimeWorkRequestBuilder<ProximityDetectionWorker>()
                .setInitialDelay(delayMinutes.coerceAtLeast(0).toLong(), TimeUnit.MINUTES)
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
    }
}
