package com.chloemlla.lumen.crash

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportPersistenceTest {
    @Test
    fun jsonRoundTripPreservesReportFieldsAndEvents() {
        val report = sampleReport()

        assertEquals(report, crashReportFromJson(report.toJson()))
    }

    @Test
    fun legacyJsonUsesSafeDefaultsAndVerifiedAuthorAttribution() {
        val json = sampleReport().toJson().apply {
            remove("reportId")
            remove("threadName")
            remove("processName")
            remove("recentEvents")
            put("authorName", "tampered")
            put("authorUrl", "https://example.invalid")
            put("authorFingerprint", "tampered")
        }

        val restored = crashReportFromJson(json)

        assertEquals(sampleReport().crashedAtMillis.toString().takeLast(12), restored.reportId)
        assertEquals("unknown", restored.threadName)
        assertEquals("unknown", restored.processName)
        assertTrue(restored.recentEvents.isEmpty())
        assertEquals(CrashAuthorAttribution.AUTHOR_NAME, restored.authorName)
        assertEquals(CrashAuthorAttribution.AUTHOR_URL, restored.authorUrl)
        assertEquals(CrashAuthorAttribution.FINGERPRINT_HEX, restored.authorFingerprint)
    }

    @Test
    fun storeSavesLoadsClearsAndRemovesLegacyCopies() = withTemporaryStore { root, store ->
        val report = sampleReport()
        val legacy = File(root, "legacy/crash_report.json").apply {
            parentFile.mkdirs()
            writeText("stale", Charsets.UTF_8)
        }

        store.save(report)

        assertFalse(legacy.exists())
        assertTrue(File(root, "external-files/lumen-crash/crash_report.json").isFile)
        assertTrue(File(root, "external-cache/lumen-crash/crash_report.json").isFile)
        assertEquals(report, store.load())
        store.clear()
        assertNull(store.load())
    }

    @Test
    fun storeMigratesLegacyReportToExternalLocation() {
        val root = Files.createTempDirectory("lumen-crash-store").toFile()
        try {
            val external = File(root, "external/crash_report.json")
            val legacy = File(root, "legacy/crash_report.json").apply {
                parentFile.mkdirs()
                writeText(sampleReport().toJson().toString(), Charsets.UTF_8)
            }
            val store = CrashReportStore(
                externalTargets = listOf(external),
                legacyPrivateTargets = listOf(legacy),
            )

            assertEquals(sampleReport(), store.load())
            assertTrue(external.isFile)
            assertFalse(legacy.exists())
            assertNotNull(store.load())
        } finally {
            root.deleteRecursively()
        }
    }

    private fun withTemporaryStore(block: (File, CrashReportStore) -> Unit) {
        val root = Files.createTempDirectory("lumen-crash-store").toFile()
        try {
            val store = CrashReportStore(
                externalTargets = listOf(
                    File(root, "external-files/lumen-crash/crash_report.json"),
                    File(root, "external-cache/lumen-crash/crash_report.json"),
                ),
                legacyPrivateTargets = listOf(File(root, "legacy/crash_report.json")),
            )
            block(root, store)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun sampleReport(): CrashReport {
        return CrashReport(
            reportId = "86949b7f7eb3",
            crashedAtMillis = 1_753_960_844_732L,
            crashedAtText = "2026-07-31 18:20:44.732",
            exceptionType = "android.app.ForegroundServiceStartNotAllowedException",
            rootCause = "startForegroundService() not allowed",
            threadName = "DefaultDispatcher-worker-4",
            processName = "com.chloemlla.projectlumen",
            systemInfo = "Android: 16 (SDK 36)\nABI: arm64-v8a",
            stackTrace = "android.app.ForegroundServiceStartNotAllowedException\n\tat example.Frame.call(Frame.kt:1)",
            recentEvents = listOf("Application.onCreate", "MMKV initialized", "包含中文"),
        )
    }
}
