package com.projectlumen.app.core.api

import com.projectlumen.app.BuildConfig

object ProjectLumenApiConfig {
    const val DEFAULT_BASE_URL = "http://eye.chloemlla.com/api"
    const val REQUEST_TIMEOUT_MILLIS = 6_000

    val baseUrl: String
        get() = BuildConfig.API_BASE_URL.trim().ifBlank { DEFAULT_BASE_URL }.trimEnd('/')
}
