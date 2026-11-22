package com.chloemlla.lumen.crash

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-logic tests for the crash-attribution layer.
 *
 * These run on the JVM without an Android runtime on purpose: the rules that decide "this was a
 * Rust abort / a WebView renderer death / a JVM crash" are exactly the rules that must not depend
 * on a device to be verified.
 */
class CrashAnalysisTest {
    // ── CrashComponentClassifier ──────────────────────────────────────────────

    @Test
    fun rendererProcessIsAttributedBeforeExceptionType() {
        val component = CrashComponentClassifier.classify(
            kind = CrashReportKind.PRIOR_EXIT,
            processName = "com.example.app:sandboxed_process0",
            exceptionType = "REASON_CRASH",
            stackTrace = "java.lang.RuntimeException: renderer gone",
        )
        assertEquals(CrashComponent.WEBVIEW_RENDERER, component)
    }

    @Test
    fun privilegedProcessCountsAsRenderer() {
        val component = CrashComponentClassifier.classify(
            kind = CrashReportKind.PRIOR_EXIT,
            processName = "com.example.app:privileged_process0",
            exceptionType = "REASON_CRASH_NATIVE",
            stackTrace = "",
        )
        assertEquals(CrashComponent.WEBVIEW_RENDERER, component)
    }

    @Test
    fun watchdogKindsAreAnr() {
        listOf(CrashReportKind.ANR, CrashReportKind.STARTUP_HANG, CrashReportKind.FREEZE)
            .forEach { kind ->
                assertEquals(
                    "kind=${kind.wireValue}",
                    CrashComponent.ANR,
                    CrashComponentClassifier.classify(kind, "com.example.app", "ANR", ""),
                )
            }
    }

    @Test
    fun externalKindIsAttributedToHost() {
        assertEquals(
            CrashComponent.HOST_EXTERNAL,
            CrashComponentClassifier.classify(
                CrashReportKind.EXTERNAL,
                "com.example.app",
                "ExternalFailure",
                "TypeError: undefined is not a function",
            ),
        )
    }

    @Test
    fun rustSymbolMarkersIdentifyRust() {
        val trace = """
            #00 pc 0000000000041f2c /data/app/~~x/base.apk!/lib/arm64-v8a/libtauri_app_lib.so
            rust_begin_unwind
            core::panicking::panic_fmt
        """.trimIndent()
        assertEquals(
            CrashComponent.RUST_NATIVE,
            CrashComponentClassifier.classify(
                CrashReportKind.PRIOR_EXIT,
                "com.example.app",
                "REASON_CRASH_NATIVE",
                trace,
            ),
        )
    }

    @Test
    fun tauriLibraryNameAloneIdentifiesRust() {
        assertEquals(
            CrashComponent.RUST_NATIVE,
            CrashComponentClassifier.classify(
                CrashReportKind.CRASH,
                "com.example.app",
                "java.lang.UnsatisfiedLinkError",
                "dlopen failed: library \"libapp_lib.so\" not found",
            ),
        )
    }

    @Test
    fun tombstoneFramesWithoutRustMarkersAreNative() {
        val trace = "#01 pc 0000000000012345 /data/app/lib/arm64-v8a/libsqlite3.so (offset 0x1234)"
        assertEquals(
            CrashComponent.NATIVE,
            CrashComponentClassifier.classify(
                CrashReportKind.PRIOR_EXIT,
                "com.example.app",
                "REASON_CRASH_NATIVE",
                trace,
            ),
        )
    }

    @Test
    fun webViewFramesAreWebView() {
        assertEquals(
            CrashComponent.WEBVIEW,
            CrashComponentClassifier.classify(
                CrashReportKind.CRASH,
                "com.example.app",
                "java.lang.RuntimeException",
                "at android.webkit.WebViewClassic.handleMessage(WebViewClassic.java:1)",
            ),
        )
    }

