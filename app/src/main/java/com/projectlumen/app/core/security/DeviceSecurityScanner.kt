package com.projectlumen.app.core.security

import android.content.Context
import android.util.Log
import com.chloemlla.crooot.CRoootScanOptions
import com.chloemlla.crooot.CRoootScanResult
import com.chloemlla.crooot.CRoootSdk
import com.eltavine.duckdetector.features.selinux.domain.SelinuxMode
import com.eltavine.duckdetector.features.selinux.domain.SelinuxReport
import com.eltavine.duckdetector.features.tee.domain.TeeReport
import com.eltavine.duckdetector.features.tee.domain.TeeScanStage
import com.eltavine.duckdetector.features.tee.domain.TeeVerdict
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Integrates CRooot device-security scanning into Project Lumen.
 *
 * This wrapper enforces the one-scan-at-a-time rule, applies a configurable timeout,
 * and exposes structured results for the [AppIntegrityGuard] and other security consumers.
 *
 * Usage:
 * ```kotlin
 * val scanner = DeviceSecurityScanner(context)
 * val assessment = scanner.scan()
 * if (assessment.rooted) { /* device is likely rooted */ }
 * ```
 */
class DeviceSecurityScanner(
    private val context: Context,
    /**
     * Receives CRooot failures so they reach the crash reporter instead of only logcat.
     *
     * CRooot's sacrificial probes run in `fork()`ed helper processes, so their deaths are invisible
     * to both the Java uncaught-exception handler and `ActivityManager`'s process-exit history.
     * Defaults to a no-op so a scanner without a crash reporter still behaves as before.
     */
    private val failureReporter: ((CroootFailure) -> Unit)? = null,
) {

    /** Result of a CRooot device-security scan, distilled for consumption by Lumen's security layer. */
    data class SecurityAssessment(
        /** Whether the scan completed within the timeout. */
        val completed: Boolean,
        /** `true` when a HIGH-severity root indication was found. */
        val rooted: Boolean,
        /** `true` when any suspicious indicator was found (any severity). */
        val suspicious: Boolean,
        /** `true` when the hardware/TEE integrity check passed (or was skipped). */
        val hardwareIntegrityOk: Boolean?,
        /** `true` when SELinux is in enforcing mode without paradox. */
        val selinuxEnforcing: Boolean?,
        /** `true` when TEE attestation completed without failure. */
        val teeAttestationOk: Boolean?,
        /** Pre-formatted summary for scans that had no raw result (timeout/failure). */
        private val summaryOverride: String?,
        /** Raw CRooot result, or null if the scan failed or timed out. */
        val rawResult: CRoootScanResult?,
        /** Error message if the scan failed. */
        val errorMessage: String?,
    ) {
        /** Human-readable summary for diagnostics, formatted on first access only. */
        val summary: String by lazy { summaryOverride ?: rawResult?.let(CroootReportFormatter::format) ?: "" }

        companion object {
            internal fun timeout() = SecurityAssessment(
                completed = false,
                rooted = false,
                suspicious = false,
                hardwareIntegrityOk = null,
                selinuxEnforcing = null,
                teeAttestationOk = null,
                summaryOverride = "CRooot scan timed out.",
                rawResult = null,
                errorMessage = "Scan timed out.",
            )

            internal fun failed(cause: Throwable) = SecurityAssessment(
                completed = false,
                rooted = false,
                suspicious = false,
                hardwareIntegrityOk = null,
                selinuxEnforcing = null,
                teeAttestationOk = null,
                summaryOverride = "CRooot scan failed: ${cause.message ?: cause::class.java.simpleName}",
                rawResult = null,
                errorMessage = cause.message ?: cause::class.java.simpleName,
            )
        }
    }

    private val sdk = CRoootSdk.create(context)
    private val scanMutex = Mutex()

    /** Default scan timeout in milliseconds. */
    var scanTimeoutMs: Long = DEFAULT_SCAN_TIMEOUT_MS

    /**
     * Runs a CRooot device-security scan with the given options.
     *
     * Thread-safe: only one scan runs at a time across this instance.
     * Cancellation-safe: [withTimeout] bounds host waiting; blocking probe
     * cleanup may continue briefly after timeout.
     */
    suspend fun scan(
        options: CRoootScanOptions = CRoootScanOptions(
            includeHardware = true,
            includeDuckFeatures = true,
        ),
        phase: CroootScanPhase = CroootScanPhase.MANUAL,
    ): SecurityAssessment = scanMutex.withLock {
        withContext(Dispatchers.Default) {
            try {
                val result = withTimeout(scanTimeoutMs) {
                    sdk.scan(options)
                }
                distill(result)
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                Log.w(TAG, "CRooot ${phase.label} scan timed out after ${scanTimeoutMs}ms.", e)
                reportFailure(
                    CroootFailure(
                        phase = phase,
                        options = options,
                        throwable = null,
                        message = "timed out after ${scanTimeoutMs}ms",
                    ),
                )
                SecurityAssessment.timeout()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "CRooot ${phase.label} scan failed.", e)
                reportFailure(
                    CroootFailure(
                        phase = phase,
                        options = options,
                        throwable = e,
                        message = "${e::class.java.simpleName}: ${e.message ?: "no message"}",
                    ),
                )
                SecurityAssessment.failed(e)
            }
        }
    }

    /** Never lets a failing reporter break the scan that is already reporting its failure. */
    private fun reportFailure(failure: CroootFailure) {
        runCatching { failureReporter?.invoke(failure) }
            .onFailure { Log.e(TAG, "CRooot failure reporter threw.", it) }
    }

    /**
     * Runs a quick scan (KKND root only, no Duck features, no hardware).
     * Suitable for cold-start or background checks.
     */
    suspend fun quickScan(): SecurityAssessment = scan(
        options = CRoootScanOptions(
            includeHardware = false,
            includeDuckFeatures = false,
        ),
        phase = CroootScanPhase.QUICK,
    )

    /**
     * Runs a full scan with all features enabled.
     * Suitable for user-initiated security checks.
     */
    suspend fun fullScan(): SecurityAssessment = scan(
        options = CRoootScanOptions(
            includeHardware = true,
            includeDuckFeatures = true,
        ),
        phase = CroootScanPhase.FULL,
    )

    private fun distill(result: CRoootScanResult): SecurityAssessment {
        val tee = result.duckReports["tee"] as? TeeReport
        val selinux = result.duckReports["selinux"] as? SelinuxReport

        return SecurityAssessment(
            completed = true,
            rooted = result.kkndRoot.isRooted,
            suspicious = result.kkndRoot.isSuspicious,
            hardwareIntegrityOk = result.kkndHardware?.overallOk,
            selinuxEnforcing = selinux?.mode?.let { mode ->
                mode == SelinuxMode.ENFORCING && selinux.paradoxDetected != true
            },
            teeAttestationOk = when {
                tee == null -> null
                tee.stage == TeeScanStage.READY -> tee.verdict.let { v ->
                    v == TeeVerdict.CONSISTENT
                }
                else -> false
            },
            summaryOverride = null,
            rawResult = result,
            errorMessage = null,
        )
    }

    companion object {
        private const val TAG = "DeviceSecurityScanner"
        /** Default timeout: 60 seconds. */
        private const val DEFAULT_SCAN_TIMEOUT_MS = 60_000L
    }
}