package com.projectlumen.app.core.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import android.util.Log
import com.projectlumen.app.BuildConfig
import java.security.MessageDigest

internal fun managedIntegrityFailureReasons(
    javaDebugDetected: Boolean,
    runtimeHookDetected: Boolean,
): List<String> = buildList {
    if (javaDebugDetected) add("java_debugger")
    if (runtimeHookDetected) add("java_runtime_hook")
}

object AppIntegrityGuard {
    private const val TAG = "AppIntegrityGuard"

    fun enforce(context: Context) {
        if (BuildConfig.DEBUG) return

        val appContext = context.applicationContext
        // Clear any identity established by an earlier diagnostic or enforcement call before
        // evaluating managed signals. A Java-side rejection must never leave native signing
        // authorized for the rest of the process.
        NativeSecurityBridge.invalidateVerifiedIdentity()
        val managedFailureReasons = managedIntegrityFailureReasons(
            javaDebugDetected = Debug.isDebuggerConnected() || Debug.waitingForDebugger(),
            runtimeHookDetected = hasRuntimeHookingClasses(),
        )
        if (!BuildConfig.APP_INTEGRITY_ENFORCEMENT_ENABLED || managedFailureReasons.isNotEmpty()) {
            val diagnosticReasons = managedFailureReasons.ifEmpty { listOf("certificate_not_configured") }
            if (!BuildConfig.APP_INTEGRITY_ENFORCEMENT_ENABLED) {
                Log.e(
                    TAG,
                    "App integrity enforcement is not configured; release signing remains blocked: " +
                        diagnosticReasons.joinToString(),
                )
                return
            }
            Log.e(TAG, "Integrity check failed: ${managedFailureReasons.joinToString()}")
            throw SecurityException(
                "Project Lumen integrity check failed: ${managedFailureReasons.joinToString()}.",
            )
        }

        val nativeVerdict = NativeSecurityBridge.evaluateEnvironmentOrNull(
            packageName = appContext.packageName,
            signingCertSha256 = signingCertificateSha256(appContext),
            debugAllowed = false,
            establishReleaseIdentity = true,
        )
        if (nativeVerdict == null) {
            // Native bridge unavailable (missing ABI / load failure). Log and soft-fail so
            // managed-device baseline generation can still launch. Release signing remains
            // fail-closed because it cannot obtain a verified native identity.
            Log.e(TAG, "Native integrity bridge unavailable; release signing remains blocked.")
            return
        }

        val failureReasons = nativeVerdict.diagnosticCodes().map { reason -> "native_$reason" }
        if (failureReasons.isNotEmpty()) {
            // Soft-fail: still report via caller runCatching, but never hard-kill the process
            // when the failure is environment-related (managed emulators, debug hooks).
            Log.e(TAG, "Integrity check failed: ${failureReasons.joinToString()}")
            throw SecurityException(
                "Project Lumen integrity check failed: ${failureReasons.joinToString()}.",
            )
        }
    }

    fun nativeProtectionSummary(context: Context): String {
        val appContext = context.applicationContext
        val managedFailureReasons = managedIntegrityFailureReasons(
            javaDebugDetected = !BuildConfig.DEBUG &&
                (Debug.isDebuggerConnected() || Debug.waitingForDebugger()),
            runtimeHookDetected = !BuildConfig.DEBUG && hasRuntimeHookingClasses(),
        )
        if (!BuildConfig.DEBUG &&
            (!BuildConfig.APP_INTEGRITY_ENFORCEMENT_ENABLED || managedFailureReasons.isNotEmpty())
        ) {
            NativeSecurityBridge.invalidateVerifiedIdentity()
        }
        val nativeVerdict = NativeSecurityBridge.evaluateEnvironmentOrNull(
            packageName = appContext.packageName,
            signingCertSha256 = signingCertificateSha256(appContext),
            debugAllowed = BuildConfig.DEBUG,
            establishReleaseIdentity = false,
        )
        return buildList {
            add("nativeBridge=${if (NativeSecurityBridge.isAvailable) "available" else "unavailable"}")
            add(
                "nativeEnvironment=" + when {
                    nativeVerdict == null -> "unknown"
                    nativeVerdict.isAllowed -> "allowed"
                    else -> "blocked"
                },
            )
            add("nativeReasons=${nativeVerdict?.diagnosticSummary() ?: "native_load_failure"}")
            add(
                "managedReasons=" +
                    managedFailureReasons.joinToString(",").ifBlank { "none" },
            )
            add(
                "requestSigning=" + if (BuildConfig.DEBUG) {
                    "native_or_debug_fallback"
                } else {
                    "verified_native_required"
                },
            )
            add("appIntegrity=${if (BuildConfig.APP_INTEGRITY_ENFORCEMENT_ENABLED) "enabled" else "disabled"}")
        }.joinToString(separator = ";")
    }

    @Suppress("DEPRECATION")
    private fun signingCertificateSha256(context: Context): String {
        return runCatching {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES,
                )
            } else {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES,
                )
            }

            val signatureBytes = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
            } else {
                packageInfo.signatures?.firstOrNull()?.toByteArray()
            }) ?: return@runCatching ""
            MessageDigest.getInstance("SHA-256")
                .digest(signatureBytes)
                .joinToString(separator = "") { byte -> "%02X".format(byte.toInt() and 0xff) }
        }.getOrDefault("")
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
