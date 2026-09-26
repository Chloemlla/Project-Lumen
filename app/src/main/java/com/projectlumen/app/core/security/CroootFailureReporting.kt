package com.projectlumen.app.core.security

import com.chloemlla.crooot.CRoootScanOptions
import com.chloemlla.crooot.CRoootScanResult
import com.eltavine.duckdetector.features.nativeroot.domain.NativeRootMethodOutcome
import com.eltavine.duckdetector.features.nativeroot.domain.NativeRootReport
import com.eltavine.duckdetector.features.nativeroot.domain.NativeRootStage
import com.eltavine.duckdetector.features.selinux.domain.SelinuxReport
import com.eltavine.duckdetector.features.selinux.domain.SelinuxStage
import com.eltavine.duckdetector.features.tee.domain.TeeReport
import com.eltavine.duckdetector.features.tee.domain.TeeScanStage
import com.eltavine.duckdetector.features.virtualization.domain.VirtualizationReport
import com.eltavine.duckdetector.features.virtualization.domain.VirtualizationStage

/** Which CRooot scan produced a failure. */
enum class CroootScanPhase {
    QUICK,
    FULL,
    MANUAL;

    /** Stable, log-safe name used in reports and breadcrumbs. */
    val label: String
        get() = when (this) {
            QUICK -> "quick"
            FULL -> "full"
            MANUAL -> "manual"
        }
}

/**
 * A CRooot failure the crash reporter should persist.
 *
 * CRooot's sacrificial native probes run in `fork()`ed helper processes, so a probe the app seccomp
 * policy kills with `SIGSYS` never reaches the Java uncaught-exception handler and never appears in
 * `ActivityManager`'s process-exit history. Scan-level failures and the probe failures CRooot
 * reports back in its own result have to be handed to the crash reporter explicitly or they exist
 * only in logcat.
 */
data class CroootFailure(
    /** The scan that failed. */
    val phase: CroootScanPhase,
    /** The options the failing scan ran with. */
    val options: CRoootScanOptions,
    /** The thrown failure, or `null` when the scan timed out. */
    val throwable: Throwable?,
    /** Bounded, non-sensitive description of what failed. */
    val message: String,
) {
    /** Converts this failure into the throwable the crash reporter accepts. */
    fun asThrowable(): CroootScanException = CroootScanException(phase, message, throwable)

    /** Compact breadcrumb text, so a later crash report still shows that CRooot failed. */
    fun breadcrumb(): String = "CRooot ${phase.label} scan failed: $message"
}

/**
 * Throwable used to carry a CRooot scan failure into `LumenCrash.recordNonFatal`.
 *
 * The stack trace belongs to the CRooot integration point, so [cause] keeps the original throwable
 * (and therefore its stack) attached.
 */
class CroootScanException(
    val phase: CroootScanPhase,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * Compresses the failure signals inside a completed CRooot result into bounded breadcrumb lines.
 *
 * Only labels, stages and counts are emitted: this text is attached to crash reports, which are
 * uploaded off-device, so paths, certificate digests and device identifiers stay out. Every line is
 * length-capped and the list is capped as well, because CRooot can report a failure per detector.
 */
internal object CroootFailureDigest {
    private const val MAX_LINES = 12
    private const val MAX_LINE_LENGTH = 180

    fun describe(result: CRoootScanResult): List<String> {
        val lines = linkedSetOf<String>()

        val nativeRoot = result.duckReports["nativeRoot"] as? NativeRootReport
        if (nativeRoot != null) {
            if (nativeRoot.ksuSupercallAttempted && nativeRoot.ksuSupercallBlocked) {
                lines += "nativeRoot: KernelSU sacrificial reboot probe blocked by the app seccomp policy"
            }
            nativeRoot.errorMessage?.let { lines += "nativeRoot error: $it" }
            if (nativeRoot.stage != NativeRootStage.READY) {
                lines += "nativeRoot stage=${nativeRoot.stage}"
            }
            nativeRoot.methods
                .filter { it.outcome == NativeRootMethodOutcome.SUPPORT }
                .forEach { method -> lines += "nativeRoot probe unavailable: ${method.label} (${method.summary})" }
        }

        val virtualization = result.duckReports["virtualization"] as? VirtualizationReport
        if (virtualization != null) {
            virtualization.errorMessage?.let { lines += "virtualization error: $it" }
            if (virtualization.stage != VirtualizationStage.READY) {
                lines += "virtualization stage=${virtualization.stage}"
            }
            if (!virtualization.syscallPackSupported) {
                lines += "virtualization: sacrificial syscall pack unsupported"
            }
        }

        val selinux = result.duckReports["selinux"] as? SelinuxReport
        if (selinux != null) {
            selinux.errorMessage?.let { lines += "selinux error: $it" }
            if (selinux.stage != SelinuxStage.READY) {
                lines += "selinux stage=${selinux.stage}"
            }
        }

        val tee = result.duckReports["tee"] as? TeeReport
        if (tee != null) {
            tee.failureMessage?.let { lines += "tee failure: $it" }
            if (tee.stage != TeeScanStage.READY) {
                lines += "tee stage=${tee.stage}"
            }
        }

        return lines
            .take(MAX_LINES)
            .map { line -> line.replace(Regex("\\s+"), " ").take(MAX_LINE_LENGTH) }
    }
}
