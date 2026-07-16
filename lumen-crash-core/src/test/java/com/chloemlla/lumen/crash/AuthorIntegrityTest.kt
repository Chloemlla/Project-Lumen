package com.chloemlla.lumen.crash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthorIntegrityTest {
    @Test
    fun fingerprintMatchesConstant() {
        assertEquals(CrashAuthorAttribution.FINGERPRINT_HEX, AuthorIntegrity.fingerprintHex())
    }

    @Test
    fun verifyOrThrowSucceeds() {
        AuthorIntegrity.verifyOrThrow("unit-test")
    }

    @Test
    fun clipboardTextIncludesAuthorAttribution() {
        AuthorIntegrity.verifyOrThrow("unit-test-clipboard")
        val report = CrashReport(
            reportId = "abc123def456",
            crashedAtMillis = 1L,
            crashedAtText = "2026-01-01 00:00:00.000",
            exceptionType = "java.lang.IllegalStateException",
            rootCause = "boom",
            threadName = "main",
            processName = "test",
            systemInfo = "App: test",
            stackTrace = "stack",
            recentEvents = emptyList(),
        )
        val text = report.toClipboardText()
        assertTrue(text.contains("Chloemlla"))
        assertTrue(text.contains("https://github.com/Chloemlla/"))
        assertTrue(text.contains(CrashAuthorAttribution.FINGERPRINT_HEX))
    }
}
