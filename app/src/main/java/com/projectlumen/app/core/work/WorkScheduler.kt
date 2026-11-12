package com.projectlumen.app.core.work

import android.content.Context
import android.os.UserManager
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.chloemlla.lumen.crash.CrashBreadcrumbs
import com.projectlumen.app.ProjectLumenApplication
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The single choke point for every WorkManager touch in the app.
 *
 * WorkManager is normally initialized by `androidx.startup.InitializationProvider` before
 * `Application.onCreate`, but that initializer is not `directBootAware`: a process the platform
 * creates before the user's first unlock after a reboot (this app's `BootReceiver` is
 * `directBootAware` and takes `LOCKED_BOOT_COMPLETED`) never runs it, and the process keeps that
 * state for its whole lifetime — including after the unlock, when the boot-restore pass finally
 * reaches credential-encrypted storage and schedules work. `WorkManager.getInstance` then throws
 * `IllegalStateException("WorkManager is not initialized properly …")` into whichever coroutine
 * happened to schedule first (crash report `0d56b7959042`: `scheduleProximityMonitoring` called
 * from `BootReceiver.restoreScheduledWork`). Nothing was actually scheduled, so proximity/blink
 * monitoring silently stayed dead until the next successful enqueue.
 *
 * Three layers keep that from recurring:
 *  - [ProjectLumenApplication] implements `Configuration.Provider`, so a skipped eager initializer
 *    is repaired by the first `getInstance` call instead of failing (WorkManager's own documented
 *    remedy for "the initializer never ran");
 *  - this helper refuses to touch WorkManager while credential-encrypted storage is still locked —
 *    its database lives there, and a half-initialized WorkManager is worse than none;
 *  - every failure is contained here: a WorkManager problem is logged, breadcrumbed and reported
 *    once per process, and the caller keeps running. Scheduling callers are all fire-and-forget
 *    (the next launch, `BOOT_COMPLETED`, or the worker chain itself re-enqueues), so a skipped
 *    enqueue self-heals instead of taking the caller's coroutine down with it.
 */
internal object WorkScheduler {
    private const val TAG = "WorkScheduler"

    /** Reset per process; a fresh process gets one locked-state breadcrumb and one failure report. */
    private val lockedSkipReported = AtomicBoolean(false)
    private val failureReported = AtomicBoolean(false)

    /**
     * Enqueues [request] as the unique work named [uniqueName]. Returns false when WorkManager is
     * unavailable right now; the caller is expected to carry on.
     */
    fun enqueue(
        context: Context,
        uniqueName: String,
        policy: ExistingWorkPolicy,
        request: OneTimeWorkRequest,
    ): Boolean {
        val workManager = workManager(context) ?: return false
        return runCatching {
            workManager.enqueueUniqueWork(uniqueName, policy, request)
            true
        }.getOrElse { throwable ->
            report(context, "enqueue $uniqueName", throwable)
            false
        }
    }

    /** Cancels the unique work named [uniqueNames]. Returns false when WorkManager is unavailable. */
    fun cancel(context: Context, vararg uniqueNames: String): Boolean {
        val workManager = workManager(context) ?: return false
        return runCatching {
            uniqueNames.forEach { uniqueName -> workManager.cancelUniqueWork(uniqueName) }
            true
        }.getOrElse { throwable ->
            report(context, "cancel ${uniqueNames.joinToString(",")}", throwable)
            false
        }
    }

    /**
     * True when credential-encrypted storage (Room, MMKV, WorkManager's database) can be opened.
     * Always true outside the direct-boot window, which is every state except the one between a
     * reboot and the user's first unlock.
     */
    fun isStorageUnlocked(context: Context): Boolean {
        return runCatching {
            context.getSystemService(UserManager::class.java)?.isUserUnlocked ?: true
        }.getOrDefault(true)
    }

    private fun workManager(context: Context): WorkManager? {
        val appContext = context.applicationContext ?: context
        if (!isStorageUnlocked(appContext)) {
            // Expected platform state right after a reboot, not a fault: report it at most once per
            // process and never as a failure, or every boot floods crash telemetry.
            if (lockedSkipReported.compareAndSet(false, true)) {
                Log.i(TAG, "WorkManager skipped: user storage is still locked")
                recordBreadcrumb("WorkManager skipped: user storage locked")
            }
            return null
        }
        return runCatching { WorkManager.getInstance(appContext) }
            .getOrElse { throwable ->
                report(appContext, "getInstance", throwable)
                null
            }
    }

    private fun report(context: Context, action: String, throwable: Throwable) {
        Log.w(TAG, "WorkManager unavailable for $action", throwable)
        recordBreadcrumb("WorkManager unavailable: $action (${throwable::class.java.simpleName})")
        // One report per process: the proximity chain re-enqueues on every tick, and an exception
        // storm would bury the first, informative report.
        if (failureReported.compareAndSet(false, true)) {
            runCatching {
                (context.applicationContext as? ProjectLumenApplication)?.recordHandledFailure(throwable)
            }
        }
    }

    private fun recordBreadcrumb(event: String) {
        runCatching { CrashBreadcrumbs.record(event) }
    }
}
