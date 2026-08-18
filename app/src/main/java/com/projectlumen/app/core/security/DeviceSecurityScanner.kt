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
class DeviceSecurityScanner(private val context: Context) {

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
        /** Human-readable summary for diagnostics. */
        val summary: String,
        /** Raw CRooot result, or null if the scan failed or timed out. */
        val rawResult: CRoootScanResult?,
        /** Error message if the scan failed. */
        val errorMessage: String?,
    ) {
        companion object {
            internal fun timeout() = SecurityAssessment(
                completed = false,
                rooted = false,
                suspicious = false,
                hardwareIntegrityOk = null,
                selinuxEnforcing = null,
                teeAttestationOk = null,
                summary = "CRooot scan timed out.",
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
                summary = "CRooot scan failed: ${cause.message ?: cause::class.java.simpleName}",
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
    ): SecurityAssessment = scanMutex.withLock {
        withContext(Dispatchers.Default) {
            try {
                val result = withTimeout(scanTimeoutMs) {
                    sdk.scan(options)
                }
                distill(result)
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                Log.w(TAG, "CRooot scan timed out after ${scanTimeoutMs}ms.", e)
                SecurityAssessment.timeout()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "CRooot scan failed.", e)
                SecurityAssessment.failed(e)
            }
        }
    }

    /**
     * Runs a quick scan (KKND root only, no Duck features, no hardware).
     * Suitable for cold-start or background checks.
     */
    suspend fun quickScan(): SecurityAssessment = scan(
        CRoootScanOptions(
            includeHardware = false,
            includeDuckFeatures = false,
        ),
    )

    /**
     * Runs a full scan with all features enabled.
     * Suitable for user-initiated security checks.
     */
    suspend fun fullScan(): SecurityAssessment = scan(
        CRoootScanOptions(
            includeHardware = true,
            includeDuckFeatures = true,
        ),
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
            summary = CroootReportFormatter.format(result),
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