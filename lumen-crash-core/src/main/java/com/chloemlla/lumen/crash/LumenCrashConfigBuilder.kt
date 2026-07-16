package com.chloemlla.lumen.crash

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build

/**
 * Builder for the short install path.
 *
 * Auto-fills app display name / version metadata from [PackageManager] unless overridden.
 * Author attribution remains non-configurable.
 */
class LumenCrashConfigBuilder internal constructor(
    private val application: Application,
) {
    var appDisplayName: String? = null
    var versionName: String? = null
    var versionCode: Int? = null
    var commitHash: String = "unknown"
    var fileProviderAuthority: String? = LumenCrashDefaults.fileProviderAuthority(application.packageName)
    var shareSubject: String? = null
    var reportTitle: String? = null
    var reportMessage: String? = null
    var pasteUploadEnabled: Boolean = true
    var pasteUploadBaseUrl: String = CrashReportPasteUploader.DEFAULT_BASE_URL
    var onCrashSaved: ((CrashReport) -> Unit)? = null
    var killProcessWhenNoPreviousHandler: Boolean = true

    fun build(): LumenCrashConfig {
        val packageInfo = runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                application.packageManager.getPackageInfo(
                    application.packageName,
                    PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                application.packageManager.getPackageInfo(application.packageName, 0)
            }
        }.getOrNull()

        val resolvedDisplayName = appDisplayName?.takeIf { it.isNotBlank() }
            ?: resolveAppLabel()
            ?: application.packageName
        val resolvedVersionName = versionName?.takeIf { it.isNotBlank() }
            ?: packageInfo?.versionName?.takeIf { it.isNotBlank() }
            ?: "unknown"
        val resolvedVersionCode = versionCode ?: packageInfo?.longVersionCodeCompat()?.toInt() ?: 0

        return LumenCrashConfig(
            appDisplayName = resolvedDisplayName,
            versionName = resolvedVersionName,
            versionCode = resolvedVersionCode,
            commitHash = commitHash.ifBlank { "unknown" },
            fileProviderAuthority = fileProviderAuthority?.takeIf { it.isNotBlank() }
                ?: LumenCrashDefaults.fileProviderAuthority(application.packageName),
            shareSubject = shareSubject,
            reportTitle = reportTitle,
            reportMessage = reportMessage,
            pasteUploadEnabled = pasteUploadEnabled,
            pasteUploadBaseUrl = pasteUploadBaseUrl,
            onCrashSaved = onCrashSaved,
            killProcessWhenNoPreviousHandler = killProcessWhenNoPreviousHandler,
        )
    }

    private fun resolveAppLabel(): String? {
        val appInfo: ApplicationInfo = application.applicationInfo
        val label = application.packageManager.getApplicationLabel(appInfo)
        return label?.toString()?.takeIf { it.isNotBlank() }
    }
}

private fun android.content.pm.PackageInfo.longVersionCodeCompat(): Long {
    return if (Build.VERSION.SDK_INT >= 28) {
        longVersionCode
    } else {
        @Suppress("DEPRECATION")
        versionCode.toLong()
    }
}