    @Test
    fun plainThrowableIsJava() {
        assertEquals(
            CrashComponent.JAVA,
            CrashComponentClassifier.classify(
                CrashReportKind.CRASH,
                "com.example.app",
                "java.lang.IllegalStateException",
                "at com.example.app.MainActivity.onCreate(MainActivity.kt:12)",
            ),
        )
    }

    // ── ExitSignals ───────────────────────────────────────────────────────────

    @Test
    fun abortSignalIsNamed() {
        assertEquals("SIGABRT", ExitSignals.name(6))
        assertEquals("SIGABRT (6)", ExitSignals.describe(6))
        assertEquals("SIGSEGV (11)", ExitSignals.describe(11))
        assertEquals("SIGNAL_40", ExitSignals.name(40))
        assertNull(ExitSignals.describe(0))
        assertNull(ExitSignals.describe(-1))
        assertNull(ExitSignals.describe(200))
    }

    // ── NativeLibraries ───────────────────────────────────────────────────────

    @Test
    fun libraryExtractionIsOrderedDeduplicatedAndBounded() {
        val trace = """
            #00 /data/app/lib/arm64-v8a/libapp_lib.so (BuildId: abc)
            #01 /data/app/lib/arm64-v8a/libapp_lib.so (BuildId: abc)
            #02 /data/app/lib/arm64-v8a/libc++_shared.so
        """.trimIndent()
        assertEquals(listOf("libapp_lib.so", "libc++_shared.so"), NativeLibraries.extract(trace))
        assertEquals(emptyList<String>(), NativeLibraries.extract(""))
        assertEquals(emptyList<String>(), NativeLibraries.extract("liba.so libb.so", limit = 0))
        assertEquals(listOf("liba.so"), NativeLibraries.extract("liba.so libb.so", limit = 1))
    }

    // ── PriorExitClassification ───────────────────────────────────────────────

    @Test
    fun mainProcessDetection() {
        assertTrue(PriorExitClassification.isMainProcess("com.example.app", "com.example.app"))
        assertTrue(PriorExitClassification.isMainProcess("com.example.app", ""))
        assertTrue(!PriorExitClassification.isMainProcess("com.example.app", "com.example.app:push"))
        assertTrue(
            PriorExitClassification.isWebViewRenderer("com.example.app:sandboxed_process0"),
        )
    }

    // ── CrashReportKind ───────────────────────────────────────────────────────

    @Test
    fun externalKindWireValueRoundTrips() {
        assertEquals(CrashReportKind.EXTERNAL, CrashReportKind.fromWireValue("external"))
        assertEquals("external", CrashReportKind.EXTERNAL.wireValue)
        assertTrue(!CrashReportKind.NON_FATAL.isWatchdogKind())
        assertTrue(CrashReportKind.ANR.isWatchdogKind())
    }

    // ── Report JSON ───────────────────────────────────────────────────────────

    @Test
    fun componentFieldsSurviveJsonRoundTrip() {
        val report = sampleReport(
            component = CrashComponent.RUST_NATIVE,
            exitStatus = 6,
            nativeLibraries = listOf("libapp_lib.so"),
            externalSource = "rust.panic",
        )
        val restored = crashReportFromJson(report.toJson())
        assertEquals(CrashComponent.RUST_NATIVE, restored.component)
        assertEquals(6, restored.exitStatus)
        assertEquals(listOf("libapp_lib.so"), restored.nativeLibraries)
        assertEquals("rust.panic", restored.externalSource)
        assertEquals(CrashReportKind.EXTERNAL, restored.kind)
    }

    @Test
    fun olderReportsWithoutNewFieldsStillParse() {
        val legacy = JSONObject(
            """
            {
              "reportId": "0123456789ab",
              "crashedAtMillis": 1700000000000,
              "crashedAtText": "2023-11-14 22:13:20.000",
              "exceptionType": "java.lang.IllegalStateException",
              "rootCause": "boom",
              "threadName": "main",
              "processName": "com.example.app",
              "systemInfo": "App: demo",
              "stackTrace": "stack"
            }
            """.trimIndent(),
        )
        val restored = crashReportFromJson(legacy)
        assertEquals(CrashComponent.UNKNOWN, restored.component)
        assertEquals(0, restored.exitStatus)
        assertEquals(emptyList<String>(), restored.nativeLibraries)
        assertNull(restored.externalSource)
    }

