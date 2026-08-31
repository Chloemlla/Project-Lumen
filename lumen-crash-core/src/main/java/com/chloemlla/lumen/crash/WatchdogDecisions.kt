package com.chloemlla.lumen.crash

/** Outcome of one freeze check. */
internal enum class WatchdogFreezeDecision {
    /** The main thread processed a heartbeat inside the timeout. */
    RESPONSIVE,

    /** First silent check: grant one grace interval before reporting. */
    CANDIDATE,

    /** Silent for two consecutive checks. */
    REPORT,
}

/**
 * Timing rules of [LumenCrashWatchdog].
 *
 * The loop owns the clock and the atomics; these functions own the decisions. That split exists
 * because the decisions are the part that historically produced false positives, and the only
 * part a plain JVM test can reach (the watchdog itself needs a main looper).
 */
internal object WatchdogDecisions {
    fun startupDeadlineExpired(
        startupPending: Boolean,
        nowMillis: Long,
        startedAtMillis: Long,
        timeoutMillis: Long,
    ): Boolean = startupPending && nowMillis - startedAtMillis >= timeoutMillis

    fun freezeDecision(
        heartbeatAgeMillis: Long,
        timeoutMillis: Long,
        alreadyCandidate: Boolean,
    ): WatchdogFreezeDecision = when {
        heartbeatAgeMillis < timeoutMillis -> WatchdogFreezeDecision.RESPONSIVE
        !alreadyCandidate -> WatchdogFreezeDecision.CANDIDATE
        else -> WatchdogFreezeDecision.REPORT
    }
}
