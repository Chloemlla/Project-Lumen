package com.projectlumen.app.core.services

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.projectlumen.app.ProjectLumenApplication
import com.projectlumen.app.core.enums.ActiveEngine
import java.util.concurrent.TimeUnit

class TimerReconciliationWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val app = applicationContext as ProjectLumenApplication
        val settings = app.settingsRepository().get()
            ?: return Result.success()
        val runtime = app.database.runtimeStateDao().get()
            ?: return Result.success()
        if (settings.keepAliveEnabled && runtime.activeEngine != ActiveEngine.IDLE.name) {
            app.startTimerService()
            app.notifications.syncRuntimeAlarms(settings, runtime)
            enqueue(applicationContext)
        }
        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK = "project-lumen-timer-reconciliation"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<TimerReconciliationWorker>()
                .setInitialDelay(15L, TimeUnit.MINUTES)
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
