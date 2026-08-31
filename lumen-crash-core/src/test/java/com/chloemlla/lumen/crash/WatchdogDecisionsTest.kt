package com.chloemlla.lumen.crash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchdogDecisionsTest {
    @Test
    fun startupDeadlineIsNotExpiredBeforeTheTimeout() {
        assertFalse(
            WatchdogDecisions.startupDeadlineExpired(
                startupPending = true,
                nowMillis = 14_999L,
                startedAtMillis = 0L,
                timeoutMillis = 15_000L,
            ),
        )
    }

    @Test
    fun startupDeadlineExpiresOnTheTimeout() {
        assertTrue(
            WatchdogDecisions.startupDeadlineExpired(
                startupPending = true,
                nowMillis = 15_000L,
                startedAtMillis = 0L,
                timeoutMillis = 15_000L,
            ),
        )
    }

    @Test
    fun completedStartupNeverExpires() {
        assertFalse(
            WatchdogDecisions.startupDeadlineExpired(
                startupPending = false,
                nowMillis = 600_000L,
                startedAtMillis = 0L,
                timeoutMillis = 15_000L,
            ),
        )
    }

    @Test
    fun aResponsiveMainThreadClearsTheFreezeCandidate() {
        assertEquals(
            WatchdogFreezeDecision.RESPONSIVE,
            WatchdogDecisions.freezeDecision(
                heartbeatAgeMillis = 4_999L,
                timeoutMillis = 5_000L,
                alreadyCandidate = true,
            ),
        )
    }

    @Test
    fun firstSilentCheckOnlyArmsTheCandidate() {
        assertEquals(
            WatchdogFreezeDecision.CANDIDATE,
            WatchdogDecisions.freezeDecision(
                heartbeatAgeMillis = 5_000L,
                timeoutMillis = 5_000L,
                alreadyCandidate = false,
            ),
        )
    }

    @Test
    fun twoConsecutiveSilentChecksReport() {
        assertEquals(
            WatchdogFreezeDecision.REPORT,
            WatchdogDecisions.freezeDecision(
                heartbeatAgeMillis = 6_000L,
                timeoutMillis = 5_000L,
                alreadyCandidate = true,
            ),
        )
    }
}
