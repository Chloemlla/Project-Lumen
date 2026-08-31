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
import kotlinx.coroutines.CancellationException

class TimerReconciliationWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val app = applicationContext as ProjectLumenApplication
        return try {
            reconcile(app)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            app.recordHandledFailure(throwable)
            // Retry instead of failing: a failed run would end the self-renewing watchdog chain.
            Result.retry()
        }
    }

    private suspend fun reconcile(app: ProjectLumenApplication): Result {
        val settings = app.settingsRepository().get()
            ?: return Result.success()
        // Advance first: the stored trigger times are in the past whenever the process was killed
        // across a due phase, and re-arming them without advancing schedules nothing at all.
        val runtime = AlarmReceiver.reconcileNow(
            app = app,
            notifications = app.notifications,
            settings = settings,
            nowMillis = System.currentTimeMillis(),
            capStatsDelta = true,
        )
        if (!settings.keepAliveEnabled || runtime.activeEngine == ActiveEngine.IDLE.name) {
            return Result.success()
        }
        app.startTimerService()
        // Plain WorkManager runs are not exempt from the Android 12+ background FGS ban, so the
        // ongoing notification plus the alarms re-armed above are the reliable part of recovery.
        if (settings.notificationEnabled) {
            app.notifications.showOngoingStatus(runtime)
        }
        enqueue(applicationContext)
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
