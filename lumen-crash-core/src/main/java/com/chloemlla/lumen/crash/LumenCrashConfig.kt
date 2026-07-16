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
    val onCrashSaved: ((CrashReport) -> Unit)? = null,
    val killProcessWhenNoPreviousHandler: Boolean = true,
)

data class CrashAppInfo(
    val appDisplayName: String,
    val versionName: String,
    val versionCode: Int,
    val commitHash: String,
)
