package com.chloemlla.lumen.crash

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Background watchdog for main-thread freezes and optional startup hangs.
 *
 * It deliberately lives outside the main looper. A timer or coroutine scheduled on the main
 * thread cannot observe the exact failure this class is meant to detect.
 *
 * Freeze detection is gated on the app having a resumed (foreground) activity. While the
 * process is backgrounded the OS may stop scheduling it entirely (doze / OEM app-freeze /
 * screen off): the main thread simply cannot process heartbeats, so a watchdog that reports in
 * that state produces false positives. The heartbeat baseline is therefore refreshed in the
 * background, and a freeze is only emitted for a foreground main thread that stays silent past
 * the timeout for two consecutive checks. All timing uses [SystemClock.uptimeMillis] so deep
 * sleep is not counted as an unresponsive main thread.
 *
 * Startup detection is gated the same way: only a process the user is waiting on owes a first
 * frame, so a launch that is not user-visible when the timeout expires is retired rather than
 * reported. A resumed activity also completes the startup timer on its own, because it is
 * objective proof that the process can draw; [markStartupComplete] stays available as the more
 * precise host-supplied signal.
 */
internal class LumenCrashWatchdog(
    private val application: Application,
    private val config: LumenCrashConfig,
    private val onReport: (CrashReportKind, Long, String) -> Unit,
) {
    private val mainThread = Looper.getMainLooper().thread
    private val mainHandler = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)
    private val startupComplete = AtomicBoolean(!config.startupHangWatchdogEnabled)
    private val startupReported = AtomicBoolean(false)
    private val freezeReported = AtomicBoolean(false)
    private val freezeCandidate = AtomicBoolean(false)
    private val startedAtMillis = SystemClock.uptimeMillis()
    private val lastHeartbeatAtMillis = AtomicLong(startedAtMillis)

    /** Number of activities currently resumed. > 0 means the app is user-visible. */
    private val resumedActivityCount = AtomicInteger(0)

    @Volatile
    private var worker: Thread? = null

    private val heartbeat = Runnable {
        lastHeartbeatAtMillis.set(SystemClock.uptimeMillis())
        // Allow a later freeze to produce a new report after the app recovers.
        freezeReported.set(false)
    }

    private val activityLifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) {
            resumedActivityCount.incrementAndGet()
            // The process demonstrably draws frames, whether or not the host reports it.
            markStartupComplete()
        }

        override fun onActivityPaused(activity: Activity) {
            resumedActivityCount.updateAndGet { (it - 1).coerceAtLeast(0) }
        }

        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }

    private val isForeground: Boolean
        get() = resumedActivityCount.get() > 0

    fun start() {
        if (!running.compareAndSet(false, true)) return

        application.registerActivityLifecycleCallbacks(activityLifecycleCallbacks)
        mainHandler.post(heartbeat)
        worker = Thread({ runLoop() }, "LumenCrash-Watchdog").apply {
            isDaemon = true
            start()
        }
    }

    fun markStartupComplete() {
        if (!config.startupHangWatchdogEnabled) return
        if (startupComplete.compareAndSet(false, true)) {
            CrashBreadcrumbs.record("Startup watchdog completed")
            mainHandler.post(heartbeat)
        }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        mainHandler.removeCallbacks(heartbeat)
        application.unregisterActivityLifecycleCallbacks(activityLifecycleCallbacks)
        worker?.interrupt()
        worker = null
    }

    private fun runLoop() {
        val checkIntervalMillis = config.anrWatchdogCheckIntervalMillis.coerceAtLeast(100L)
        val anrTimeoutMillis = config.anrWatchdogTimeoutMillis
            .coerceAtLeast(checkIntervalMillis)
        val startupTimeoutMillis = config.startupHangTimeoutMillis.coerceAtLeast(1_000L)

        while (running.get()) {
            // Re-post the heartbeat every tick: a responsive main thread keeps
            // refreshing lastHeartbeatAtMillis, so only a main thread that cannot
            // process messages (a real freeze) lets the age cross the timeout.
            mainHandler.post(heartbeat)
            val now = SystemClock.uptimeMillis()
            val startupPending = config.startupHangWatchdogEnabled && !startupComplete.get()

            if (WatchdogDecisions.startupDeadlineExpired(
                    startupPending = startupPending,
                    nowMillis = now,
                    startedAtMillis = startedAtMillis,
                    timeoutMillis = startupTimeoutMillis,
                ) &&
                startupReported.compareAndSet(false, true)
            ) {
                if (isUserVisibleLaunch()) {
                    emit(CrashReportKind.STARTUP_HANG, now - startedAtMillis)
                } else {
                    // Nobody was waiting for a frame: the process was started for a content
                    // provider, broadcast or job, or the user walked away mid cold start.
                    // Ageing past the timeout says nothing about the host here, so hand the
                    // rest of the process lifetime to the freeze detector instead.
                    startupComplete.set(true)
                    CrashBreadcrumbs.record("Startup watchdog skipped: process not user-visible")
                }
            }

            // A startup report is more useful than a duplicate generic freeze report while the
            // host has not yet reported its first frame.
            if (!startupPending && config.anrWatchdogEnabled && isForeground) {
                val heartbeatAge = now - lastHeartbeatAtMillis.get()
                when (
                    WatchdogDecisions.freezeDecision(
                        heartbeatAgeMillis = heartbeatAge,
                        timeoutMillis = anrTimeoutMillis,
                        alreadyCandidate = freezeCandidate.get(),
                    )
                ) {
                    // The main thread processed a heartbeat during the grace window.
                    WatchdogFreezeDecision.RESPONSIVE -> freezeCandidate.set(false)
                    // First sighting of a silent main thread. Grant one grace interval so a main
                    // thread that was merely suspended by the OS (and resumes a tick behind the
                    // watchdog) can process the queued heartbeat.
                    WatchdogFreezeDecision.CANDIDATE -> freezeCandidate.set(true)
                    WatchdogFreezeDecision.REPORT ->
                        if (freezeReported.compareAndSet(false, true)) {
                            emit(CrashReportKind.FREEZE, heartbeatAge)
                        }
                }
            } else {
                // Background / not user-visible: a silent main thread here is almost always the
                // OS suspending the process (doze / OEM app-freeze / screen off), not a real
                // freeze. Keep the heartbeat baseline fresh so no stale age carries into the
                // next foreground session.
                lastHeartbeatAtMillis.set(now)
                freezeCandidate.set(false)
            }

            try {
                Thread.sleep(checkIntervalMillis)
            } catch (_: InterruptedException) {
                if (running.get()) continue
                return
            }
        }
    }

    /**
     * True when the user is actually waiting on this process to draw. A process started for a
     * content provider, broadcast or job is never expected to render a frame, and neither is a
     * launch the user abandoned, so [ActivityManager.getMyMemoryState] decides the cases where
     * no activity ever reached onResume (an [Application.onCreate] that never returns still
     * hosts the top activity record and reports IMPORTANCE_FOREGROUND).
     */
    private fun isUserVisibleLaunch(): Boolean {
        if (isForeground) return true
        return runCatching {
            val state = ActivityManager.RunningAppProcessInfo()
            ActivityManager.getMyMemoryState(state)
            state.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        }.getOrDefault(false)
    }

    private fun emit(kind: CrashReportKind, durationMillis: Long) {
        val dump = runCatching { CrashThreadDump.capture(mainThread) }
            .getOrDefault("<thread dump failed>")
        runCatching { onReport(kind, durationMillis.coerceAtLeast(0L), dump) }
    }
}
