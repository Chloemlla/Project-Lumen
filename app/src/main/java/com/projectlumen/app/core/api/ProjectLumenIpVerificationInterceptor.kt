package com.projectlumen.app.core.api

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * 给同源请求挂上 Synapse 首访闸门需要的 `X-Fingerprint` / `X-IP-Verification-Token`。
 *
 * 语义与 Synapse-Client 的 `SynapseIpVerificationInterceptor` 一致：
 *  - 正常路径只加头，不多打请求；
 *  - 撞上 403 + `IP_VERIFICATION_REQUIRED` 时握手一次并重放 —— 这个 403 说明请求根本没进
 *    业务逻辑，重放是安全的；
 *  - 握手换不到令牌时（服务端要人机验证、或 IP 已被封禁）照原样把那个 403 交回调用方，
 *    同时把结论写进 [ProjectLumenIpVerification.challenge] 让界面渲染验证模块。
 *    拦截器不阻塞等用户点验证，也不会再发一个注定还是 403 的请求。
 */
internal class ProjectLumenIpVerificationInterceptor(
    private val origin: HttpUrl,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!isSameOrigin(request.url)) return chain.proceed(request)

        val headers = ProjectLumenIpVerification.headers()
        if (headers.isEmpty()) return chain.proceed(request)

        val first = chain.proceed(request.withHeaders(headers))
        if (first.code != HTTP_FORBIDDEN || !isVerificationRequired(first)) return first

        val token = ProjectLumenIpVerification.tokenForRequest(forceRefresh = true) ?: return first
        first.close()
        return chain.proceed(request.withHeaders(headers + (TOKEN_HEADER to token)))
    }

    private fun isSameOrigin(url: HttpUrl): Boolean =
        url.scheme == origin.scheme && url.host == origin.host && url.port == origin.port

    private fun Request.withHeaders(headers: Map<String, String>): Request = newBuilder()
        .apply { headers.forEach { (name, value) -> header(name, value) } }
        .build()

    private fun isVerificationRequired(response: Response): Boolean {
        val body = runCatching { response.peekBody(PEEK_LIMIT_BYTES).string() }.getOrNull() ?: return false
        return body.contains(ERROR_CODE) || body.contains(REQUIRES_VERIFICATION_SENTINEL)
    }

    private companion object {
        const val HTTP_FORBIDDEN = 403
        const val PEEK_LIMIT_BYTES = 4L * 1024L
        const val ERROR_CODE = ProjectLumenIpVerification.ERROR_CODE
        const val REQUIRES_VERIFICATION_SENTINEL = "\"requiresVerification\":true"
        const val TOKEN_HEADER = ProjectLumenIpVerification.TOKEN_HEADER
    }
}
