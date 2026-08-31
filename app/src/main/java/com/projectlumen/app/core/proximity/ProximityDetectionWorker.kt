package com.projectlumen.app.core.proximity

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.chloemlla.lumen.crash.CrashBreadcrumbs
import com.projectlumen.app.ProjectLumenApplication
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

class ProximityDetectionWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val calibrate = inputData.getBoolean(KEY_CALIBRATE, false)
        val eventTriggered = inputData.getBoolean(KEY_EVENT_TRIGGER, false)
        val app = applicationContext as ProjectLumenApplication
        val settingsResult = runCatching { app.settingsRepository().get() }
        val settings = settingsResult.getOrNull()
        settingsResult.exceptionOrNull()?.let { throwable ->
            if (throwable is CancellationException) throw throwable
            app.recordHandledFailure(throwable)
        }
        val monitoringEnabled = settings?.proximityMonitoringEnabled == true || settings?.blinkMonitoringEnabled == true
        val timeTriggerAllowed = settings?.developerModeEnabled != true || settings.developerTimeTriggerEnabled
        val intervalSeconds = settings?.proximityIntervalSeconds() ?: FALLBACK_RETRY_DELAY_SECONDS
        // The periodic chain only survives if every exit path schedules the next tick.
        val keepChainAlive = !calibrate &&
            (settingsResult.isFailure || (settings != null && monitoringEnabled && timeTriggerAllowed))
        var nextDelaySeconds = if (settingsResult.isFailure) backoffSeconds(intervalSeconds) else intervalSeconds
        try {
            if (
                (calibrate || monitoringEnabled) &&
                !ProximityCameraForegroundEligibility.canStartCameraForegroundService(applicationContext)
            ) {
                nextDelaySeconds = backoffSeconds(intervalSeconds)
                runCatching {
                    CrashBreadcrumbs.record(
                        "proximity worker: camera foreground start unavailable, next tick in ${nextDelaySeconds}s",
                    )
                }
                return Result.success()
            }
            if (!calibrate && settings != null && monitoringEnabled && app.shizuku.shouldDeferSampling(settings)) {
                nextDelaySeconds = intervalSeconds.coerceAtMost(MAX_BACKOFF_SECONDS)
                return Result.success()
            }
            val gateAllowed = settings == null || calibrate || ProximityTriggerGate(applicationContext).canRun(settings)
            if (calibrate || (monitoringEnabled && timeTriggerAllowed && gateAllowed)) {
                ProximityDetectionService.start(applicationContext, calibrate)
            }
            return Result.success()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            app.recordHandledFailure(throwable)
            nextDelaySeconds = backoffSeconds(intervalSeconds)
            return Result.success()
        } finally {
            if (keepChainAlive && !isStopped) {
                enqueueNext(
                    context = applicationContext,
                    delaySeconds = nextDelaySeconds,
                    // An unlock-triggered run must not reset the pending periodic tick.
                    keepExisting = eventTriggered,
                )
            }
        }
    }

    companion object {
        private const val UNIQUE_SAMPLE_WORK = "project-lumen-proximity-sample"
        private const val UNIQUE_CALIBRATION_WORK = "project-lumen-proximity-calibration"
        private const val UNIQUE_EVENT_WORK = "project-lumen-proximity-event"
        private const val KEY_CALIBRATE = "calibrate"
        private const val KEY_EVENT_TRIGGER = "event_trigger"
        private const val FALLBACK_RETRY_DELAY_SECONDS = 120
        private const val MIN_BACKOFF_SECONDS = 60
        private const val MAX_BACKOFF_SECONDS = 120

        fun enqueueNext(
            context: Context,
            delaySeconds: Int? = null,
            delayMinutes: Int = 0,
            keepExisting: Boolean = false,
        ) {
            val delay = delaySeconds ?: delayMinutes.coerceAtLeast(0) * 60
            val request = OneTimeWorkRequestBuilder<ProximityDetectionWorker>()
                .setInitialDelay(delay.coerceAtLeast(0).toLong(), TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_SAMPLE_WORK,
                if (keepExisting) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        fun enqueueEventSample(context: Context) {
            val request = OneTimeWorkRequestBuilder<ProximityDetectionWorker>()
                .setInputData(workDataOf(KEY_EVENT_TRIGGER to true))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_EVENT_WORK,
                ExistingWorkPolicy.KEEP,
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
            val workManager = WorkManager.getInstance(context)
            workManager.cancelUniqueWork(UNIQUE_SAMPLE_WORK)
            workManager.cancelUniqueWork(UNIQUE_EVENT_WORK)
            workManager.cancelUniqueWork(UNIQUE_CALIBRATION_WORK)
        }

        private fun backoffSeconds(intervalSeconds: Int): Int =
            intervalSeconds.coerceIn(MIN_BACKOFF_SECONDS, MAX_BACKOFF_SECONDS)

        private fun com.projectlumen.app.core.database.entities.AppSettingsEntity.proximityIntervalSeconds(): Int {
            return if (developerModeEnabled) {
                developerTickIntervalSeconds.coerceIn(10, 30 * 60)
            } else {
                proximityCheckIntervalMinutes.coerceAtLeast(1) * 60
            }
        }
    }
}
