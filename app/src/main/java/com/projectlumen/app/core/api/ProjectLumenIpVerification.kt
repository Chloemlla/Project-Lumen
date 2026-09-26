package com.projectlumen.app.core.api

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Synapse 首访闸门（`/api/ip-verification`）在 Project-Lumen 侧的实现。
 *
 * 与 Synapse-Client 的 `SynapseIpVerificationInterceptor`、网页端
 * `frontend/src/utils/ipVerification.ts` 同语义：
 *  - 闸门开启时服务根下的 `/api/` 路径全部要带 `X-Fingerprint`（服务端要求
 *    `^[a-zA-Z0-9_-]{8,200}$`）与 `X-IP-Verification-Token`；
 *  - 干净 IP 由服务端直接签发 `issuedBy: "auto"` 的令牌，不打扰用户；
 *  - 被标记的 IP 回 `requiresVerification: true`，这时才把 [challenge] 抛给界面渲染
 *    人机验证模块，用 `captchaToken` 走 `/api/ip-verification/complete`；
 *  - 令牌有效期一律听服务端的（`expiresAt` 优先，其次 `tokenTtlMinutes`，默认 40 分钟），
 *    本地只提前 60 秒换新；
 *  - 403 + `IP_VERIFICATION_REQUIRED` 说明请求根本没进业务逻辑，重放一次是安全的。
 *
 * 令牌只活在进程内：它绑定 (指纹, IP) 且只有 40 分钟，落盘既没必要也多一份泄露面。
 */
internal object ProjectLumenIpVerification {

    const val ERROR_CODE: String = "IP_VERIFICATION_REQUIRED"
    const val BANNED_ERROR_TEXT: String = "IP已被封禁"

    private const val DEFAULT_TTL_MINUTES = 40
    private const val REFRESH_SKEW_MILLIS = 60_000L
    private const val RETRY_COOLDOWN_MILLIS = 5 * 60_000L

    private val fingerprintPattern = Regex("^[A-Za-z0-9_-]{8,200}$")
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val refreshLock = Any()

    @Volatile
    private var apiRoot: HttpUrl? = null

    @Volatile
    private var certificatePins: String = ""

    /** 设备标识可能在存储不可用时每次都不一样，这里锁存一次，避免令牌反复失效。 */
    @Volatile
    private var cachedFingerprint: String? = null

    @Volatile
    private var cached: CachedToken? = null

    /** 拿不到令牌时的冷却截止时间，避免每个请求都去撞闸门。 */
    @Volatile
    private var blockedUntilMillis: Long = 0L

    private val _challenge = MutableStateFlow<ProjectLumenIpChallenge?>(null)

    /** 界面观察这里：非空表示服务端要求人机验证，需要渲染验证模块。 */
    val challenge: StateFlow<ProjectLumenIpChallenge?> = _challenge.asStateFlow()

    private data class CachedToken(
        val token: String,
        val fingerprint: String,
        val expiresAtMillis: Long,
    ) {
        fun isUsableFor(candidate: String): Boolean =
            candidate == fingerprint && System.currentTimeMillis() < expiresAtMillis - REFRESH_SKEW_MILLIS
    }

    /**
     * 由 [SecureOkHttpFactory] 在建带闸门的客户端时调用（同一主机重复安装是幂等的）。
     * 引导请求要用不带拦截器的客户端，身份只能从构造参数里拿，不能反过来依赖 DI。
     */
    internal fun install(apiRoot: HttpUrl, certificatePins: String) {
        if (this.apiRoot != null) return
        this.apiRoot = apiRoot
        this.certificatePins = certificatePins
    }

    /** 闸门所需的请求头；没有令牌时只带指纹，让服务端回 403。 */
    fun headers(): Map<String, String> {
        val fingerprint = fingerprint()
        if (fingerprint.isEmpty()) return emptyMap()
        val token = cached?.takeIf { it.isUsableFor(fingerprint) }?.token
        return buildMap {
            put(FINGERPRINT_HEADER, fingerprint)
            if (token != null) put(TOKEN_HEADER, token)
        }
    }

    /**
     * 闸门用的设备指纹 = 安装标识（单一来源，见 [ProjectLumenClientIdentity.deviceInstallationId]）。
     * 两处各读一份会让令牌里的指纹与设备分组里的标识对不上；存储不可用时安装标识
     * 每次都可能不一样，所以这里只算一次并锁存在进程里。
     */
    fun fingerprint(): String {
        cachedFingerprint?.let { return it }
        val resolved = ProjectLumenClientIdentity.deviceInstallationId()
            ?.takeIf { fingerprintPattern.matches(it) }
            ?: runCatching { java.util.UUID.randomUUID().toString().replace("-", "") }.getOrNull()
            ?: return ""
        cachedFingerprint = resolved
        return resolved
    }

    /**
     * 验证码组件的页面源。Cloudflare / hCaptcha 按页面 origin 校验 sitekey 域名，
     * 所以 WebView 里 `loadDataWithBaseURL` 必须拿真实 origin，不能用 `data:`。
     */
    fun pageBaseUrl(): String {
        val root = apiRoot ?: return ""
        val port = if (root.port == root.defaultPort) "" else ":${root.port}"
        return "${root.scheme}://${root.host}$port"
    }

