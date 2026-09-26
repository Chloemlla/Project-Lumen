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
            remove("kind")
            remove("durationMillis")
            put("authorName", "tampered")
            put("authorUrl", "https://example.invalid")
            put("authorFingerprint", "tampered")
        }

        val restored = crashReportFromJson(json)

        assertEquals(sampleReport().crashedAtMillis.toString().takeLast(12), restored.reportId)
        assertEquals("unknown", restored.threadName)
        assertEquals("unknown", restored.processName)
        assertTrue(restored.recentEvents.isEmpty())
        assertEquals(CrashReportKind.CRASH, restored.kind)
        assertEquals(0L, restored.durationMillis)
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

    @Test
    fun storeFallsBackToInternalStorageWhenExternalIsUnwritable() {
        val root = Files.createTempDirectory("lumen-crash-store").toFile()
        try {
            val external = File(root, "external/lumen-crash/crash_report.json")
            val internal = File(root, "internal/crash_report.json")
            val store = CrashReportStore(
                externalTargets = listOf(external),
                legacyPrivateTargets = listOf(internal),
            )
            // Block the external target by placing a regular file where its directory must go.
            val blocker = File(root, "external").apply { writeText("not a directory") }

            store.save(sampleReport())

            assertFalse(external.exists())
            assertTrue(internal.isFile)
            assertEquals(sampleReport(), store.load())

            // External becomes writable again: the next save migrates out and drops the internal copy.
            assertTrue(blocker.delete())
            store.save(sampleReport())
            assertTrue(external.isFile)
            assertFalse(internal.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun priorExitReportRoundTripsThroughJsonWithExitReason() {
        val report = CrashReport(
            reportId = "a1b2c3d4e5f6",
            crashedAtMillis = 1_753_960_844_732L,
            crashedAtText = "2026-07-31 18:20:44.732",
            exceptionType = "REASON_CRASH_NATIVE",
            rootCause = "SIGSEGV",
            threadName = "previous-process",
            processName = "com.chloemlla.projectlumen",
            systemInfo = "Android: 16 (SDK 36)\nABI: arm64-v8a",
            stackTrace = "backtrace",
            kind = CrashReportKind.PRIOR_EXIT,
            exitReason = "REASON_CRASH_NATIVE / SIGSEGV",
        )

        assertEquals(report, crashReportFromJson(report.toJson()))
        assertEquals("REASON_CRASH_NATIVE / SIGSEGV", report.toJson().getString("exitReason"))
    }

    @Test
    fun nonFatalReportRoundTripsAndLabelsItselfAsHandled() {
        val report = sampleReport().copy(kind = CrashReportKind.NON_FATAL, durationMillis = 0L)

        assertEquals(report, crashReportFromJson(report.toJson()))
        assertEquals("non_fatal", report.toJson().getString("kind"))
        assertTrue(report.toClipboardText().contains("Report type: non_fatal"))
    }

    @Test
    fun processExitReasonNamesMatchApplicationExitInfoConstants() {
        // Values from the Android reference for ApplicationExitInfo. The previous expectations
        // (13 -> REASON_CRASH_NATIVE, 10 -> REASON_ANR) encoded the off-by-one table and hid it.
        val expected = mapOf(
            0 to "REASON_UNKNOWN",
            1 to "REASON_EXIT_SELF",
            2 to "REASON_SIGNALED",
            3 to "REASON_LOW_MEMORY",
            4 to "REASON_CRASH",
            5 to "REASON_CRASH_NATIVE",
            6 to "REASON_ANR",
            7 to "REASON_INITIALIZATION_FAILURE",
            8 to "REASON_PERMISSION_CHANGE",
            9 to "REASON_EXCESSIVE_RESOURCE_USAGE",
            10 to "REASON_USER_REQUESTED",
            11 to "REASON_USER_STOPPED",
            12 to "REASON_DEPENDENCY_DIED",
            13 to "REASON_OTHER",
            14 to "REASON_FREEZER",
            15 to "REASON_PACKAGE_STATE_CHANGE",
            16 to "REASON_PACKAGE_UPDATED",
            17 to "REASON_MEMORY_LIMITER",
            18 to "REASON_ANOMALY",
        )
        expected.forEach { (code, name) ->
            assertEquals("reason $code", name, processExitReasonName(code))
        }
        assertEquals("UNKNOWN(99)", processExitReasonName(99))
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
            kind = CrashReportKind.ANR,
            durationMillis = 6_500L,
        )
    }
}
