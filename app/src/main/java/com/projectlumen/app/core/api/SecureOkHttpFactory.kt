package com.projectlumen.app.core.api

import okhttp3.CertificatePinner
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

internal object SecureOkHttpFactory {
    fun create(
        baseUrl: String,
        certificatePins: String,
        requireCertificatePins: Boolean = false,
    ): OkHttpClient {
        val url = baseUrl.toHttpUrl()
        if (url.scheme != "https") {
            throw IllegalArgumentException("Project Lumen API endpoints must use HTTPS.")
        }

        val pins = CertificatePinPolicy.parse(certificatePins)
        if (requireCertificatePins && pins.isEmpty()) {
            throw IllegalArgumentException("Project Lumen certificate pins are required for ${url.host}.")
        }

        return OkHttpClient.Builder().apply {
            if (pins.isNotEmpty()) {
                certificatePinner(
                    CertificatePinner.Builder().apply {
                        pins.forEach { pin -> add(url.host, pin) }
                    }.build(),
                )
            }
        }
            .connectTimeout(ProjectLumenApiConfig.REQUEST_TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(ProjectLumenApiConfig.REQUEST_TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS)
            .writeTimeout(ProjectLumenApiConfig.REQUEST_TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS)
            .build()
    }
}
