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
 *
 * Every optional field takes its initial value from [LumenCrashConfig] so the two install paths
 * cannot drift apart; only [fileProviderAuthority] deliberately differs, because the short path
 * knows the host package name.
 */
class LumenCrashConfigBuilder internal constructor(
    private val application: Application,
) {
    private val defaults = LumenCrashConfig(appDisplayName = "", versionName = "", versionCode = 0)

    var appDisplayName: String? = null
    var versionName: String? = null
    var versionCode: Int? = null
    var commitHash: String = defaults.commitHash
    var fileProviderAuthority: String? = LumenCrashDefaults.fileProviderAuthority(application.packageName)
    var shareSubject: String? = defaults.shareSubject
    var reportTitle: String? = defaults.reportTitle
    var reportMessage: String? = defaults.reportMessage
    var pasteUploadEnabled: Boolean = defaults.pasteUploadEnabled
    var pasteUploadBaseUrl: String = defaults.pasteUploadBaseUrl
    var onCrashSaved: ((CrashReport) -> Unit)? = defaults.onCrashSaved
    var killProcessWhenNoPreviousHandler: Boolean = defaults.killProcessWhenNoPreviousHandler
    var anrWatchdogEnabled: Boolean = defaults.anrWatchdogEnabled
    var anrWatchdogTimeoutMillis: Long = defaults.anrWatchdogTimeoutMillis
    var anrWatchdogCheckIntervalMillis: Long = defaults.anrWatchdogCheckIntervalMillis
    var startupHangWatchdogEnabled: Boolean = defaults.startupHangWatchdogEnabled
    var startupHangTimeoutMillis: Long = defaults.startupHangTimeoutMillis
    var onReportSaved: ((CrashReport) -> Unit)? = defaults.onReportSaved
    var onAnrDetected: ((CrashReport) -> Unit)? = defaults.onAnrDetected
    var priorExitCaptureEnabled: Boolean = defaults.priorExitCaptureEnabled
    var crashReportBackendEnabled: Boolean = defaults.crashReportBackendEnabled
    var crashReportBackendBaseUrl: String = defaults.crashReportBackendBaseUrl
    var crashReportAccessToken: String? = defaults.crashReportAccessToken
    var deviceInstallationIdProvider: (() -> String?)? = defaults.deviceInstallationIdProvider

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
            anrWatchdogEnabled = anrWatchdogEnabled,
            anrWatchdogTimeoutMillis = anrWatchdogTimeoutMillis,
            anrWatchdogCheckIntervalMillis = anrWatchdogCheckIntervalMillis,
            startupHangWatchdogEnabled = startupHangWatchdogEnabled,
            startupHangTimeoutMillis = startupHangTimeoutMillis,
            onReportSaved = onReportSaved,
            onAnrDetected = onAnrDetected,
            priorExitCaptureEnabled = priorExitCaptureEnabled,
            crashReportBackendEnabled = crashReportBackendEnabled,
            crashReportBackendBaseUrl = crashReportBackendBaseUrl,
            crashReportAccessToken = crashReportAccessToken,
            deviceInstallationIdProvider = deviceInstallationIdProvider,
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
