package com.projectlumen.app.core.security

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import android.util.Log
import com.projectlumen.app.BuildConfig
import java.security.MessageDigest

/**
 * Enforces application integrity checks before allowing sensitive operations.
 *
 * CRooot integration: when [enforce] detects a compromised environment, it also
 * logs CRooot's [DeviceSecurityScanner] summary for diagnostics. The scanner
 * is NOT called during [enforce] (which must be fast and non-blocking); instead,
 * call [DeviceSecurityScanner.scan] from a background coroutine and pass the
 * result to [isIntegrityConfirmed] for a richer assessment.
 */
object AppIntegrityGuard {
    private const val TAG = "AppIntegrityGuard"

    /**
     * Fast, synchronous integrity check that runs at cold start.
     * Detects debuggers, native environment tampering, and known hooking frameworks.
     * Does NOT run CRooot (which is an async coroutine operation).
     *
     * @throws SecurityException if the environment is compromised.
     */
    fun enforce(context: Context) {
        if (BuildConfig.DEBUG || !BuildConfig.APP_INTEGRITY_ENFORCEMENT_ENABLED) return
        // The full scan is only affordable once per cold start; the Application's onCreate and the
        // gate's init both call enforce(), and a verdict never changes mid-process. Replaying a
        // failed verdict keeps the gate's second call fail-closed without rescanning.
        cachedVerdict?.let { verified ->
            if (!verified) throw SecurityException("Project Lumen integrity check failed.")
            return
        }

        val appContext = context.applicationContext
        // Auxiliary processes run this same Application, and CRooot's own probe process is
        // named ":zygisk_fd_detector" — its name lands in /proc/self/cmdline, which the native
        // hooking-artifact scan reads, so the probe reports itself as an injected environment.
        // Only the main process holds the state this gate protects.
        if (Application.getProcessName() != appContext.packageName) {
            Log.i(TAG, "Skipping integrity enforcement outside the main process.")
            return
        }
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
            if (NativeSecurityBridge.isDebuggerAttachedNativeOrNull() == true) add("native-debugger")
            if (NativeSecurityBridge.isAdbOverNetworkDetectedOrNull() == true) add("adb-over-network")
        }
        if (failureReasons.isNotEmpty()) {
            Log.e(TAG, "Integrity check failed: ${failureReasons.joinToString()}")
            cachedVerdict = false
            throw SecurityException(
                "Project Lumen integrity check failed: ${failureReasons.joinToString()}.",
            )
        }
        cachedVerdict = true
    }

    /**
     * Returns a summary of the native protection state for diagnostics.
     */
    fun nativeProtectionSummary(context: Context): String {
        val appContext = context.applicationContext
        val nativeAllowed = NativeSecurityBridge.isNativeEnvironmentAllowedOrNull(
            packageName = appContext.packageName,
            signingCertSha256 = signingCertificateSha256(appContext),
            debugAllowed = BuildConfig.DEBUG,
        )
        return buildList {
            add("nativeBridge=${if (NativeSecurityBridge.isAvailable) "available" else "unavailable"}")
            add("nativeEnvironment=${nativeAllowed?.let { if (it) "allowed" else "blocked" } ?: "unknown"}")
            add("adbOverNetwork=${NativeSecurityBridge.isAdbOverNetworkDetectedOrNull()?.let { if (it) "detected" else "clean" } ?: "unknown"}")
            add("nativeDebugger=${NativeSecurityBridge.isDebuggerAttachedNativeOrNull()?.let { if (it) "detected" else "clean" } ?: "unknown"}")
            add("requestSigning=${if (BuildConfig.DEBUG) "native_or_debug_fallback" else "native_required"}")
            add("appIntegrity=${if (BuildConfig.APP_INTEGRITY_ENFORCEMENT_ENABLED) "enabled" else "disabled"}")
        }.joinToString(separator = ";")
    }

    /**
     * Evaluates a CRooot [DeviceSecurityScanner.SecurityAssessment] against
     * Lumen's integrity policy.
     *
     * Use this in background security checks (e.g., before sensitive API calls,
     * during entitlement verification).
     *
     * @return `true` if the device passes Lumen's integrity policy.
     */
    fun isIntegrityConfirmed(assessment: DeviceSecurityScanner.SecurityAssessment): Boolean {
        if (!assessment.completed) {
            // Scan didn't complete — conservative: treat as unconfirmed.
            Log.w(
                TAG,
                "Integrity not confirmed: scan did not complete. " +
                    "rooted=${assessment.rooted} suspicious=${assessment.suspicious} " +
                    "selinuxEnforcing=${assessment.selinuxEnforcing} " +
                    "teeAttestationOk=${assessment.teeAttestationOk}",
            )
            return false
        }
        if (assessment.rooted) {
            Log.w(TAG, "Integrity not confirmed: device is rooted.")
            return false
        }
        if (assessment.hardwareIntegrityOk == false) {
            Log.w(TAG, "Integrity not confirmed: hardware/TEE integrity compromised.")
            return false
        }
        // Suspicious indicators are logged but not automatically rejected —
        // they may be false positives on custom ROMs or developer devices.
        if (assessment.suspicious) {
            Log.w(
                TAG,
                "Integrity warning: suspicious indicators present. " +
                    "selinuxEnforcing=${assessment.selinuxEnforcing} " +
                    "teeAttestationOk=${assessment.teeAttestationOk}",
            )
        }
        return true
    }

    @Suppress("DEPRECATION")
    private fun signingCertificateSha256(context: Context): String {
        cachedSigningCertificateSha256?.let { return it }
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
        val digest = MessageDigest.getInstance("SHA-256").digest(signatureBytes)
        val output = CharArray(digest.size * 2)
        for (index in digest.indices) {
            val unsigned = digest[index].toInt() and 0xff
            output[index * 2] = HEX_CHARS[unsigned ushr 4]
            output[index * 2 + 1] = HEX_CHARS[unsigned and 0x0f]
        }
        return String(output).also { cachedSigningCertificateSha256 = it }
    }

    private fun hasRuntimeHookingClasses(): Boolean {
        return RUNTIME_HOOKING_CLASS_NAMES.any { className ->
            runCatching { Class.forName(className) }.isSuccess
        }
    }

    @Volatile
    private var cachedVerdict: Boolean? = null

    @Volatile
    private var cachedSigningCertificateSha256: String? = null

    private const val HEX_CHARS = "0123456789ABCDEF"

    private val RUNTIME_HOOKING_CLASS_NAMES = listOf(
        "de.robv.android.xposed.XposedBridge",
        "de.robv.android.xposed.XC_MethodHook",
        "com.saurik.substrate.MS$2",
    )
}
