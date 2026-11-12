package com.projectlumen.app.core.api

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 请求路径契约：业务请求必须落在 `<base>/<resource>`，**不能**再带一段 `v1/`。
 *
 * 2026-11-11 线上：客户端 21 个端点全写成 `v1/…`，而 Synapse 把整棵 Lumen 路由挂在
 * `/api/lumen` 上且没有 `/v1` 子段 ⇒ 除 `health` 之外每个请求都是 Express 兜底 404，
 * 用户看到的就是一句 `Project Lumen API request failed with HTTP 404`。
 *
 * 这里用拦截器短路网络、记录真实请求 URL：路径一旦再漂移，用例先红，不必等真机报错。
 */
class ProjectLumenApiClientContractTest {

    private val recordedPaths = mutableListOf<String>()

    private fun client(baseUrl: String = ProjectLumenApiConfig.DEFAULT_BASE_URL): ProjectLumenApiClient {
        val httpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val path = chain.request().url.encodedPath
                recordedPaths += path
                val body = when {
                    path.endsWith("/health") -> """{"status":"ok","service":"project-lumen-api"}"""
                    path.endsWith("/auth/email/start") ->
                        """{"requestId":"req-1","expiresAt":1,"delivery":"email"}"""
                    else -> "{}"
                }
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_2)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
        return ProjectLumenApiClient(baseUrl = baseUrl, httpClient = httpClient)
    }

    @Test
    fun endpointsUseTheCanonicalPrefixWithoutVersionSegment() = runBlocking {
        val apiClient = client()

        apiClient.health()
        apiClient.startEmailLogin("happy@chloemlla.com")

        assertEquals(
            listOf("/api/lumen/health", "/api/lumen/auth/email/start"),
            recordedPaths,
        )
    }

    @Test
    fun everyBaseUrlVariantLandsOnTheSamePath() = runBlocking {
        listOf(
            "https://chloemlla.com",
            "https://chloemlla.com/api",
            "https://chloemlla.com/api/",
            "https://chloemlla.com/api/lumen",
            "https://chloemlla.com/api/lumen/",
            "https://chloemlla.com/api/lumen/v1",
            "https://chloemlla.com/api/lumen/v1/",
        ).forEach { variant ->
            recordedPaths.clear()
            client(variant).health()
            assertEquals(variant, listOf("/api/lumen/health"), recordedPaths)
        }
    }
}