    @Test
    fun clipboardTextMentionsComponentAndSignal() {
        val report = sampleReport(
            component = CrashComponent.RUST_NATIVE,
            exitStatus = 6,
            nativeLibraries = listOf("libapp_lib.so"),
        )
        val text = report.toClipboardText()
        assertTrue(text.contains("Component: rust_native"))
        assertTrue(text.contains("Exit status: SIGABRT (6)"))
        assertTrue(text.contains("Native libraries: libapp_lib.so"))
    }

    // ── CrashExternalRecordParser ─────────────────────────────────────────────

    @Test
    fun parsesJsonRecord() {
        val record = CrashExternalRecordParser.parse(
            "panic.json",
            """
            {
              "source": "rust.panic",
              "message": "attempt to subtract with overflow",
              "stackTrace": "0: app_lib::main\n1: core::ops",
              "timestampMillis": 1700000000123,
              "thread": "tokio-runtime-worker"
            }
            """.trimIndent(),
        )
        assertEquals("rust.panic", record?.source)
        assertEquals("attempt to subtract with overflow", record?.message)
        assertEquals("0: app_lib::main\n1: core::ops", record?.stackTrace)
        assertEquals(1700000000123L, record?.occurredAtMillis)
        assertEquals("tokio-runtime-worker", record?.threadName)
    }

    @Test
    fun parsesJsonWithoutSourceOrTimestamp() {
        val record = CrashExternalRecordParser.parse("x.json", """{"msg":"Failed to fetch"}""")
        assertEquals("host-inbox", record?.source)
        assertEquals("Failed to fetch", record?.message)
        assertEquals(0L, record?.occurredAtMillis)
        assertNull(record?.stackTrace)
    }

    @Test
    fun parsesPlainTextRecord() {
        val record = CrashExternalRecordParser.parse(
            "crash.log",
            "FATAL EXCEPTION: main\ncom.example.NativeCrash\n\tat com.example.Main.run(Main.kt:8)",
        )
        assertEquals("native-log", record?.source)
        assertEquals("FATAL EXCEPTION: main", record?.message)
        assertTrue(record?.stackTrace?.contains("Main.kt:8") == true)
    }

    @Test
    fun rejectsEmptyAndMalformedInput() {
        assertNull(CrashExternalRecordParser.parse("empty.json", "   "))
        assertNull(CrashExternalRecordParser.parse("broken.json", "{not json"))
        assertNull(CrashExternalRecordParser.parse("missing.json", """{"stack":"only a stack"}"""))
    }

    // ── CrashReport factories ────────────────────────────────────────────────
    //
    // CrashReport.fromExternal()/fromThrowable() read Build.* and the process name, so they need a
    // device (the JVM android.jar stubs throw). Their attribution logic lives in
    // CrashComponentClassifier/NativeLibraries, which are covered above.

    private fun sampleReport(
        component: CrashComponent,
        exitStatus: Int,
        nativeLibraries: List<String>,
        externalSource: String? = "rust.panic",
    ) = CrashReport(
        reportId = "0123456789ab",
        crashedAtMillis = 1700000000000,
        crashedAtText = "2023-11-14 22:13:20.000",
        exceptionType = "ExternalFailure",
        rootCause = "attempt to subtract with overflow",
        threadName = "tokio-runtime-worker",
        processName = "com.example.app",
        systemInfo = "App: demo",
        stackTrace = "0: app_lib::main",
        recentEvents = emptyList(),
        kind = CrashReportKind.EXTERNAL,
        component = component,
        exitStatus = exitStatus,
        nativeLibraries = nativeLibraries,
        externalSource = externalSource,
    )
}
