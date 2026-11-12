package com.projectlumen.app.core.api

import java.time.Instant
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * 出口探测上报（Synapse `/api/ip-risk/*`）在 Project-Lumen 侧的实现。
 *
 * 服务端那套「客户端出口探测」原本只由网页端 `ClientOriginProbe` 驱动：浏览器先 GET
 * `/api/ip-risk/echo` 拿一次性会话（`probeId` / `probeKey` / `nonce`），再把本机观测与
 * 本机指纹组成 payload、用 `HMAC-SHA256(probeKey, canonical(payload))` 签名后 POST 到
 * `/api/ip-risk/report`。安卓客户端此前完全没有这一层，于是 ip-risk 面板上看不到任何
 * 来自官方客户端的数据 —— 用户视角就是「Synapse 的 iprisk 没拿到 Project-Lumen 报备的数据」。
 *
 * 这里只上报**原生确实能观测到的**字段（`httpExitIp` / `ipv6Exit` / 时区 / 语言 / UA）：
 * WebSocket 出口与 WebRTC 泄露是浏览器侧独有的探测轴，服务端的
 * `sanitizeProbePayload` / `computeProbeVerdict` 对缺失字段按「不可判定」处理（见
 * `/api/ip-risk/probe-config` 的 `reportProfile`），所以少报这些**不等于**失败，也绝不伪造。
 *
 * 语义与网页端一致的三条硬约束：
 *  - 一次进程生命周期只报一次（会话一次性、nonce 会进重放表）；
 *  - `hmacEnabled=false` 或没拿到探测会话就什么都不发（后端的 report 固定 403）；
 *  - 全程 best-effort：任何异常都吞掉，返回 false，绝不影响主业务流程。
 */
internal object ProjectLumenOriginProbe {

    private const val CONFIG_PATH = "api/ip-risk/probe-config"
    private const val ECHO_PATH = "api/ip-risk/echo"
    private const val REPORT_PATH = "api/ip-risk/report"
    private const val MAX_RESPONSE_BYTES = 64L * 1024L
    private const val MILLIS_PER_MINUTE = 60_000L

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val claimed = AtomicBoolean(false)

    /** 本进程是否已经尝试过上报（无论成败）：会话一次性，重试只会撞 nonce 重放。 */
    internal val hasAttempted: Boolean
        get() = claimed.get()

