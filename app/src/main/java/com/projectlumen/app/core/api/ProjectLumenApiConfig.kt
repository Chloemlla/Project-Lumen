package com.projectlumen.app.core.api

import com.projectlumen.app.BuildConfig
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

object ProjectLumenApiConfig {
    const val DEFAULT_BASE_URL = "https://chloemlla.com/api/lumen"
    const val DEFAULT_TRANSLATION_BASE_URL = "https://chloemlla.com"
    const val REQUEST_TIMEOUT_MILLIS = 6_000

    /** 云端 API 的规范路径两段：`<host>/api/lumen`，版本段不在路径里（见 ProjectLumenApiClient）。 */
    private val API_ROOT_SEGMENTS = listOf("api", "lumen")

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

    /**
     * 把 CI 变量 / 手填基址归一化成「`<scheme>://<host>[:port]/api/lumen`」。
     *
     * 线上排障（2026-11-11）里，`PROJECT_LUMEN_API_BASE_URL` 写成 `https://chloemlla.com/api`
     * 这类少一段的形态并不会报错，只会把**整台客户端**的请求打偏：
     * `…/api/health` 恰好通（服务端真有这个端点），`…/api/v1/…` 落进 Synapse 的蜜罐拿到
     * 200 假数据，其余全 404 —— 面板上是「检查服务说好的、业务一个都不通」。
     * 所以这里按结构归一而不是只比一个字面量：
     *
     * - 空 / 解析不出主机 → [DEFAULT_BASE_URL]
     * - 尾斜杠、重复斜杠、`?query` / `#fragment` 一律丢掉
     * - 末段是版本号（`v1` / `v2`…）就丢掉：版本段由服务端挂载前缀决定，不在客户端拼
     * - 路径为空或只有 `api` → 补成 `api/lumen`（主机根一律当 API 根看待）；
     *   其余路径（自建后端）保持原样
     */
    internal fun normalizeApiBaseUrl(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return DEFAULT_BASE_URL
        val parsed = runCatching { trimmed.toHttpUrl() }.getOrNull() ?: return DEFAULT_BASE_URL
        val authority = buildString {
            append(parsed.scheme)
            append("://")
            append(parsed.host)
            if (parsed.port != parsed.effectiveDefaultPort()) append(':').append(parsed.port)
        }
        val segments = parsed.pathSegments
            .filter { it.isNotBlank() }
            .toMutableList()
            .apply { if (lastOrNull()?.matches(VERSION_SEGMENT) == true) removeAt(lastIndex) }
        if (segments.isEmpty()) {
            segments += API_ROOT_SEGMENTS
        } else if (segments == listOf(API_ROOT_SEGMENTS.first())) {
            segments += API_ROOT_SEGMENTS.last()
        }
        return authority + segments.joinToString(separator = "/", prefix = "/")
    }

    private fun HttpUrl.effectiveDefaultPort(): Int = when (scheme) {
        "https" -> 443
        else -> 80
    }

    private val VERSION_SEGMENT = Regex("^v\\d+$", RegexOption.IGNORE_CASE)
}
