package com.chloemlla.lumen.crash

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Background watchdog for main-thread freezes and optional startup hangs.
 *
 * It deliberately lives outside the main looper. A timer or coroutine scheduled on the main
 * thread cannot observe the exact failure this class is meant to detect.
 */
internal class LumenCrashWatchdog(
    private val config: LumenCrashConfig,
    private val onReport: (CrashReportKind, Long, String) -> Unit,
) {
    private val mainThread = Looper.getMainLooper().thread
    private val mainHandler = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)
    private val startupComplete = AtomicBoolean(!config.startupHangWatchdogEnabled)
    private val startupReported = AtomicBoolean(false)
    private val freezeReported = AtomicBoolean(false)
    private val startedAtMillis = SystemClock.elapsedRealtime()
    private val lastHeartbeatAtMillis = AtomicLong(startedAtMillis)

    @Volatile
    private var worker: Thread? = null

    private val heartbeat = Runnable {
        lastHeartbeatAtMillis.set(SystemClock.elapsedRealtime())
        // Allow a later freeze to produce a new report after the app recovers.
        freezeReported.set(false)
    }

    fun start() {
        if (!running.compareAndSet(false, true)) return

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
            val now = SystemClock.elapsedRealtime()
            val startupPending = config.startupHangWatchdogEnabled && !startupComplete.get()

            if (startupPending &&
                !startupReported.get() &&
                now - startedAtMillis >= startupTimeoutMillis &&
                startupReported.compareAndSet(false, true)
            ) {
                emit(CrashReportKind.STARTUP_HANG, now - startedAtMillis)
            }

            // A startup report is more useful than a duplicate generic freeze report while the
            // host has not yet reported its first frame.
            if (!startupPending &&
                config.anrWatchdogEnabled &&
                now - lastHeartbeatAtMillis.get() >= anrTimeoutMillis &&
                freezeReported.compareAndSet(false, true)
            ) {
                emit(CrashReportKind.FREEZE, now - lastHeartbeatAtMillis.get())
            }

            try {
                Thread.sleep(checkIntervalMillis)
            } catch (_: InterruptedException) {
                if (running.get()) continue
                return
            }
        }
    }

    private fun emit(kind: CrashReportKind, durationMillis: Long) {
        val dump = runCatching { CrashThreadDump.capture(mainThread) }
            .getOrDefault("<thread dump failed>")
        runCatching { onReport(kind, durationMillis.coerceAtLeast(0L), dump) }
    }
}
