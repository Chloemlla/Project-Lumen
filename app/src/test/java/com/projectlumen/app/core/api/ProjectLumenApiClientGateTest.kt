package com.projectlumen.app.core.api

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectLumenApiClientGateTest {
    @Test
    fun blockedRequestStopsBeforeSigningOrHttpExecution() = runBlocking {
        val httpExecutions = AtomicInteger(0)
        val httpClient = OkHttpClient.Builder()
            .addInterceptor {
                httpExecutions.incrementAndGet()
                error("HTTP must not execute for a blocked backend capability")
            }
            .build()
        val client = ProjectLumenApiClient(
            baseUrl = "https://api.example.test/",
            httpClient = httpClient,
            backendGate = DenyGate,
        )

        val error = runCatching { client.startEmailLogin("test@example.com") }.exceptionOrNull()

        assertTrue(error is BackendCommunicationBlockedException)
        assertEquals(BackendCapability.ACCOUNT_SESSION, (error as BackendCommunicationBlockedException).capability)
        assertEquals(0, httpExecutions.get())
    }

    @Test
    fun endpointFamiliesDeclareTheirCapabilityAtTheFinalBoundary() = runBlocking {
        val gate = RecordingDenyGate()
        val client = ProjectLumenApiClient(
            baseUrl = "https://api.example.test/",
            httpClient = OkHttpClient(),
            backendGate = gate,
        )

        assertBlockedAs(BackendCapability.HEALTH_PROBE, gate) { client.health() }
        assertBlockedAs(BackendCapability.ACCOUNT_SESSION, gate) { client.fetchMe("token") }
        assertBlockedAs(BackendCapability.REMOTE_CONFIG, gate) { client.fetchConfigSync() }
        assertBlockedAs(BackendCapability.RELEASE_DISCOVERY, gate) { client.checkRemoteRelease(1L) }
        assertBlockedAs(BackendCapability.CLOUD_BACKUP, gate) { client.fetchLatestBackup("token") }
        assertBlockedAs(BackendCapability.DEVICE_CONTROL, gate) {
            client.fetchDeviceControlPolicy("token", "device")
        }
    }

    private suspend fun assertBlockedAs(
        expected: BackendCapability,
        gate: RecordingDenyGate,
        request: suspend () -> Unit,
    ) {
        val error = runCatching { request() }.exceptionOrNull()
        assertTrue(error is BackendCommunicationBlockedException)
        assertEquals(expected, gate.lastCapability)
        assertEquals(expected, (error as BackendCommunicationBlockedException).capability)
    }

    private object DenyGate : BackendCapabilityGate {
        override fun decision(capability: BackendCapability) = deniedDecision(capability)
    }

    private class RecordingDenyGate : BackendCapabilityGate {
        var lastCapability: BackendCapability? = null

        override fun decision(capability: BackendCapability): BackendCapabilityDecision {
            lastCapability = capability
            return deniedDecision(capability)
        }
    }
}

private fun deniedDecision(capability: BackendCapability): BackendCapabilityDecision {
    return BackendCapabilityDecision(
        visible = false,
        executable = false,
        forced = false,
        reason = if (capability == BackendCapability.HEALTH_PROBE) {
            BackendCapabilityReason.HEALTH_PROBE
        } else {
            BackendCapabilityReason.UNREACHABLE
        },
    )
}
