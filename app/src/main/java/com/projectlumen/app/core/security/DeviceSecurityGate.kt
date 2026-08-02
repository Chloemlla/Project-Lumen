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
        BLOCKED,
    }

    private val appContext = context.applicationContext
    private val scanner = DeviceSecurityScanner(appContext)
    private val scanStarted = AtomicBoolean(false)
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

    /** Starts one full scan; callers may safely invoke this more than once. */
    fun startStartupScan(scope: CoroutineScope) {
        if (!scanStarted.compareAndSet(false, true)) return
        _state.value = State.SCANNING
        scope.launch(Dispatchers.Default) {
            val result = runCatching { scanner.fullScan() }
                .getOrElse { error ->
                    Log.e(TAG, "Startup CRooot scan failed", error)
                    DeviceSecurityScanner.SecurityAssessment.failed(error)
                }
            _assessment.value = result
            _state.value = if (nativeIntegrityOk && isSafe(result)) State.ALLOWED else State.BLOCKED
            Log.i(TAG, "Startup device security state=${_state.value}")
        }
    }

    /** Foreground services and local sensor/control features require a clean scan. */
    fun isServiceAllowed(): Boolean = _state.value == State.ALLOWED

    /**
     * Keeps login, health, registration, and telemetry available so a blocked device can
     * report its state, but never lets connectivity overrides bypass high-impact capabilities.
     */
    fun requireBackendAllowed(capability: BackendCapability) {
        if (capability in REPORTING_CAPABILITIES) return
        if (!isServiceAllowed()) {
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
