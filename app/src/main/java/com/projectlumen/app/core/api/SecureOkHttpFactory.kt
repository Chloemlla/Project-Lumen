package com.projectlumen.app.core.api

import com.projectlumen.app.core.network.ClashPartnerCompat
import okhttp3.CertificatePinner
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.net.Proxy
import java.util.concurrent.TimeUnit

internal object SecureOkHttpFactory {
    /**
     * Builds a hardened OkHttp client for Project Lumen HTTPS APIs.
     *
     * Clash VPN path: when [ClashPartnerCompat.shouldSkipManualProxy] is true the
     * process is already bound to the VPN network. Do not stack an app-level
     * proxy, and force [Proxy.NO_PROXY] so the JVM / system [java.net.ProxySelector]
     * cannot layer an extra HTTP proxy on top of the tunnel. Process binding
     * already covers socket routing for OkHttp / WebView / HttpURLConnection.
     */
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
            // Clash VPN path: process is bound to VPN; never stack an app proxy.
            if (ClashPartnerCompat.shouldSkipManualProxy()) {
                // leave proxy unset (default) and explicitly disable system
                // ProxySelector stacking so JVM/system proxy cannot layer.
                proxy(Proxy.NO_PROXY)
            }
        }
            .connectTimeout(ProjectLumenApiConfig.REQUEST_TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(ProjectLumenApiConfig.REQUEST_TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS)
            .writeTimeout(ProjectLumenApiConfig.REQUEST_TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS)
            .build()
    }
}
