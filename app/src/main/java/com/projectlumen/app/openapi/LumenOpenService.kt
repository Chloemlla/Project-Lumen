package com.projectlumen.app.openapi

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Process
import com.project.lumen.open.ILumenOpenApi
import com.projectlumen.app.BuildConfig
import com.projectlumen.app.ProjectLumenApplication
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class LumenOpenService : Service() {
    private val controller: LumenOpenRuntimeController
        get() = (application as ProjectLumenApplication).openApiController

    private val binder = object : ILumenOpenApi.Stub() {
        override fun getEyeFatigueLevel(): Int {
            requireCaller(LumenOpenContracts.PERMISSION_ACCESS_CORE)
            return runBlocking(Dispatchers.IO) { controller.getEyeFatigueLevel() }
        }

        override fun getContinuousScreenTime(): Long {
            requireCaller(LumenOpenContracts.PERMISSION_ACCESS_CORE)
            return runBlocking(Dispatchers.IO) { controller.getContinuousScreenTime() }
        }

        override fun isRestingNow(): Boolean {
            requireCaller(LumenOpenContracts.PERMISSION_ACCESS_CORE)
            return runBlocking(Dispatchers.IO) { controller.isRestingNow() }
        }

        override fun startFocusSession(tag: String?, durationMs: Long) {
            val callerPackage = requireCaller(LumenOpenContracts.PERMISSION_ACCESS_CORE)
            runBlocking(Dispatchers.IO) {
                controller.startFocusSession(
                    tag = tag,
                    durationMs = durationMs,
                    sourceApp = callerPackage,
                )
            }
        }

        override fun stopFocusSession() {
            val callerPackage = requireCaller(LumenOpenContracts.PERMISSION_ACCESS_CORE)
            runBlocking(Dispatchers.IO) { controller.stopFocusSession(callerPackage) }
        }

        override fun triggerEyeRelaxation() {
            val callerPackage = requireCaller(LumenOpenContracts.PERMISSION_TRIGGER_CONTROL)
            runBlocking(Dispatchers.IO) { controller.triggerEyeRelaxation(callerPackage) }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun requireCaller(permission: String): String {
        val callingUid = Binder.getCallingUid()
        if (callingUid == Process.myUid()) return packageName
        if (checkCallingPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("Unauthorized access: requires $permission")
        }

        val callingPackages = packageManager.getPackagesForUid(callingUid)
            ?.filterNotNull()
            .orEmpty()
        if (callingPackages.isEmpty()) {
            throw SecurityException("Unauthorized access: unknown calling package for uid $callingUid")
        }
        verifyTrustedSignatureIfConfigured(callingPackages)
        return sanitizeLumenOpenSourceApp(
            callingPackages.first(),
            fallback = "uid:$callingUid",
        )
    }

    private fun verifyTrustedSignatureIfConfigured(callingPackages: List<String>) {
        val trustedDigests = BuildConfig.OPEN_API_TRUSTED_SIGNATURE_SHA256
            .split(',', ';', '\n')
            .mapNotNull { it.normalizedDigest().takeIf(String::isNotBlank) }
            .toSet()
        if (trustedDigests.isEmpty()) return

        val sameSignature = callingPackages.any {
            packageManager.checkSignatures(packageName, it) == PackageManager.SIGNATURE_MATCH
        }
        if (sameSignature) return

        val matchesTrustedDigest = callingPackages.any { callerPackage ->
            packageSignatures(callerPackage).any { signature ->
                signature.sha256Digest() in trustedDigests
            }
        }
        if (!matchesTrustedDigest) {
            throw SecurityException("Unauthorized access: caller signature is not trusted")
        }
    }

    @Suppress("DEPRECATION")
    private fun packageSignatures(callerPackage: String): List<Signature> {
        return runCatching {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageManager.getPackageInfo(callerPackage, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                packageManager.getPackageInfo(callerPackage, PackageManager.GET_SIGNATURES)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signingInfo = packageInfo.signingInfo ?: return@runCatching emptyList()
                val signatures = if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
                signatures?.toList().orEmpty()
            } else {
                packageInfo.signatures?.toList().orEmpty()
            }
        }.getOrDefault(emptyList())
    }

    private fun Signature.sha256Digest(): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(toByteArray())
        return bytes.joinToString(separator = "") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun String.normalizedDigest(): String {
        return trim()
            .replace(":", "")
            .replace(" ", "")
            .lowercase(Locale.US)
    }
}
