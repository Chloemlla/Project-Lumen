package com.projectlumen.app.core.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import android.util.Log
import com.projectlumen.app.BuildConfig
import java.security.MessageDigest

object AppIntegrityGuard {
    private const val TAG = "AppIntegrityGuard"

    fun enforce(context: Context) {
        if (BuildConfig.DEBUG || !BuildConfig.APP_INTEGRITY_ENFORCEMENT_ENABLED) return

        val appContext = context.applicationContext
        val javaDebugDetected = Debug.isDebuggerConnected() || Debug.waitingForDebugger()
        val nativeAllowed = NativeSecurityBridge.isNativeEnvironmentAllowedOrNull(
            packageName = appContext.packageName,
            signingCertSha256 = signingCertificateSha256(appContext),
            debugAllowed = false,
        )
        if (nativeAllowed == null) {
            // Native bridge unavailable (missing ABI / load failure). Log and soft-fail so
            // managed-device baseline generation can still launch the process.
            Log.e(TAG, "Native integrity bridge unavailable; skipping hard enforcement.")
            return
        }

        val failureReasons = buildList {
            if (javaDebugDetected) add("debugger")
            if (!nativeAllowed) add("native")
            if (hasRuntimeHookingClasses()) add("runtime-hook")
        }
        if (failureReasons.isNotEmpty()) {
            // Soft-fail: still report via caller runCatching, but never hard-kill the process
            // when the failure is environment-related (managed emulators, debug hooks).
            Log.e(TAG, "Integrity check failed: ${failureReasons.joinToString()}")
            throw SecurityException(
                "Project Lumen integrity check failed: ${failureReasons.joinToString()}.",
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun signingCertificateSha256(context: Context): String {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES,
            )
        } else {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        }

        val signatureBytes = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
        } else {
            packageInfo.signatures?.firstOrNull()?.toByteArray()
        }) ?: ByteArray(0)
        return MessageDigest.getInstance("SHA-256")
            .digest(signatureBytes)
            .joinToString(separator = "") { "%02X".format(it) }
    }

    private fun hasRuntimeHookingClasses(): Boolean {
        val classNames = listOf(
            "de.robv.android.xposed.XposedBridge",
            "de.robv.android.xposed.XC_MethodHook",
            "com.saurik.substrate.MS$2",
        )
        return classNames.any { className ->
            runCatching { Class.forName(className) }.isSuccess
        }
    }
}
