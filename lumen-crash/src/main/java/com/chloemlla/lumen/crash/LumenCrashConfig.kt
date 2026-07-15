package com.chloemlla.lumen.crash

/**
 * Host-provided configuration for the crash SDK.
 *
 * Business metadata and product copy are injectable. Author attribution is **not**
 * configurable and is always forced to ChloeMlla / https://github.com/Chloemlla/.
 */
data class LumenCrashConfig(
    val appDisplayName: String,
    val versionName: String,
    val versionCode: Int,
    val commitHash: String = "unknown",
    val fileProviderAuthority: String? = null,
    val shareSubject: String? = null,
    val reportTitle: String? = null,
    val reportMessage: String? = null,
    val onCrashSaved: ((CrashReport) -> Unit)? = null,
    val killProcessWhenNoPreviousHandler: Boolean = true,
)

data class CrashAppInfo(
    val appDisplayName: String,
    val versionName: String,
    val versionCode: Int,
    val commitHash: String,
)