    /** 旁路副作用用的作用域：调用方（主请求链路）不为上报让出任何调度。 */
    private val probeScope: CoroutineScope by lazy {
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    /**
     * 后台尝试一次上报（幂等：整进程只有第一次真正发请求）。
     *
     * 调用点在 [ProjectLumenApiClient] 的请求完成处 —— 那时闸门令牌一定已经可用，
     * 也不需要在启动阶段就先去撞一轮会话；后端不可达时它自然什么都不会做。
     */
    fun reportOnceAsync(baseUrl: String = ProjectLumenApiConfig.baseUrl) {
        if (claimed.get()) return
        probeScope.launch { runCatching { reportOnce(baseUrl) } }
    }

    /**
     * 上报一次。返回 true 仅在服务端确认 `success:true` 时成立。
     *
     * 调用方不需要等待结果，也不应该因为它失败而改变任何状态。
     */
    suspend fun reportOnce(baseUrl: String = ProjectLumenApiConfig.baseUrl): Boolean =
        withContext(Dispatchers.IO) {
            if (!claimed.compareAndSet(false, true)) return@withContext false
            runCatching { reportUnchecked(baseUrl) }.getOrDefault(false)
        }

    private fun reportUnchecked(baseUrl: String): Boolean {
        val normalizedBase = ProjectLumenApiConfig.normalizeApiBaseUrl(baseUrl)
        val origin = originOf(normalizedBase) ?: return false
        val client = SecureOkHttpFactory.create(
            baseUrl = normalizedBase,
            certificatePins = ProjectLumenApiConfig.apiCertificatePins,
            withIpVerification = true,
        )

        val config = requestJson(client, origin, CONFIG_PATH)?.optJSONObject("data") ?: return false
        if (!config.optBoolean("enabled", false)) return false
        // 没有主密钥时后端固定 403，不发这个注定被拒的请求。
        if (!config.optBoolean("hmacEnabled", false)) return false

        val echo = requestJson(client, origin, endpointPath(config.optString("echoPath"), ECHO_PATH)) ?: return false        val observed = echo.optJSONObject("data") ?: return false
        val probe = echo.optJSONObject("probe") ?: return false
        val probeId = probe.optText("probeId") ?: return false
        val nonce = probe.optText("nonce") ?: return false
        val probeKey = probe.optText("probeKey") ?: return false

        val payload = buildPayload(observed)
        // 没有出口地址的上报没有任何对照价值：服务端只会存一条不可判定的空记录。
        if (payload.optString("httpExitIp").isBlank()) return false
        val signature = signHex(probeKey, canonicalize(payload)) ?: return false

        val body = JSONObject()
            .put("probeId", probeId)
            .put("nonce", nonce)
            .put("payload", payload)
            .put("signature", signature)

        val requestBuilder = Request.Builder()
            .url(urlFor(origin, endpointPath(config.optString("reportPath"), REPORT_PATH)))
            .post(body.toString().toRequestBody(jsonMediaType))
            .header("Accept", "application/json")
            .header("User-Agent", ProjectLumenClientIdentity.USER_AGENT)
        ProjectLumenClientIdentity.headers().forEach { (name, value) -> requestBuilder.header(name, value) }

        return runCatching {
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) return@use false
                val text = readCapped(response) ?: return@use false
                runCatching { JSONObject(text).optBoolean("success", false) }.getOrDefault(false)
            }
        }.getOrDefault(false)
    }

    // ── payload / 契约 ────────────────────────────────────────────────

    /**
     * 原生可观测子集（见 `/api/ip-risk/probe-config` 的 `reportProfile.nativeFields`）。
     *
     * 缺什么就不放什么：服务端把「没这一项」当不可判定，而填 null / 空数组会被当成客户端
     * 的真实观测，很容易凭空造出 `ipv_vs_v6_mismatch` 这类假告警。
     */
    private fun buildPayload(observed: JSONObject): JSONObject {
        val payload = JSONObject()
        observed.optText("primary")?.let { payload.put("httpExitIp", it) }
        observed.optText("ipv6")?.let { payload.put("ipv6Exit", it) }

        val zone = TimeZone.getDefault()
        payload.put("timezone", zone.id)
        // 与浏览器 Date#getTimezoneOffset() 同号：UTC+8 → -480，别和服务端存的方向搞反。
        payload.put(
            "timezoneOffsetMin",
            -(zone.getOffset(System.currentTimeMillis()) / MILLIS_PER_MINUTE).toInt(),
        )
        Locale.getDefault().toLanguageTag()
            .takeIf { it.isNotBlank() }
            ?.let { payload.put("languages", JSONArray().put(it)) }
        payload.put("userAgent", ProjectLumenClientIdentity.USER_AGENT)
        payload.put("uaPlatform", ProjectLumenClientIdentity.PLATFORM)
        payload.put("collectedAt", Instant.now().toString())
        return payload
    }

    /**
     * 与 Synapse `clientProbeService.canonicalizePayload` / 网页端逐字一致的 canonical JSON：
     * 对象键按字典序升序、字符串用 JSON 转义、null 写成 `null`、数组保持顺序、递归。
     * 签名就是 `HMAC-SHA256(probeKey, 这段字节)` 的 hex —— 差一个字节都会被服务端判
     * `signature_mismatch`，且错误信息里不会告诉你差在哪。
     */
    internal fun canonicalize(value: Any?): String = when (value) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> value.keys()
            .asSequence()
            .sorted()
            .joinToString(separator = ",", prefix = "{", postfix = "}") { key ->
                JSONObject.quote(key) + ":" + canonicalize(value.opt(key))
            }

        is JSONArray -> (0 until value.length())
            .joinToString(separator = ",", prefix = "[", postfix = "]") { index ->
                canonicalize(value.opt(index))
            }

        is String -> JSONObject.quote(value)
        is Boolean, is Int, is Long -> value.toString()
        else -> JSONObject.quote(value.toString())
    }

    /** `probeKey` 是 hex 编码的 32 字节密钥（服务端 `deriveProbeKey` 派生、下发给客户端）。 */
    internal fun signHex(probeKeyHex: String, canonicalJson: String): String? = runCatching {
        val normalized = probeKeyHex.trim()
        require(normalized.isNotEmpty() && normalized.length % 2 == 0) { "invalid probe key" }
        val keyBytes = ByteArray(normalized.length / 2) { index ->
            normalized.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(keyBytes, "HmacSHA256"))
        val digest = mac.doFinal(canonicalJson.toByteArray(Charsets.UTF_8))
        buildString(digest.size * 2) {
            digest.forEach { byte ->
                val unsigned = byte.toInt() and 0xff
                append(HEX_CHARS[unsigned ushr 4])
                append(HEX_CHARS[unsigned and 0x0f])
            }
        }
    }.getOrNull()

    // ── HTTP ─────────────────────────────────────────────────────────

    /** 只接受站内相对路径（服务端下发），绝不接受配置里塞进来的绝对 URL。 */
    private fun endpointPath(configured: String, fallback: String): String {
        val value = configured.trim()
        val candidate = if (value.startsWith("/")) value else fallback
        return candidate.trim('/')
    }

    private fun requestJson(client: OkHttpClient, origin: HttpUrl, path: String): JSONObject? = runCatching {
        val requestBuilder = Request.Builder()
            .url(urlFor(origin, path))
            .get()
            .header("Accept", "application/json")
            .header("User-Agent", ProjectLumenClientIdentity.USER_AGENT)
        ProjectLumenClientIdentity.headers().forEach { (name, value) -> requestBuilder.header(name, value) }
        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val text = readCapped(response) ?: return@use null
            runCatching { JSONObject(text) }.getOrNull()
        }
    }.getOrNull()

    private fun readCapped(response: okhttp3.Response): String? {
        val body = response.body ?: return null
        if (body.contentLength() > MAX_RESPONSE_BYTES) return null
        if (body.source().request(MAX_RESPONSE_BYTES + 1L)) return null
        return body.string()
    }

    private fun urlFor(origin: HttpUrl, path: String): HttpUrl =
        origin.newBuilder().addPathSegments(path.trim('/')).build()

    /** 闸门与会话绑定都按 origin 走：基址带 `/api/lumen` 后缀，探测端点却在主机根下。 */
    internal fun originOf(baseUrl: String): HttpUrl? = runCatching {
        val parsed = ProjectLumenApiConfig.normalizeApiBaseUrl(baseUrl).toHttpUrl()
        HttpUrl.Builder().scheme(parsed.scheme).host(parsed.host).port(parsed.port).build()
    }.getOrNull()

    /**
     * 读一个可选文本字段。
     *
     * 不能直接用 `optString`：Android 的 libcore 对 JSON `null` 返回字符串 `"null"`，
     * 而 `org.json:json`（单元测试用的实现）返回 fallback `""` —— 两种行为都会把
     * 「服务端没观测到 IPv6」误写成 `ipv6Exit: "null"`，进而凭空造出 v4/v6 不一致告警。
     */
    private fun JSONObject.optText(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val value = opt(key) as? String ?: return null
        return value.trim().takeIf { it.isNotEmpty() && it != "null" }
    }

    private const val HEX_CHARS = "0123456789abcdef"
}
