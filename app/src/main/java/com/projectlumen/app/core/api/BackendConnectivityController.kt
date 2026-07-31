package com.projectlumen.app.core.api

import com.chloemlla.lumen.crash.CrashBreadcrumbs
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class BackendConnectivityController internal constructor(
    private val scope: CoroutineScope,
    private val persistence: BackendConnectivityPersistence,
    private val healthProbe: suspend () -> ApiHealth,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val delayMillis: suspend (Long) -> Unit = { delay(it) },
    private val probeTimeoutMillis: Long = PROBE_TIMEOUT_MILLIS,
) : BackendCapabilityGate {
    private val started = AtomicBoolean(false)
    private val foreground = AtomicBoolean(false)
    private val blockedBreadcrumbAt = AtomicLong(0L)
    private val probeLock = Any()
    private var activeProbe: Deferred<BackendConnectivityState>? = null
    private var retryJob: Job? = null
    private val _state = MutableStateFlow(initialState(persistence.load()))

    val state: StateFlow<BackendConnectivityState> = _state.asStateFlow()

    override fun decision(capability: BackendCapability): BackendCapabilityDecision {
        return BackendCommunicationPolicy.resolve(_state.value, capability, nowMillis())
    }

    override fun requireExecutable(capability: BackendCapability) {
        val decision = decision(capability)
        if (decision.executable) return
        recordBlockedBreadcrumb(capability, decision.reason)
        throw BackendCommunicationBlockedException(capability, decision.reason.code)
    }

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch { refresh() }
    }

    fun onForeground() {
        foreground.set(true)
        retryJob?.cancel()
        retryJob = null
        scope.launch { refresh(force = _state.value.status == BackendHealthStatus.UNREACHABLE) }
    }

    fun onBackground() {
        foreground.set(false)
        retryJob?.cancel()
        retryJob = null
    }

    fun refreshAsync(force: Boolean = true) {
        scope.launch { refresh(force) }
    }

    suspend fun refresh(force: Boolean = false): BackendConnectivityState {
        var freshState: BackendConnectivityState? = null
        val probe = synchronized(probeLock) {
            activeProbe?.takeIf { it.isActive } ?: run {
                val current = _state.value
                if (!force && isProbeFresh(current, nowMillis())) {
                    freshState = current
                    null
                } else {
                    scope.async(start = CoroutineStart.LAZY) { performProbe() }.also { created ->
                        activeProbe = created
                        created.invokeOnCompletion {
                            synchronized(probeLock) {
                                if (activeProbe === created) activeProbe = null
                            }
                        }
                        created.start()
                    }
                }
            }
        }
        freshState?.let { return it }
        return checkNotNull(probe).await()
    }

    fun setDeveloperForceEnabled(enabled: Boolean) {
        val updated = _state.value.copy(developerForceEnabled = enabled)
        _state.value = updated
        persist(updated)
        recordBreadcrumb("force_enabled=$enabled actual=${updated.status.name.lowercase()}")
    }

    private suspend fun performProbe(): BackendConnectivityState {
        val before = _state.value
        _state.value = before.copy(status = BackendHealthStatus.CHECKING, lastErrorCode = "")
        recordStatusTransition(before.status, BackendHealthStatus.CHECKING)
        var lastError: Throwable? = null
        repeat(PROBE_ATTEMPTS) { attempt ->
            val result = runCatching {
                withTimeout(probeTimeoutMillis) { healthProbe() }
            }.mapCatching { health ->
                if (!health.status.equals(EXPECTED_HEALTH_STATUS, ignoreCase = true)) {
                    throw IOException("Project Lumen backend health status was not ok.")
                }
                health
            }
            result.onSuccess {
                return markReachable()
            }.onFailure { throwable ->
                if (throwable is CancellationException && throwable !is TimeoutCancellationException) {
                    throw throwable
                }
                lastError = throwable
            }
            if (attempt + 1 < PROBE_ATTEMPTS) {
                delayMillis(PROBE_ATTEMPT_DELAY_MILLIS)
            }
        }
        return markUnreachable(lastError)
    }

    private fun markReachable(): BackendConnectivityState {
        retryJob?.cancel()
        retryJob = null
        val before = _state.value
        val now = nowMillis()
        val updated = before.copy(
            status = BackendHealthStatus.REACHABLE,
            lastConfirmedStatus = BackendHealthStatus.REACHABLE,
            lastCheckedAtMillis = now,
            lastReachableAtMillis = now,
            consecutiveFailures = 0,
            lastErrorCode = "",
        )
        _state.value = updated
        persist(updated)
        recordStatusTransition(before.status, updated.status)
        return updated
    }

    private fun markUnreachable(error: Throwable?): BackendConnectivityState {
        val before = _state.value
        val updated = before.copy(
            status = BackendHealthStatus.UNREACHABLE,
            lastConfirmedStatus = BackendHealthStatus.UNREACHABLE,
            lastCheckedAtMillis = nowMillis(),
            consecutiveFailures = before.consecutiveFailures + 1,
            lastErrorCode = errorCode(error),
        )
        _state.value = updated
        persist(updated)
        recordStatusTransition(before.status, updated.status, updated.lastErrorCode)
        scheduleRecoveryRetry(updated.consecutiveFailures)
        return updated
    }

    private fun scheduleRecoveryRetry(consecutiveFailures: Int) {
        if (!foreground.get()) return
        retryJob?.cancel()
        retryJob = scope.launch {
            delayMillis(BackendRetryPolicy.delayMillis(consecutiveFailures))
            if (foreground.get()) refresh(force = true)
        }
    }

    private fun persist(state: BackendConnectivityState) {
        persistence.save(
            PersistedBackendConnectivity(
                lastConfirmedStatus = state.lastConfirmedStatus,
                lastCheckedAtMillis = state.lastCheckedAtMillis,
                lastReachableAtMillis = state.lastReachableAtMillis,
                consecutiveFailures = state.consecutiveFailures,
                developerForceEnabled = state.developerForceEnabled,
                lastErrorCode = state.lastErrorCode,
            ),
        )
    }

    private fun isProbeFresh(state: BackendConnectivityState, now: Long): Boolean {
        if (state.status == BackendHealthStatus.CHECKING) return false
        if (state.status == BackendHealthStatus.UNREACHABLE) return false
        val ageMillis = now - state.lastCheckedAtMillis
        return state.lastCheckedAtMillis > 0L && ageMillis in 0L until MIN_PROBE_INTERVAL_MILLIS
    }

    private fun errorCode(error: Throwable?): String {
        return when (error) {
            is TimeoutCancellationException -> "timeout"
            is ProjectLumenApiException -> "http_${error.statusCode}"
            is IOException -> "io"
            null -> "unknown"
            else -> "unexpected"
        }
    }

    private fun recordBlockedBreadcrumb(
        capability: BackendCapability,
        reason: BackendCapabilityReason,
    ) {
        val now = nowMillis()
        val previous = blockedBreadcrumbAt.get()
        if (now - previous < BLOCKED_BREADCRUMB_INTERVAL_MILLIS) return
        if (!blockedBreadcrumbAt.compareAndSet(previous, now)) return
        recordBreadcrumb("blocked capability=${capability.name.lowercase()} reason=${reason.code}")
    }

    private fun recordStatusTransition(
        from: BackendHealthStatus,
        to: BackendHealthStatus,
        errorCode: String = "",
    ) {
        if (from == to && errorCode.isBlank()) return
        val suffix = errorCode.takeIf { it.isNotBlank() }?.let { " error=$it" }.orEmpty()
        recordBreadcrumb("status=${to.name.lowercase()} from=${from.name.lowercase()}$suffix")
    }

    private fun recordBreadcrumb(message: String) {
        runCatching { CrashBreadcrumbs.record("Backend connectivity $message") }
    }

    private fun initialState(persisted: PersistedBackendConnectivity): BackendConnectivityState {
        return BackendConnectivityState(
            status = persisted.lastConfirmedStatus,
            lastConfirmedStatus = persisted.lastConfirmedStatus,
            lastCheckedAtMillis = persisted.lastCheckedAtMillis,
            lastReachableAtMillis = persisted.lastReachableAtMillis,
            consecutiveFailures = persisted.consecutiveFailures,
            developerForceEnabled = persisted.developerForceEnabled,
            lastErrorCode = persisted.lastErrorCode,
        )
    }

    companion object {
        private const val EXPECTED_HEALTH_STATUS = "ok"
        private const val PROBE_ATTEMPTS = 2
        private const val PROBE_TIMEOUT_MILLIS = 7_000L
        private const val PROBE_ATTEMPT_DELAY_MILLIS = 500L
        private const val MIN_PROBE_INTERVAL_MILLIS = 60_000L
        private const val BLOCKED_BREADCRUMB_INTERVAL_MILLIS = 60_000L
    }
}
