package com.projectlumen.app.core.api

import com.projectlumen.app.BuildConfig

object ProjectLumenApiConfig {
    const val DEFAULT_BASE_URL = "https://eye.chloemlla.com/api"
    const val DEFAULT_TRANSLATION_BASE_URL = "https://tts.chloemlla.com"
    const val REQUEST_TIMEOUT_MILLIS = 6_000
    private const val DEFAULT_API_CERTIFICATE_PINS =
        "sha256/UR9ahtQRSyCW4Fp4mUtq9C10jpKmfS1XHLlztgQRcB8=," +
            "sha256/xwyjb5aN7tSRWj02XSZa2cKGLxXLdKHUBfLT/7twjhQ="

    val baseUrl: String
        get() = BuildConfig.API_BASE_URL.trim().ifBlank { DEFAULT_BASE_URL }.trimEnd('/')

    val translationBaseUrl: String
        get() = BuildConfig.TRANSLATION_API_BASE_URL.trim().ifBlank { DEFAULT_TRANSLATION_BASE_URL }.trimEnd('/')

    val telemetryAccessToken: String
        get() = BuildConfig.TELEMETRY_ACCESS_TOKEN.trim()

    val apiCertificatePins: String
        get() = BuildConfig.API_CERTIFICATE_PINS.trim().ifBlank { DEFAULT_API_CERTIFICATE_PINS }

    val translationCertificatePins: String
        get() = BuildConfig.TRANSLATION_CERTIFICATE_PINS.trim()
}
