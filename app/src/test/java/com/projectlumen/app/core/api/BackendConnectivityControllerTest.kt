package com.projectlumen.app.core.api

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendConnectivityControllerTest {
    @Test
    fun persistedStableStateIsRestored() {
        val persistence = FakePersistence(
            PersistedBackendConnectivity(
                lastConfirmedStatus = BackendHealthStatus.UNREACHABLE,
                lastCheckedAtMillis = 123L,
                lastReachableAtMillis = 100L,
                consecutiveFailures = 3,
                developerForceEnabled = true,
                lastErrorCode = "io",
            ),
        )
        val scope = CoroutineScope(SupervisorJob())
        val controller = controller(scope, persistence) { healthy() }

        assertEquals(BackendHealthStatus.UNREACHABLE, controller.state.value.status)
        assertEquals(123L, controller.state.value.lastCheckedAtMillis)
        assertEquals(3, controller.state.value.consecutiveFailures)
        assertTrue(controller.state.value.developerForceEnabled)
        scope.cancel()
    }

    @Test
    fun failedProbeRecoversAndPersistsSuccess() = runBlocking {
        val persistence = FakePersistence()
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        var reachable = false
        var nowMillis = 500L
        val controller = controller(
            scope = scope,
            persistence = persistence,
            nowMillis = { nowMillis },
        ) {
            if (!reachable) throw IOException("offline")
            healthy()
        }

        val failed = controller.refresh(force = true)
        assertEquals(BackendHealthStatus.UNREACHABLE, failed.status)
        assertEquals(1, failed.consecutiveFailures)
        assertEquals("io", failed.lastErrorCode)

        reachable = true
        nowMillis = 900L
        val recovered = controller.refresh(force = true)
        assertEquals(BackendHealthStatus.REACHABLE, recovered.status)
        assertEquals(0, recovered.consecutiveFailures)
        assertEquals(900L, recovered.lastReachableAtMillis)
        assertEquals(BackendHealthStatus.REACHABLE, persistence.value.lastConfirmedStatus)
        scope.cancel()
    }

    @Test
    fun developerOverrideIsPersistedSeparatelyFromHealth() {
        val persistence = FakePersistence(
            PersistedBackendConnectivity(lastConfirmedStatus = BackendHealthStatus.UNREACHABLE),
        )
        val scope = CoroutineScope(SupervisorJob())
        val controller = controller(scope, persistence) { healthy() }

        controller.setDeveloperForceEnabled(true)

        assertTrue(controller.state.value.developerForceEnabled)
        assertEquals(BackendHealthStatus.UNREACHABLE, controller.state.value.status)
        assertTrue(persistence.value.developerForceEnabled)
        assertEquals(BackendHealthStatus.UNREACHABLE, persistence.value.lastConfirmedStatus)
        scope.cancel()
    }

    @Test
    fun httpFailureUsesStableErrorCode() = runBlocking {
        val persistence = FakePersistence()
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val controller = controller(scope, persistence) {
            throw ProjectLumenApiException(503, "unavailable")
        }

        val state = controller.refresh(force = true)

        assertEquals(BackendHealthStatus.UNREACHABLE, state.status)
        assertEquals("http_503", state.lastErrorCode)
        assertEquals("http_503", persistence.value.lastErrorCode)
        scope.cancel()
    }

    @Test
    fun concurrentRefreshesShareOneProbe() = runBlocking {
        val persistence = FakePersistence()
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val releaseProbe = CompletableDeferred<Unit>()
        var probeCalls = 0
        val controller = controller(scope, persistence) {
            probeCalls += 1
            releaseProbe.await()
            healthy()
        }

        val first = async { controller.refresh(force = true) }
        val second = async { controller.refresh(force = false) }
        while (probeCalls == 0) yield()

        assertEquals(1, probeCalls)
        assertFalse(first.isCompleted)
        assertFalse(second.isCompleted)
        releaseProbe.complete(Unit)
        assertEquals(BackendHealthStatus.REACHABLE, first.await().status)
        assertEquals(BackendHealthStatus.REACHABLE, second.await().status)
        assertEquals(1, probeCalls)
        scope.cancel()
    }

    private fun controller(
        scope: CoroutineScope,
        persistence: FakePersistence,
        nowMillis: () -> Long = { 1_000L },
        healthProbe: suspend () -> ApiHealth,
    ): BackendConnectivityController {
        return BackendConnectivityController(
            scope = scope,
            persistence = persistence,
            healthProbe = healthProbe,
            nowMillis = nowMillis,
            delayMillis = {},
            probeTimeoutMillis = 1_000L,
        )
    }

    private fun healthy() = ApiHealth(status = "ok", service = "project-lumen", version = "test")

    private class FakePersistence(
        var value: PersistedBackendConnectivity = PersistedBackendConnectivity(),
    ) : BackendConnectivityPersistence {
        override fun load(): PersistedBackendConnectivity = value

        override fun save(value: PersistedBackendConnectivity) {
            this.value = value
        }
    }
}
