package com.projectlumen.app.core.api

import okhttp3.CertificatePinner
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

internal object SecureOkHttpFactory {
    @Suppress("UNUSED_PARAMETER")
    fun create(
        baseUrl: String,
        certificatePins: String,
        requireCertificatePins: Boolean = true,
    ): OkHttpClient {
        val url = baseUrl.toHttpUrl()
        if (url.scheme != "https") {
            throw IllegalArgumentException("Project Lumen API endpoints must use HTTPS.")
        }

        val pins = certificatePins.split(',', ';', '\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val configuredCertificatePinner = CertificatePinner.Builder().apply {
            pins.forEach { pin -> add(url.host, normalizePin(pin)) }
        }.build()

        return OkHttpClient.Builder().apply {
            if (pins.isNotEmpty()) {
                certificatePinner(configuredCertificatePinner)
            }
        }
            .connectTimeout(ProjectLumenApiConfig.REQUEST_TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(ProjectLumenApiConfig.REQUEST_TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS)
            .writeTimeout(ProjectLumenApiConfig.REQUEST_TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS)
            .build()
    }

    private fun normalizePin(pin: String): String {
        return if (pin.startsWith("sha256/")) pin else "sha256/$pin"
    }
}
