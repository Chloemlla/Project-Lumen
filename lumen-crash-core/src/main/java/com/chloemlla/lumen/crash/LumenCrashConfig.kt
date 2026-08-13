package com.chloemlla.lumen.crash

/**
 * Host-provided configuration for the crash SDK.
 *
 * Business metadata and product copy are injectable. Author attribution is **not**
 * configurable and is always forced to Chloemlla / https://github.com/Chloemlla/.
 */
data class LumenCrashConfig(
    val appDisplayName: String,
    val versionName: String,
    val versionCode: Int,
    val commitHash: String = "unknown",
    /**
     * Optional override for share-as-file.
     *
     * When null/blank, the SDK uses its bundled provider:
     * `${applicationId}.lumen.crash.fileprovider`.
     */
    val fileProviderAuthority: String? = null,
    val shareSubject: String? = null,
    val reportTitle: String? = null,
    val reportMessage: String? = null,
    /**
     * When true, the crash UI can upload the report text to a LogPaste endpoint and
     * surface a shareable HTTPS link (default: https://paste.gentoo.zip).
     */
    val pasteUploadEnabled: Boolean = true,
    /** HTTPS base URL for LogPaste-compatible upload. Trailing slashes are ignored. */
    val pasteUploadBaseUrl: String = CrashReportPasteUploader.DEFAULT_BASE_URL,
    /** Legacy callback invoked for both crash and watchdog reports after persistence. */
    val onCrashSaved: ((CrashReport) -> Unit)? = null,
    val killProcessWhenNoPreviousHandler: Boolean = true,
    /** Detects a main looper that stops processing heartbeat callbacks. */
    val anrWatchdogEnabled: Boolean = true,
    /** How long the main looper may be silent before a freeze report is persisted. */
    val anrWatchdogTimeoutMillis: Long = 5_000L,
    /** How often the watchdog checks the heartbeat and startup deadline. */
    val anrWatchdogCheckIntervalMillis: Long = 1_000L,
    /** Detects hosts that never report their first rendered frame. */
    val startupHangWatchdogEnabled: Boolean = false,
    /** Maximum time from [LumenCrash.install] to [LumenCrash.markStartupComplete]. */
    val startupHangTimeoutMillis: Long = 15_000L,
    /** Receives every report after at least one persistence target accepted it. */
    val onReportSaved: ((CrashReport) -> Unit)? = null,
    /** Receives synthetic startup-hang and main-thread freeze reports. */
    val onAnrDetected: ((CrashReport) -> Unit)? = null,
    /**
     * Collects the previous process's unexpected exit (native crash / signal /
     * system ANR) via ApplicationExitInfo (API 30+) at install time and persists
     * a PRIOR_EXIT report. Requires Android 11+. Defaults to true.
     */
    val priorExitCaptureEnabled: Boolean = true,

    // ── Backend crash-report upload ──────────────────────────────────────────

    /**
     * Master switch for the built-in unconditional backend crash upload.
     *
     * When `true` (default), every persisted report is also POSTed to the
     * crash-report backend. The upload is best-effort and never blocks the
     * crash/main thread.
     */
    val crashReportBackendEnabled: Boolean = true,

    /**
     * HTTPS base URL for the crash-report endpoint.
     *
     * The full endpoint path is
     * `{baseUrl}${LumenCrashDefaults.DEFAULT_CRASH_BACKEND_ENDPOINT_PATH}`.
     * Must use HTTPS. Defaults to [LumenCrashDefaults.DEFAULT_CRASH_BACKEND_BASE_URL].
     */
    val crashReportBackendBaseUrl: String = LumenCrashDefaults.DEFAULT_CRASH_BACKEND_BASE_URL,

    /**
     * Optional Bearer token for the anonymous crash-report endpoint.
     *
     * When null or blank the SDK uploads without an Authorization header; the
     * endpoint accepts anonymous posts. Configure it only when the backend
     * requires a shared token.
     */
    val crashReportAccessToken: String? = null,

    /**
     * Supplier for the device installation ID, evaluated at upload time.
     *
     * When null, the SDK persists its own per-install device ID (a UUID in
     * SharedPreferences). The lambda is invoked on a background executor so it
     * may safely read from MMKV / encrypted stores.
     */
    val deviceInstallationIdProvider: (() -> String?)? = null,
)

data class CrashAppInfo(
    val appDisplayName: String,
    val versionName: String,
    val versionCode: Int,
    val commitHash: String,
)
