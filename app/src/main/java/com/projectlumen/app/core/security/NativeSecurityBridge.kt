package com.projectlumen.app.core.security

internal object NativeSecurityBridge {
    init {
        System.loadLibrary("lumen_security")
    }

    external fun requestSigningSecret(): String

    external fun isNativeEnvironmentAllowed(
        packageName: String,
        signingCertSha256: String,
        debugAllowed: Boolean,
    ): Boolean
}
