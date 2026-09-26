package com.projectlumen.app.core.api

import com.chloemlla.lumen.crash.CrashBreadcrumbs
import com.projectlumen.app.BuildConfig
import com.projectlumen.app.core.network.ClashPartnerCompat
import okhttp3.CertificatePinner
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.net.Proxy
import java.util.concurrent.ConcurrentHashMap
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
     *
     * `withIpVerification` 为 true 时，给同源请求挂上 Synapse 首访闸门需要的
     * `X-Fingerprint` / `X-IP-Verification-Token`，见 [ProjectLumenIpVerificationInterceptor]。
     * 闸门自己的引导请求必须走 false，否则会自我递归。
     */
    fun create(
        baseUrl: String,
        certificatePins: String,
        requireCertificatePins: Boolean = false,
        withIpVerification: Boolean = false,
    ): OkHttpClient {
        val url = baseUrl.toHttpUrl()
        if (url.scheme != "https") {
            throw IllegalArgumentException("Project Lumen API endpoints must use HTTPS.")
        }
        // Key on the current VPN binding too: the cache stays bounded, but a client built
        // after Clash connects/disconnects still picks up the matching proxy decision.
        val skipProxy = ClashPartnerCompat.shouldSkipManualProxy()
        val cacheKey = "$baseUrl|$certificatePins|$requireCertificatePins|$skipProxy|$withIpVerification"
        return clients.computeIfAbsent(cacheKey) {
            buildClient(url, certificatePins, requireCertificatePins, skipProxy, withIpVerification)
        }
    }

    private fun buildClient(
        url: HttpUrl,
        certificatePins: String,
        requireCertificatePins: Boolean,
        skipProxy: Boolean,
        withIpVerification: Boolean,
    ): OkHttpClient {
        val pins = CertificatePinPolicy.parse(certificatePins)
        if (requireCertificatePins && pins.isEmpty()) {
            throw IllegalArgumentException("Project Lumen certificate pins are required for ${url.host}.")
        }
        recordPinningDiagnostics(url.host, certificatePins, pins)

        return OkHttpClient.Builder().apply {
            if (withIpVerification) {
                // 闸门挂在主机根路径上（/api/ip-verification），所以存下去的是 origin 而不是
                // 带 /api/lumen 后缀的 baseUrl；引导请求从这里拿不带拦截器的客户端。
                val root = HttpUrl.Builder().scheme(url.scheme).host(url.host).port(url.port).build()
                ProjectLumenIpVerification.install(root, certificatePins)
                addInterceptor(ProjectLumenIpVerificationInterceptor(root))
            }
            if (pins.isNotEmpty()) {
                certificatePinner(
                    CertificatePinner.Builder().apply {
                        pins.forEach { pin -> add(url.host, pin) }
                    }.build(),
                )
            }
            // Clash VPN path: process is bound to VPN; never stack an app proxy.
            if (skipProxy) {
                // leave proxy unset (default) and explicitly disable system
                // ProxySelector stacking so JVM/system proxy cannot layer.
                proxy(Proxy.NO_PROXY)
            }
        }
            .connectTimeout(ProjectLumenApiConfig.REQUEST_TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(ProjectLumenApiConfig.REQUEST_TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS)
            .writeTimeout(ProjectLumenApiConfig.REQUEST_TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS)
            // Per-read timeouts cannot stop a slow drip; bound the whole call as well.
            .callTimeout(
                ProjectLumenApiConfig.REQUEST_TIMEOUT_MILLIS.toLong() * CALL_TIMEOUT_FACTOR,
                TimeUnit.MILLISECONDS,
            )
            .build()
    }

    /** Release builds shipping without usable pins are otherwise invisible at runtime. */
    private fun recordPinningDiagnostics(
        host: String,
        configuredPins: String,
        parsedPins: List<String>,
    ) {
        if (BuildConfig.DEBUG || parsedPins.isNotEmpty()) return
        val reason = if (configuredPins.isBlank()) "unconfigured" else "unusable_pins"
        runCatching { CrashBreadcrumbs.record("Certificate pinning inactive host=$host reason=$reason") }
    }

    private val clients = ConcurrentHashMap<String, OkHttpClient>()
    private const val CALL_TIMEOUT_FACTOR = 5L
}

