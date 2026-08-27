package com.chloemlla.lumen.crash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportBackendUploaderTest {
    @Test
    fun normalizeBaseUrlTrimsSlashAndRequiresHttps() {
        assertEquals(
            "https://tts.chloemlla.com",
            CrashReportBackendUploader.normalizeBaseUrl("  https://tts.chloemlla.com/  "),
        )
        assertTrue(
            runCatching {
                CrashReportBackendUploader.normalizeBaseUrl("http://tts.chloemlla.com")
            }.isFailure,
        )
    }

    @Test
    fun acceptedResponsesResolveToAccepted() {
        assertEquals(
            CrashUploadOutcome.ACCEPTED,
            CrashReportBackendUploader.classifyResponse(200, """{"accepted":true,"duplicate":false}"""),
        )
        // Idempotent re-upload of an already stored report.
        assertEquals(
            CrashUploadOutcome.ACCEPTED,
            CrashReportBackendUploader.classifyResponse(200, """{"accepted":true,"duplicate":true}"""),
        )
        // Missing or unparseable body still means the server took it.
        assertEquals(CrashUploadOutcome.ACCEPTED, CrashReportBackendUploader.classifyResponse(200, ""))
        assertEquals(CrashUploadOutcome.ACCEPTED, CrashReportBackendUploader.classifyResponse(204, "<html>"))
    }

    @Test
    fun quotaAndServerFailuresStayRetryable() {
        assertEquals(
            CrashUploadOutcome.RETRYABLE,
            CrashReportBackendUploader.classifyResponse(200, """{"accepted":false}"""),
        )
        assertEquals(CrashUploadOutcome.RETRYABLE, CrashReportBackendUploader.classifyResponse(429, ""))
        assertEquals(CrashUploadOutcome.RETRYABLE, CrashReportBackendUploader.classifyResponse(408, ""))
        assertEquals(CrashUploadOutcome.RETRYABLE, CrashReportBackendUploader.classifyResponse(503, ""))
    }

    @Test
    fun rejectedPayloadsAreNotRetried() {
        assertEquals(
            CrashUploadOutcome.REJECTED,
            CrashReportBackendUploader.classifyResponse(400, """{"accepted":false,"error":"reportId is required"}"""),
        )
        assertEquals(CrashUploadOutcome.REJECTED, CrashReportBackendUploader.classifyResponse(401, ""))
        assertEquals(CrashUploadOutcome.REJECTED, CrashReportBackendUploader.classifyResponse(404, ""))
    }

    @Test
    fun requestBodyCarriesIngestIdentityFields() {
        val body = CrashReportBackendUploader.buildRequestBody(
            report = sampleReport(),
            deviceInstallationId = "device-42",
            packageName = "com.example.host",
            versionCode = 1042,
        )

        assertEquals("device-42", body.getString("deviceInstallationId"))
        assertEquals("com.example.host", body.getString("packageName"))
        assertEquals(1042, body.getInt("versionCode"))
        assertEquals("crash", body.getString("kind"))
    }

    @Test
    fun exitReasonIsFoldedIntoUploadedSystemInfo() {
        val report = sampleReport().copy(
            kind = CrashReportKind.PRIOR_EXIT,
            exitReason = "REASON_LOW_MEMORY / killed by lmkd",
        )

        val systemInfo = CrashReportBackendUploader
            .buildRequestBody(report, "device-42", "com.example.host", 1042)
            .getString("systemInfo")

        assertTrue(systemInfo.startsWith("Exit reason: REASON_LOW_MEMORY / killed by lmkd\n"))
        assertTrue(systemInfo.endsWith(report.systemInfo))
        // The locally stored report keeps its untouched system info.
        assertEquals("Device: test", report.systemInfo)
    }

    @Test
    fun reportsWithoutExitReasonKeepSystemInfoUnchanged() {
        val report = sampleReport()

        assertEquals(
            report.systemInfo,
            CrashReportBackendUploader
                .buildRequestBody(report, "device-42", "com.example.host", 1042)
                .getString("systemInfo"),
        )
    }

    private fun sampleReport(): CrashReport = CrashReport(
        reportId = "0123456789ab",
        crashedAtMillis = 1_700_000_000_000L,
        crashedAtText = "2023-11-15 06:13:20.000",
        exceptionType = "java.lang.IllegalStateException",
        rootCause = "boom",
        threadName = "main",
        processName = "com.example.host",
        systemInfo = "Device: test",
        stackTrace = "java.lang.IllegalStateException: boom",
        recentEvents = listOf("event-1"),
    )
}