    /**
     * 静默拿一个可用令牌：复用缓存 → 向服务端要一个。
     *
     * 只在 403 之后被调用，且带冷却窗口，所以闸门关着的部署不会平白多打请求。
     * 服务端要求人机验证 / IP 被封时把结论写进 [challenge] 并返回 null ——
     * 拦截器不阻塞等用户，请求照原样回 403，用户完成验证后的下一个请求就能过。
     */
    fun tokenForRequest(forceRefresh: Boolean): String? {
        val fingerprint = fingerprint()
        if (fingerprint.isEmpty()) return null
        if (!forceRefresh) {
            cached?.takeIf { it.isUsableFor(fingerprint) }?.let { return it.token }
        }
        synchronized(refreshLock) {
            if (!forceRefresh) {
                cached?.takeIf { it.isUsableFor(fingerprint) }?.let { return it.token }
                if (System.currentTimeMillis() < blockedUntilMillis) return null
            }
            val outcome = runCatching { requestToken(fingerprint) }
                .getOrElse { Issued.failure("bootstrap_exception") }
            return when (outcome) {
                is Issued.Token -> {
                    cached = CachedToken(outcome.token, fingerprint, outcome.expiresAtMillis)
                    blockedUntilMillis = 0L
                    _challenge.value = null
                    outcome.token
                }

                is Issued.NeedsHuman -> {
                    cached = null
                    blockedUntilMillis = System.currentTimeMillis() + RETRY_COOLDOWN_MILLIS
                    _challenge.value = ProjectLumenIpChallenge(
                        reason = outcome.reason,
                        fingerprint = fingerprint,
                    )
                    null
                }

                is Issued.Banned -> {
                    cached = null
                    blockedUntilMillis = System.currentTimeMillis() + RETRY_COOLDOWN_MILLIS
                    _challenge.value = ProjectLumenIpChallenge(
                        reason = outcome.reason,
                        fingerprint = fingerprint,
                        banned = true,
                        banExpiresAt = outcome.expiresAt,
                    )
                    null
                }

                is Issued.Failed -> {
                    cached = null
                    blockedUntilMillis = System.currentTimeMillis() + RETRY_COOLDOWN_MILLIS
                    null
                }
            }
        }
    }

    fun dismissChallenge() {
        _challenge.value = null
        blockedUntilMillis = 0L
    }

    /** 用户取消验证模块：留一个短冷却，别让下一个请求立刻再弹一次。 */
    fun challengeCancelled() {
        _challenge.value = null
        blockedUntilMillis = System.currentTimeMillis() + RETRY_COOLDOWN_MILLIS
    }

    // ── 人机验证 ────────────────────────────────────────────────────────

    /**
     * 拉验证码配置。服务端一次给出 Turnstile 与 hCaptcha 两组，本端按
     * 「先 Turnstile 后 hCaptcha」挑选，与网页端 `useSecureCaptchaSelection` 的取舍一致。
     */
    suspend fun captchaConfig(): ProjectLumenCaptchaConfig? = withContext(Dispatchers.IO) {
        val root = apiRoot ?: return@withContext null
        val request = Request.Builder()
            .url(root.newBuilder().addPathSegments("api/turnstile/public-config").build())
            .get()
            .header("Accept", "application/json")
            .header("User-Agent", ProjectLumenClientIdentity.USER_AGENT)
            .build()
        executeJson(request) { json ->
            val turnstileKey = json.optString("siteKey").trim()
            val hcaptchaKey = json.optString("hcaptchaSiteKey").trim()
            when {
                json.optBoolean("enabled", false) && turnstileKey.isNotEmpty() ->
                    ProjectLumenCaptchaConfig(ProjectLumenCaptchaType.TURNSTILE, turnstileKey)

                json.optBoolean("hcaptchaEnabled", false) && hcaptchaKey.isNotEmpty() ->
                    ProjectLumenCaptchaConfig(ProjectLumenCaptchaType.HCAPTCHA, hcaptchaKey)

                else -> null
            }
        }
    }

    /** 提交验证码换访问令牌；成功时同时把令牌存进 40 分钟缓存。 */
    suspend fun complete(captchaToken: String, captchaType: String): Boolean = withContext(Dispatchers.IO) {
        val root = apiRoot ?: return@withContext false
        val fingerprint = fingerprint()
        if (fingerprint.isEmpty() || captchaToken.isBlank()) return@withContext false
        val body = JSONObject()
            .put("fingerprint", fingerprint)
            .put("captchaToken", captchaToken.trim())
            .put("captchaType", captchaType)
            .toString()
            .toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(root.newBuilder().addPathSegments("api/ip-verification/complete").build())
            .post(body)
            .header("Accept", "application/json")
            .header("User-Agent", ProjectLumenClientIdentity.USER_AGENT)
            .build()
        val issued = executeJson(request) { json -> parseIssued(json) }
        when {
            issued is Issued.Token -> {
                cached = CachedToken(issued.token, fingerprint, issued.expiresAtMillis)
                blockedUntilMillis = 0L
                _challenge.value = null
                true
            }

            else -> false
        }
    }

