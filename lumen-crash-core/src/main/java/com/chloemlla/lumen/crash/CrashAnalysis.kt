package com.chloemlla.lumen.crash

/**
 * Which runtime produced the failure.
 *
 * A crash report that only says "the process died" is nearly useless on Android: the same
 * full-screen report can come from a Java exception, a WebView renderer process death, a Rust
 * panic that aborted, or a system ANR. Hosts that embed a WebView shell (Tauri / Capacitor /
 * plain `WebView`) need this distinction to route the report to the right person.
 *
 * Wire values are stable because a report can be read by a newer SDK than the one that wrote it.
 */
enum class CrashComponent(val wireValue: String) {
    /** Rust code in `lib*_lib.so` (Tauri/Wry) - panics, aborts, and Rust-side aborts. */
    RUST_NATIVE("rust_native"),

    /** Other native code: JNI libraries, C/C++ plugins, or an unsymbolized tombstone. */
    NATIVE("native"),

    /** The WebView *renderer* process (`:sandboxed_process0`) died; the app usually survived. */
    WEBVIEW_RENDERER("webview_renderer"),

    /** Java/Kotlin frames inside the WebView glue (`android.webkit.*`, Chromium). */
    WEBVIEW("webview"),

    /** Plain Java/Kotlin crash on the app's own thread. */
    JAVA("java"),

    /** Main thread unresponsive: ANR, startup hang, or a recovered freeze. */
    ANR("anr"),

    /** Supplied by the host (JS global handler, Rust panic hook inbox, Flutter bridge). */
    HOST_EXTERNAL("host_external"),

    UNKNOWN("unknown");

    companion object {
        fun fromWireValue(value: String?): CrashComponent =
            values().firstOrNull { it.wireValue == value } ?: UNKNOWN
    }
}

/**
 * Derives [CrashComponent] from evidence the SDK already has: the report kind, the process that
 * died, and the stack/tombstone text.
 *
 * Deliberately a pure function so it can be unit-tested without an Android runtime, and so the
 * rules stay in one place instead of being duplicated in every `CrashReport.from*` factory.
 */
internal object CrashComponentClassifier {
    /** `:sandboxed_process0` (renderer), `:privileged_process0` (privileged WebView), OEM variants. */
    private val webViewProcessMarkers = listOf(
        ":sandboxed_process",
        ":privileged_process",
        ":webview",
        ":chromium",
    )

    /**
     * Native libraries that can only come from Rust toolchains: Tauri's `lib<app>_lib.so`, wry/tao,
     * and any `librustc`/`libstd-*` artifact that leaked into the APK.
     */
    private val rustLibraryRegex = Regex(
        """lib[A-Za-z0-9_.+-]*(?:_lib|rust|tauri|wry|tao)[A-Za-z0-9_.+-]*\.so""",
        RegexOption.IGNORE_CASE,
    )

    /** Rust symbol fragments that survive into a stripped release tombstone. */
    private val rustSymbolMarkers = listOf(
        "rust_begin_unwind",
        "rust_panic",
        "core::panicking",
        "std::panicking",
        "core::result::unwrap",
        "core::option::unwrap",
        "cargo/registry",
        "RUST_BACKTRACE",
        "_RNv",
        "_ZN4core",
    )

    /** A native frame from a tombstone (`pc`, `offset`, or a `BuildId` annotation). */
    private val nativeFrameRegex = Regex(
        """\.so\b[^\n]*(?:\(offset|\(BuildId|\+0x|pc [0-9a-fA-F]{8,})""",
    )

    private val webViewFrameRegex = Regex(
        """(?:android\.webkit\.|android\.webkit\.WebView|org\.chromium\.|com\.android\.org\.chromium|com\.google\.android\.webview|chromium_android)""",
    )

