package com.projectlumen.app.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeSecurityVerdictTest {
    @Test
    fun zeroMaskIsAllowed() {
        val verdict = NativeSecurityVerdict(mask = 0)

        assertTrue(verdict.isAllowed)
        assertEquals("none", verdict.diagnosticSummary())
    }

    @Test
    fun compositeMaskMapsToStableOrderedCodes() {
        val verdict = NativeSecurityVerdict(
            NativeSecurityReason.PACKAGE_MISMATCH.bit or
                NativeSecurityReason.TRACER_DETECTED.bit or
                NativeSecurityReason.HOOK_ARTIFACT_DETECTED.bit,
        )

        assertFalse(verdict.isAllowed)
        assertEquals(
            listOf("package_mismatch", "tracer_detected", "hook_artifact_detected"),
            verdict.diagnosticCodes(),
        )
    }

    @Test
    fun unknownBitsRemainVisibleForForwardCompatibility() {
        val verdict = NativeSecurityVerdict(mask = 1 shl 20)

        assertEquals(listOf("unknown_0x100000"), verdict.diagnosticCodes())
    }
}
