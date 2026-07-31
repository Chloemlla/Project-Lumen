package com.projectlumen.app.core.security

internal data class NativeSigningResult(
    val signatureHex: String?,
    val verdict: NativeSecurityVerdict,
)

internal object NativeSecurityBridge {
    private val libraryLoadFailure = runCatching {
        System.loadLibrary("lumen_security")
    }.exceptionOrNull()

    val isAvailable: Boolean get() = libraryLoadFailure == null

    fun evaluateEnvironmentOrNull(
        packageName: String,
        signingCertSha256: String,
        debugAllowed: Boolean,
    ): NativeSecurityVerdict? {
        if (!isAvailable) return null
        return runCatching {
            NativeSecurityVerdict(
                mask = evaluateEnvironment(
                    packageName = packageName,
                    signingCertSha256 = signingCertSha256,
                    debugAllowed = debugAllowed,
                ),
            )
        }.getOrNull()
    }

    fun signCanonicalPayloadOrNull(
        canonicalPayloadUtf8: ByteArray,
        debugAllowed: Boolean,
    ): NativeSigningResult? {
        if (!isAvailable) return null
        return runCatching {
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
    }

    private external fun evaluateEnvironment(
        packageName: String,
        signingCertSha256: String,
        debugAllowed: Boolean,
    ): Int

    private external fun signCanonicalPayload(
        canonicalPayloadUtf8: ByteArray,
        debugAllowed: Boolean,
        reasonMaskOut: IntArray,
    ): String?
}
