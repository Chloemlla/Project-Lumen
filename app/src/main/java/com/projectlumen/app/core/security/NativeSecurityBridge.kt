package com.projectlumen.app.core.security

internal data class NativeSigningResult(
    val signatureHex: String?,
    val verdict: NativeSecurityVerdict,
)

internal object NativeSecurityBridge {
    private val libraryLoadFailure = runCatching {
        System.loadLibrary("lumen_security")
    }.exceptionOrNull()

    @Volatile
    private var managedReleaseIdentityVerified = false

    val isAvailable: Boolean get() = libraryLoadFailure == null

    fun evaluateEnvironmentOrNull(
        packageName: String,
        signingCertSha256: String,
        debugAllowed: Boolean,
        establishReleaseIdentity: Boolean,
    ): NativeSecurityVerdict? {
        if (!isAvailable) return null
        val verdict = runCatching {
            NativeSecurityVerdict(
                mask = evaluateEnvironment(
                    packageName = packageName,
                    signingCertSha256 = signingCertSha256,
                    debugAllowed = debugAllowed,
                    establishReleaseIdentity = establishReleaseIdentity,
                ),
            )
        }.getOrNull()
        if (!debugAllowed) {
            when {
                verdict == null -> managedReleaseIdentityVerified = false
                establishReleaseIdentity -> managedReleaseIdentityVerified = verdict.isAllowed
                !verdict.isAllowed -> managedReleaseIdentityVerified = false
            }
        }
        return verdict
    }

    fun invalidateVerifiedIdentity() {
        managedReleaseIdentityVerified = false
        if (!isAvailable) return
        runCatching { invalidateReleaseIdentity() }
    }

    fun signCanonicalPayloadOrNull(
        canonicalPayloadUtf8: ByteArray,
        debugAllowed: Boolean,
    ): NativeSigningResult? {
        if (!isAvailable) return null
        if (!debugAllowed && !managedReleaseIdentityVerified) {
            return NativeSigningResult(
                signatureHex = null,
                verdict = NativeSecurityVerdict(NativeSecurityReason.RELEASE_IDENTITY_NOT_VERIFIED.bit),
            )
        }
        val result = runCatching {
            val reasonMask = IntArray(1)
            val signature = signCanonicalPayload(
                canonicalPayloadUtf8 = canonicalPayloadUtf8,
                debugAllowed = debugAllowed,
                reasonMaskOut = reasonMask,
            )
            NativeSigningResult(
                signatureHex = signature,
                verdict = NativeSecurityVerdict(reasonMask.single()),
            )
        }.getOrNull()
        if (!debugAllowed && (result == null || !result.verdict.isAllowed)) {
            managedReleaseIdentityVerified = false
        }
        return result
    }

    private external fun evaluateEnvironment(
        packageName: String,
        signingCertSha256: String,
        debugAllowed: Boolean,
        establishReleaseIdentity: Boolean,
    ): Int

    private external fun invalidateReleaseIdentity()

    private external fun signCanonicalPayload(
        canonicalPayloadUtf8: ByteArray,
        debugAllowed: Boolean,
        reasonMaskOut: IntArray,
    ): String?
}
