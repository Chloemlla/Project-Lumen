package com.projectlumen.app.core.api

import com.projectlumen.app.BuildConfig

object ProjectLumenApiConfig {
    const val DEFAULT_BASE_URL = "http://eye.chloemlla.com/api"
    const val DEFAULT_TRANSLATION_BASE_URL = "https://tts.chloemlla.com"
    const val REQUEST_TIMEOUT_MILLIS = 6_000

    val baseUrl: String
        get() = BuildConfig.API_BASE_URL.trim().ifBlank { DEFAULT_BASE_URL }.trimEnd('/')

    val translationBaseUrl: String
        get() = BuildConfig.TRANSLATION_API_BASE_URL.trim().ifBlank { DEFAULT_TRANSLATION_BASE_URL }.trimEnd('/')

    val telemetryAccessToken: String
        get() = BuildConfig.TELEMETRY_ACCESS_TOKEN.trim()
}
