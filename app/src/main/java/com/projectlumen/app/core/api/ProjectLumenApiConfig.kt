package com.projectlumen.app.core.api

import com.projectlumen.app.BuildConfig

object ProjectLumenApiConfig {
    const val DEFAULT_BASE_URL = "https://eye.chloemlla.com/api"
    private const val DEFAULT_HOST_ROOT = "https://eye.chloemlla.com"
    const val DEFAULT_TRANSLATION_BASE_URL = "https://tts.chloemlla.com"
    const val REQUEST_TIMEOUT_MILLIS = 6_000

    val baseUrl: String
        get() = normalizeApiBaseUrl(BuildConfig.API_BASE_URL.trim().ifBlank { DEFAULT_BASE_URL })

    val translationBaseUrl: String
        get() = BuildConfig.TRANSLATION_API_BASE_URL.trim().ifBlank { DEFAULT_TRANSLATION_BASE_URL }.trimEnd('/')

    val telemetryAccessToken: String
        get() = BuildConfig.TELEMETRY_ACCESS_TOKEN.trim()

    val apiCertificatePins: String
        get() = BuildConfig.API_CERTIFICATE_PINS.trim()

    val translationCertificatePins: String
        get() = BuildConfig.TRANSLATION_CERTIFICATE_PINS.trim()

    internal fun normalizeApiBaseUrl(value: String): String {
        val trimmed = value.trim().trimEnd('/')
        return when {
            trimmed.isBlank() -> DEFAULT_BASE_URL
            trimmed == DEFAULT_HOST_ROOT -> DEFAULT_BASE_URL
            else -> trimmed
        }
    }
}
