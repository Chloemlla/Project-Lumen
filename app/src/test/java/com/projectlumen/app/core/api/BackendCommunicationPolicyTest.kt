package com.projectlumen.app.core.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendCommunicationPolicyTest {
    @Test
    fun healthProbeAlwaysRemainsAvailable() {
        val decision = BackendCommunicationPolicy.resolve(
            state = BackendConnectivityState(status = BackendHealthStatus.UNREACHABLE),
            capability = BackendCapability.HEALTH_PROBE,
            nowMillis = NOW_MILLIS,
        )

        assertTrue(decision.visible)
        assertTrue(decision.executable)
        assertEquals(BackendCapabilityReason.HEALTH_PROBE, decision.reason)
    }

    @Test
    fun unknownAndUnreachableStatesHideBackendCapabilities() {
        listOf(BackendHealthStatus.UNKNOWN, BackendHealthStatus.UNREACHABLE).forEach { status ->
            val decision = BackendCommunicationPolicy.resolve(
                state = BackendConnectivityState(status = status),
                capability = BackendCapability.CLOUD_SYNC,
                nowMillis = NOW_MILLIS,
            )

            assertFalse(decision.visible)
            assertFalse(decision.executable)
        }
    }

    @Test
    fun reachableStateAllowsBackendCapabilities() {
        val decision = BackendCommunicationPolicy.resolve(
            state = BackendConnectivityState(status = BackendHealthStatus.REACHABLE),
            capability = BackendCapability.TELEMETRY,
            nowMillis = NOW_MILLIS,
        )

        assertTrue(decision.visible)
        assertTrue(decision.executable)
        assertEquals(BackendCapabilityReason.REACHABLE, decision.reason)
    }

    @Test
    fun checkingStateHonorsOnlyFreshConfirmedReachability() {
        val fresh = BackendCommunicationPolicy.resolve(
            state = BackendConnectivityState(
                status = BackendHealthStatus.CHECKING,
                lastConfirmedStatus = BackendHealthStatus.REACHABLE,
                lastReachableAtMillis = NOW_MILLIS - BackendCommunicationPolicy.RECENT_REACHABLE_TTL_MILLIS,
            ),
            capability = BackendCapability.ACCOUNT_SESSION,
            nowMillis = NOW_MILLIS,
        )
        val stale = BackendCommunicationPolicy.resolve(
            state = BackendConnectivityState(
                status = BackendHealthStatus.CHECKING,
                lastConfirmedStatus = BackendHealthStatus.REACHABLE,
                lastReachableAtMillis = NOW_MILLIS - BackendCommunicationPolicy.RECENT_REACHABLE_TTL_MILLIS - 1L,
            ),
            capability = BackendCapability.ACCOUNT_SESSION,
            nowMillis = NOW_MILLIS,
        )

        assertTrue(fresh.executable)
        assertEquals(BackendCapabilityReason.RECENTLY_REACHABLE, fresh.reason)
        assertFalse(stale.executable)
        assertEquals(BackendCapabilityReason.CHECKING, stale.reason)
    }

    @Test
    fun developerOverrideAllowsRequestsWithoutChangingActualHealth() {
        val state = BackendConnectivityState(
            status = BackendHealthStatus.UNREACHABLE,
            lastConfirmedStatus = BackendHealthStatus.UNREACHABLE,
            developerForceEnabled = true,
        )

        val decision = BackendCommunicationPolicy.resolve(
            state = state,
            capability = BackendCapability.CLOUD_BACKUP,
            nowMillis = NOW_MILLIS,
        )

        assertTrue(decision.visible)
        assertTrue(decision.executable)
        assertTrue(decision.forced)
        assertEquals(BackendCapabilityReason.DEVELOPER_FORCE_ENABLED, decision.reason)
        assertEquals(BackendHealthStatus.UNREACHABLE, state.status)
    }

    @Test
    fun retryBackoffIsBounded() {
        assertEquals(5_000L, BackendRetryPolicy.delayMillis(1))
        assertEquals(30_000L, BackendRetryPolicy.delayMillis(2))
        assertEquals(120_000L, BackendRetryPolicy.delayMillis(3))
        assertEquals(300_000L, BackendRetryPolicy.delayMillis(4))
        assertEquals(300_000L, BackendRetryPolicy.delayMillis(100))
    }

    private companion object {
        const val NOW_MILLIS = 1_000_000L
    }
}
