package com.projectlumen.app.core.security

import com.chloemlla.crooot.CRoootScanResult
import com.eltavine.duckdetector.features.nativeroot.domain.NativeRootMethodOutcome
import com.eltavine.duckdetector.features.nativeroot.domain.NativeRootMethodResult
import com.eltavine.duckdetector.features.nativeroot.domain.NativeRootReport
import com.eltavine.duckdetector.features.nativeroot.domain.NativeRootStage
import com.juanma0511.rootdetector.model.ScanResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CRooot reports probe failures inside its own result instead of throwing, and its sacrificial
 * native probes die in `fork()`ed helper processes that no crash handler can see. The digest is the
 * only path that turns those signals into something a crash report carries.
 */
class CroootFailureDigestTest {
    @Test
    fun blockedSacrificialProbeIsReported() {
        val report = NativeRootReport.loading().copy(
            stage = NativeRootStage.READY,
            ksuSupercallAttempted = true,
            ksuSupercallBlocked = true,
        )

        val digest = CroootFailureDigest.describe(resultWith("nativeRoot" to report))

        assertTrue(
            "A probe killed by the app seccomp policy must be named in the digest",
            digest.any { it.contains("KernelSU sacrificial reboot probe blocked") },
        )
        assertTrue("A READY report must not be reported as a failed stage", digest.none { it.contains("stage=") })
    }

    @Test
    fun unavailableProbesAndFailedStagesAreReported() {
        val report = NativeRootReport.loading().copy(
            stage = NativeRootStage.FAILED,
            errorMessage = "native collector unavailable",
            methods = listOf(
                NativeRootMethodResult(
                    label = "cgroupLeakage",
                    summary = "Unavailable",
                    outcome = NativeRootMethodOutcome.SUPPORT,
                    detail = "hidden by the platform",
                ),
            ),
        )

        val digest = CroootFailureDigest.describe(resultWith("nativeRoot" to report))

        assertTrue(digest.any { it.contains("nativeRoot error: native collector unavailable") })
        assertTrue(digest.any { it.contains("nativeRoot stage=FAILED") })
        assertTrue(digest.any { it.contains("nativeRoot probe unavailable: cgroupLeakage") })
    }

    @Test
    fun digestOfAScanWithoutDuckReportsStaysEmpty() {
        // A quick scan intentionally requests no Duck features, so the absence of those reports is
        // not a failure and must not be reported as one.
        val digest = CroootFailureDigest.describe(resultWith())

        assertTrue("A feature-less scan must not fabricate failures", digest.isEmpty())
    }

    @Test
    fun digestStaysBounded() {
        val report = NativeRootReport.loading().copy(
            errorMessage = "x".repeat(500),
            methods = (1..40).map { index ->
                NativeRootMethodResult(
                    label = "probe-$index",
                    summary = "Unavailable",
                    outcome = NativeRootMethodOutcome.SUPPORT,
                    detail = "detail",
                )
            },
        )

        val digest = CroootFailureDigest.describe(resultWith("nativeRoot" to report))

        assertTrue("Digest must stay bounded", digest.size <= 12)
        assertTrue("Digest lines must stay bounded", digest.all { it.length <= 180 })
    }

    @Test
    fun digestFlattensWhitespaceSoABreadcrumbStaysOneLine() {
        val report = NativeRootReport.loading().copy(
            errorMessage = "line one\nline two\ttabbed",
        )

        val digest = CroootFailureDigest.describe(resultWith("nativeRoot" to report))

        assertTrue(digest.any { it == "nativeRoot error: line one line two tabbed" })
        assertEquals(1, digest.count { it.startsWith("nativeRoot error:") })
    }

    private fun resultWith(vararg reports: Pair<String, Any?>): CRoootScanResult = CRoootScanResult(
        kkndRoot = ScanResult(items = emptyList(), scanDurationMs = 0L),
        kkndHardware = null,
        duckReports = reports.toMap(),
        durationMs = 0L,
    )
}
