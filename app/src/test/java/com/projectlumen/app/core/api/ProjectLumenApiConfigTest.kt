package com.projectlumen.app.core.api

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 基址归一化：CI 变量 / 手填地址少一段或多一段时，**不能**静默把整台客户端的请求打偏。
 *
 * 线上事故（2026-11-11）：`PROJECT_LUMEN_API_BASE_URL` 被写成 `https://chloemlla.com/api`
 * 这类形态时，`…/api/health` 恰好通、`…/api/v1/…` 落进 Synapse 蜜罐拿到 200 假数据，
 * 其余全 404 —— 面板上表现为「检查服务正常、业务一个都不通」。
 */
class ProjectLumenApiConfigTest {

    private val canonical = "https://chloemlla.com/api/lumen"

    @Test
    fun missingPathSegmentsAreCompleted() {
        listOf(
            "https://chloemlla.com",
            "https://chloemlla.com/",
            "https://chloemlla.com/api",
            "https://chloemlla.com/api/",
            "https://chloemlla.com//api//",
        ).forEach { input ->
            assertEquals(input, canonical, ProjectLumenApiConfig.normalizeApiBaseUrl(input))
        }
    }

    @Test
    fun canonicalFormsAreStable() {
        listOf(
            canonical,
            "$canonical/",
            "https://chloemlla.com/api/lumen/v1",
            "https://chloemlla.com/api/lumen/v1/",
            "  https://chloemlla.com/api/lumen/v1/  ",
            "https://chloemlla.com/api/lumen/?from=ci#frag",
        ).forEach { input ->
            assertEquals(input, canonical, ProjectLumenApiConfig.normalizeApiBaseUrl(input))
        }
    }

    @Test
    fun portAndSubdomainArePreserved() {
        assertEquals(
            "https://staging.chloemlla.com:8443/api/lumen",
            ProjectLumenApiConfig.normalizeApiBaseUrl("https://staging.chloemlla.com:8443/api/lumen/v1/"),
        )
        assertEquals(
            "https://chloemlla.com/api/lumen",
            ProjectLumenApiConfig.normalizeApiBaseUrl("https://chloemlla.com:443/api/lumen"),
        )
    }

    @Test
    fun customBackendPathsAreLeftAlone() {
        // 自建后端只去掉尾斜杠与版本段，不强行补 /api/lumen。
        assertEquals(
            "https://lumen.example.test/custom/backend",
            ProjectLumenApiConfig.normalizeApiBaseUrl("https://lumen.example.test/custom/backend/v2/"),
        )
    }

    @Test
    fun blankOrUnparseableFallsBackToDefault() {
        listOf("", "   ", "not a url", "ftp://chloemlla.com").forEach { input ->
            assertEquals(
                input,
                ProjectLumenApiConfig.DEFAULT_BASE_URL,
                ProjectLumenApiConfig.normalizeApiBaseUrl(input),
            )
        }
    }
}
