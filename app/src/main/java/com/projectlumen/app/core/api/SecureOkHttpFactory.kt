package com.projectlumen.app.core.api

import com.projectlumen.app.BuildConfig
import okhttp3.CertificatePinner
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.TimeUnit

internal object SecureOkHttpFactory {
    fun create(
        baseUrl: String,
        certificatePins: String,
        requireCertificatePins: Boolean = true,
    ): OkHttpClient {
        val url = baseUrl.toHttpUrl()
        if (url.scheme != "https") {
            throw IOException("Project Lumen API endpoints must use HTTPS.")
        }

        val pins = certificatePins.split(',', ';', '\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (requireCertificatePins && !BuildConfig.DEBUG && pins.size < 2) {
            throw IOException("Release builds require a primary and backup certificate pin for ${url.host}.")
        }

        val certificatePinner = CertificatePinner.Builder().apply {
            pins.forEach { pin -> add(url.host, normalizePin(pin)) }
        }.build()

        return OkHttpClient.Builder()
            .certificatePinner(certificatePinner)
            .connectTimeout(ProjectLumenApiConfig.REQUEST_TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(ProjectLumenApiConfig.REQUEST_TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS)
            .writeTimeout(ProjectLumenApiConfig.REQUEST_TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS)
            .build()
    }

    private fun normalizePin(pin: String): String {
        return if (pin.startsWith("sha256/")) pin else "sha256/$pin"
    }
}
