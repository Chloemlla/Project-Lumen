package com.projectlumen.app.core.security

internal object NativeSecurityBridge {
    private val libraryLoadFailure = runCatching {
        System.loadLibrary("lumen_security")
    }.exceptionOrNull()

    val isAvailable: Boolean get() = libraryLoadFailure == null

    fun requestSigningSecretOrNull(): String? {
        if (!isAvailable) return null
        return runCatching { requestSigningSecret() }.getOrNull()
    }

    fun isNativeEnvironmentAllowedOrNull(
        packageName: String,
        signingCertSha256: String,
        debugAllowed: Boolean,
    ): Boolean? {
        if (!isAvailable) return null
        return runCatching {
            isNativeEnvironmentAllowed(
                packageName = packageName,
                signingCertSha256 = signingCertSha256,
                debugAllowed = debugAllowed,
            )
        }.getOrNull()
    }

    external fun requestSigningSecret(): String

    external fun isNativeEnvironmentAllowed(
        packageName: String,
        signingCertSha256: String,
        debugAllowed: Boolean,
    ): Boolean
}