    // ── HTTP ────────────────────────────────────────────────────────────

    private fun requestToken(fingerprint: String): Issued {
        val root = apiRoot ?: return Issued.failure("not_configured")
        val body = JSONObject().put("fingerprint", fingerprint).toString().toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(root.newBuilder().addPathSegments("api/ip-verification/session").build())
            .post(body)
            .header("Accept", "application/json")
            .header("User-Agent", ProjectLumenClientIdentity.USER_AGENT)
            .build()
        val client = bootstrapClient(root)
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (response.code == 403 && isBannedPayload(text)) {
                val json = runCatching { JSONObject(text) }.getOrNull()
                return Issued.Banned(
                    reason = json?.optString("reason")?.takeIf { it.isNotBlank() }
                        ?: json?.optString("error")?.takeIf { it.isNotBlank() },
                    expiresAt = json?.optString("expiresAt")?.takeIf { it.isNotBlank() },
                )
            }
            if (!response.isSuccessful) return Issued.failure("http_${response.code}")
            val json = runCatching { JSONObject(text) }.getOrElse { return Issued.failure("invalid_json") }
            return parseIssued(json)
        }
    }

    private fun parseIssued(json: JSONObject): Issued {
        val token = json.optString("token").takeIf { it.isNotBlank() }
        return when {
            token != null && json.optBoolean("verified", false) ->
                Issued.Token(token, resolveExpiryMillis(json))

            json.optBoolean("requiresVerification", false) ->
                Issued.NeedsHuman(
                    reason = json.optString("reason").takeIf { it.isNotBlank() }
                        ?: json.optString("error").takeIf { it.isNotBlank() },
                )

            json.optBoolean("verified", false) ->
                // 闸门没开 / 该路径被豁免：服务端会直接 verified 但不发令牌。走到这里说明
                // 刚才那个 403 和这个结论矛盾，交给冷却窗口重试，不拿注定失败的请求反复撞闸门。
                Issued.Failed("verification_not_required")

            else -> Issued.failure(
                json.optString("reason").takeIf { it.isNotBlank() }
                    ?: json.optString("error").takeIf { it.isNotBlank() }
                    ?: "no_token_issued",
            )
        }
    }

    /** `expiresAt` 优先，其次 `tokenTtlMinutes`，都没有才退回默认 40 分钟。 */
    private fun resolveExpiryMillis(json: JSONObject): Long {
        val instant = json.optString("expiresAt").takeIf { it.isNotBlank() }?.let { raw ->
            runCatching { java.time.Instant.parse(raw).toEpochMilli() }.getOrNull()
        }
        if (instant != null && instant > 0L) return instant
        val ttlMinutes = json.optInt("tokenTtlMinutes", 0).takeIf { it > 0 } ?: DEFAULT_TTL_MINUTES
        return System.currentTimeMillis() + ttlMinutes * 60_000L
    }

    private fun isBannedPayload(text: String): Boolean =
        text.contains(BANNED_ERROR_TEXT) || text.contains("\"IP_BANNED\"") || text.contains("\"banned\":true")

    private fun <T> executeJson(request: Request, parse: (JSONObject) -> T?): T? {
        val root = apiRoot ?: return null
        return try {
            bootstrapClient(root).newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val text = response.body?.string().orEmpty()
                parse(JSONObject(text))
            }
        } catch (_: Exception) {
            // 验证配置/提交失败只退回默认表现，不把网络异常披给 UI。
            null
        }
    }

    /** 不带本闸门的客户端：引导请求必须走这条，否则自我递归。 */
    private fun bootstrapClient(root: HttpUrl): OkHttpClient {
        return SecureOkHttpFactory.create(
            baseUrl = "${root.scheme}://${root.host}:${root.port}",
            certificatePins = certificatePins,
        )
    }

    private sealed interface Issued {
        data class Token(val token: String, val expiresAtMillis: Long) : Issued
        data class NeedsHuman(val reason: String?) : Issued
        data class Banned(val reason: String?, val expiresAt: String?) : Issued
        data class Failed(val reason: String?) : Issued

        companion object {
            fun failure(reason: String): Failed = Failed(reason)
        }
    }

    internal const val FINGERPRINT_HEADER = "X-Fingerprint"
    internal const val TOKEN_HEADER = "X-IP-Verification-Token"
}

/** 界面要渲染的验证请求。`banned == true` 时没有验证可做，只展示原因。 */
internal data class ProjectLumenIpChallenge(
    val reason: String?,
    val fingerprint: String,
    val banned: Boolean = false,
    val banExpiresAt: String? = null,
)

enum class ProjectLumenCaptchaType { TURNSTILE, HCAPTCHA }

internal data class ProjectLumenCaptchaConfig(
    val type: ProjectLumenCaptchaType,
    val siteKey: String,
)
