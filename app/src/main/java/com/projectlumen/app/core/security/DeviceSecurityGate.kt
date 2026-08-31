package com.projectlumen.app.core.security

import android.content.Context
import android.util.Log
import com.projectlumen.app.core.api.BackendCapability
import com.projectlumen.app.core.api.BackendCommunicationBlockedException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Application-wide fail-closed policy for CRooot-backed device security.
 *
 * The raw CRooot object never leaves the process. Only the bounded fields in
 * [backendEvidence] are sent during device registration so the backend can
 * refuse high-impact operations for the same installation.
 */
class DeviceSecurityGate(context: Context) {
    enum class State {
        UNKNOWN,
        SCANNING,
        ALLOWED,

        /**
         * The device is not proven dangerous, but the scan could not fully vouch for it
         * (timeout, permissive SELinux, missing hardware attestation). Local features keep
         * working; high-impact backend capabilities stay refused.
         */
        DEGRADED,
        BLOCKED,
    }

    private val appContext = context.applicationContext
    private val scanner = DeviceSecurityScanner(appContext)
    private val scanInFlight = AtomicBoolean(false)
    private val _state = MutableStateFlow(State.UNKNOWN)
    private val _assessment = MutableStateFlow<DeviceSecurityScanner.SecurityAssessment?>(null)

    val state: StateFlow<State> = _state.asStateFlow()
    val assessment: StateFlow<DeviceSecurityScanner.SecurityAssessment?> = _assessment.asStateFlow()

    private var nativeIntegrityOk = true

    init {
        nativeIntegrityOk = runCatching {
            AppIntegrityGuard.enforce(appContext)
            true
        }.getOrElse {
            Log.e(TAG, "Native integrity check blocked the device", it)
            false
        }
    }

    /**
     * Starts a quick root check followed by the full scan; callers may safely invoke this more
     * than once. Re-invoking after a previous run completes re-scans, so a transient failure is
     * never terminal for the installation.
     */
    fun startStartupScan(scope: CoroutineScope) {
        if (!scanInFlight.compareAndSet(false, true)) return
        if (_state.value == State.UNKNOWN) _state.value = State.SCANNING
        scope.launch(Dispatchers.Default) {
            try {
                // The quick scan only probes for root, so it lands a verdict in milliseconds and
                // closes the cold-start window where nothing is known yet.
                publish(runCatching { scanner.quickScan() }.getOrElse { error ->
                    Log.w(TAG, "Quick CRooot scan failed", error)
                    DeviceSecurityScanner.SecurityAssessment.failed(error)
                })
                publish(runCatching { scanner.fullScan() }.getOrElse { error ->
                    Log.e(TAG, "Startup CRooot scan failed", error)
                    DeviceSecurityScanner.SecurityAssessment.failed(error)
                })
            } finally {
                scanInFlight.set(false)
            }
        }
    }

    private fun publish(result: DeviceSecurityScanner.SecurityAssessment) {
        _assessment.value = result
        _state.value = classify(result)
        Log.i(TAG, "Device security state=${_state.value}")
    }

    /**
     * Foreground services and local sensor/control features run unless the device is proven
     * dangerous. An unfinished scan is not evidence of danger, so it must not silently swallow
     * the user's action.
     */
    fun isServiceAllowed(): Boolean = _state.value != State.BLOCKED

    /** High-impact backend capabilities require a scan that fully vouched for the device. */
    fun isFullyTrusted(): Boolean = _state.value == State.ALLOWED

    /** Suspends until the scan lands a verdict, for callers that can wait instead of guessing. */
    suspend fun awaitDecision(): State =
        state.first { it != State.UNKNOWN && it != State.SCANNING }

    /**
     * Keeps login, health, registration, and telemetry available so a blocked device can
     * report its state, but never lets connectivity overrides bypass high-impact capabilities.
     */
    fun requireBackendAllowed(capability: BackendCapability) {
        if (capability in REPORTING_CAPABILITIES) return
        if (!isFullyTrusted()) {
            throw BackendCommunicationBlockedException(capability, DEVICE_SECURITY_BLOCKED_REASON)
        }
    }

    /** A compact, non-sensitive CRooot summary for the device registration request. */
    fun backendEvidence(): JSONObject? {
        val current = _assessment.value ?: return null
        return JSONObject()
            .put("status", if (isSafe(current)) "clean" else "dangerous")
            .put("verified", false)
            .put("completed", current.completed)
            .put("rooted", current.rooted)
            .put("suspicious", current.suspicious)
            .put("hardwareIntegrityOk", current.hardwareIntegrityOk)
            .put("selinuxEnforcing", current.selinuxEnforcing)
            .put("teeAttestationOk", current.teeAttestationOk)
            .put("observedAt", System.currentTimeMillis())
            .put("scannerVersion", "crooot-0.1.0")
    }

    /**
     * Only a positive root indication (or a failed native integrity check) is treated as proof of
     * danger. Everything else the scanner cannot vouch for degrades instead of bricking the app:
     * permissive SELinux, absent hardware attestation and scan timeouts are common on third-party
     * ROMs and emulators, and killing every local feature there is a false positive, not security.
     *
     * A quick scan leaves the hardware/SELinux/TEE checks unpopulated, so it can never reach
     * [State.ALLOWED] on its own — only the full scan can vouch for high-impact capabilities.
     */
    private fun classify(assessment: DeviceSecurityScanner.SecurityAssessment): State = when {
        !nativeIntegrityOk -> State.BLOCKED
        assessment.rooted -> State.BLOCKED
        !assessment.completed -> State.DEGRADED
        assessment.hardwareIntegrityOk != true -> State.DEGRADED
        assessment.selinuxEnforcing != true -> State.DEGRADED
        assessment.teeAttestationOk != true -> State.DEGRADED
        else -> State.ALLOWED
    }

    private fun isSafe(assessment: DeviceSecurityScanner.SecurityAssessment): Boolean {
        // Null means that a detector was not part of the selected profile; an explicit failure
        // is represented by false and must fail closed. Startup uses fullScan, so all checks are
        // normally populated in production.
        return assessment.completed &&
            !assessment.rooted &&
            assessment.hardwareIntegrityOk != false &&
            assessment.selinuxEnforcing != false &&
            assessment.teeAttestationOk != false
    }

    companion object {
        const val DEVICE_SECURITY_BLOCKED_REASON = "device_security_blocked"
        private const val TAG = "DeviceSecurityGate"
        private val REPORTING_CAPABILITIES = setOf(
            BackendCapability.HEALTH_PROBE,
            BackendCapability.ACCOUNT_SESSION,
            BackendCapability.DEVICE_REGISTRATION,
            BackendCapability.TELEMETRY,
            BackendCapability.RELEASE_DISCOVERY,
        )
    }
}