    fun classify(
        kind: CrashReportKind,
        processName: String,
        exceptionType: String,
        stackTrace: String,
    ): CrashComponent {
        val process = processName.lowercase()
        if (webViewProcessMarkers.any { process.contains(it) }) {
            return CrashComponent.WEBVIEW_RENDERER
        }
        if (kind == CrashReportKind.EXTERNAL) return CrashComponent.HOST_EXTERNAL
        if (kind == CrashReportKind.ANR ||
            kind == CrashReportKind.STARTUP_HANG ||
            kind == CrashReportKind.FREEZE
        ) {
            return CrashComponent.ANR
        }
        if (rustSymbolMarkers.any { stackTrace.contains(it, ignoreCase = true) } ||
            rustLibraryRegex.containsMatchIn(stackTrace)
        ) {
            return CrashComponent.RUST_NATIVE
        }
        // REASON_CRASH_NATIVE / REASON_SIGNALED arrive as the exception type of a prior-exit report.
        val nativeReason = exceptionType == "REASON_CRASH_NATIVE" ||
            exceptionType == "REASON_SIGNALED" ||
            exceptionType.startsWith("REASON_CRASH_NATIVE")
        if (nativeReason || nativeFrameRegex.containsMatchIn(stackTrace)) {
            return CrashComponent.NATIVE
        }
        if (webViewFrameRegex.containsMatchIn(stackTrace)) return CrashComponent.WEBVIEW
        return CrashComponent.JAVA
    }
}

/**
 * Signal numbers as `ApplicationExitInfo.status` reports them for a signaled process.
 *
 * `REASON_CRASH_NATIVE` + `SIGABRT (6)` is the fingerprint of a Rust `abort()` (what
 * `tauri::mobile_entry_point` does after catching an unwinding panic), so the number is worth
 * carrying into the report text instead of leaving a bare `REASON_CRASH_NATIVE`.
 */
internal object ExitSignals {
    fun name(signal: Int): String = when (signal) {
        1 -> "SIGHUP"
        2 -> "SIGINT"
        3 -> "SIGQUIT"
        4 -> "SIGILL"
        5 -> "SIGTRAP"
        6 -> "SIGABRT"
        7 -> "SIGBUS"
        8 -> "SIGFPE"
        9 -> "SIGKILL"
        10 -> "SIGUSR1"
        11 -> "SIGSEGV"
        12 -> "SIGUSR2"
        13 -> "SIGPIPE"
        14 -> "SIGALRM"
        15 -> "SIGTERM"
        16 -> "SIGSTKFLT"
        17 -> "SIGCHLD"
        18 -> "SIGCONT"
        19 -> "SIGSTOP"
        20 -> "SIGTSTP"
        21 -> "SIGTTIN"
        22 -> "SIGTTOU"
        23 -> "SIGURG"
        24 -> "SIGXCPU"
        25 -> "SIGXFSZ"
        26 -> "SIGVTALRM"
        27 -> "SIGPROF"
        28 -> "SIGWINCH"
        29 -> "SIGIO"
        30 -> "SIGPWR"
        31 -> "SIGSYS"
        else -> "SIGNAL_$signal"
    }

    /** `SIGABRT (6)`, or null when the exit carried no signal (a plain `exit()` status). */
    fun describe(status: Int): String? {
        if (status <= 0 || status > 64) return null
        return "${name(status)} ($status)"
    }
}

/**
 * Library names present in a stack trace or tombstone.
 *
 * Those names are the first thing symbolization needs: a Rust panic reports raw PCs, and the
 * `base.apk!/lib/<abi>/lib…_lib.so` path plus the tombstone `BuildId` is what maps them back to
 * Cargo's `*.so` with symbols.
 */
internal object NativeLibraries {
    const val DEFAULT_LIMIT = 12

    private val libraryRegex = Regex("""lib[A-Za-z0-9_.+-]+\.so""")

    fun extract(text: String, limit: Int = DEFAULT_LIMIT): List<String> {
        if (text.isEmpty() || limit <= 0) return emptyList()
        val names = LinkedHashSet<String>()
        for (match in libraryRegex.findAll(text)) {
            names.add(match.value)
            if (names.size >= limit) break
        }
        return names.toList()
    }
}

/**
 * Process-role helpers for `ApplicationExitInfo` records.
 *
 * `getHistoricalProcessExitReasons` returns *every* process the package owned, including the
 * WebView renderer (`:sandboxed_process0`). A renderer death is not an app crash: the shell keeps
 * running and Chromium recreates the renderer. Treating it like a main-process crash is what makes
 * a WebView-shell host show a full-screen crash report after a harmless renderer restart.
 */
internal object PriorExitClassification {
    fun isMainProcess(packageName: String, processName: String): Boolean {
        if (processName.isBlank()) return true
        if (processName == packageName) return true
        // No colon means "the package's default process", even if the label differs.
        return !processName.contains(':') && processName.startsWith(packageName)
    }

    fun isWebViewRenderer(processName: String): Boolean {
        val process = processName.lowercase()
        return process.contains(":sandboxed_process") ||
            process.contains(":privileged_process") ||
            process.contains(":webview")
    }
}
