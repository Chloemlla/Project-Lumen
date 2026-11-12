package com.projectlumen.app.core.api

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 出口探测上报的签名契约（Synapse `/api/ip-risk/report`）。
 *
 * 服务端先 `canonicalizePayload(payload)`（键字典序、`JSON.stringify`）再
 * `HMAC-SHA256(probeKey, canonical)`，两个字节都不能差，否则只回一个
 * `signature_mismatch` 且不告诉你差在哪。所以这里把「canonical 字符串 + 签名」
 * 连同线上验收结果一起钉死：
 *
 * - 同一份 payload（键**故意乱序**插入）在 2026-11-11 用线上 `/api/ip-risk/echo`
 *   签发的会话上报，服务端返回 `{"success":true,"data":{"stored":true,"flags":[]…}}`；
 * - 用例里的 `probeKey` 是合成值（线上会话密钥由主密钥派生、一次性、nonce 已消费），
 *   签名期望值由同一份已验证实现算出。
 *
 * 任一断言变红都意味着客户端会静默丢掉所有上报（服务端只记失败码），必须当成契约破损处理。
 */
class ProjectLumenOriginProbeTest {

    private val collectedAt = "2026-11-11T00:00:00Z"
    private val expectedCanonical =
        """{"collectedAt":"$collectedAt","httpExitIp":"203.0.113.7","languages":["zh-CN"],""" +
            """"timezone":"Asia/Shanghai","timezoneOffsetMin":-480,"uaPlatform":"Android",""" +
            """"userAgent":"Project-Lumen-Android"}"""

    /** 乱序插入：canonical 的排序能力必须自己成立，不靠调用方凑顺序。 */
    private fun samplePayload(): JSONObject = JSONObject()
        .put("userAgent", "Project-Lumen-Android")
        .put("uaPlatform", "Android")
        .put("timezoneOffsetMin", -480)
        .put("timezone", "Asia/Shanghai")
        .put("languages", JSONArray().put("zh-CN"))
        .put("httpExitIp", "203.0.113.7")
        .put("collectedAt", collectedAt)

    @Test
    fun canonicalJsonMatchesTheServerSideRule() {
        assertEquals(expectedCanonical, ProjectLumenOriginProbe.canonicalize(samplePayload()))
    }

    @Test
    fun canonicalJsonIsStableAcrossInsertionOrder() {
        val reversed = JSONObject()
        samplePayload().keys().asSequence().toList().reversed().forEach { key ->
            reversed.put(key, samplePayload().opt(key))
        }
        assertEquals(expectedCanonical, ProjectLumenOriginProbe.canonicalize(reversed))
    }

    @Test
    fun canonicalJsonKeepsArraysOrderedAndQuotesStrings() {
        val payload = JSONObject()
            .put("languages", JSONArray().put("zh-CN").put("en-US"))
            .put("note", "a\"b\\c")
        assertEquals(
            """{"languages":["zh-CN","en-US"],"note":"a\"b\\c"}""",
            ProjectLumenOriginProbe.canonicalize(payload),
        )
    }

    @Test
    fun signatureMatchesHmacSha256OverCanonicalBytes() {
        val probeKey = "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff"
        assertEquals(
            "2989adc1dd4e21d33d553816bbb6b84084ce22eeff8090d0ddda0cc689aeeef2",
            ProjectLumenOriginProbe.signHex(probeKey, expectedCanonical),
        )
    }

    @Test
    fun malformedProbeKeyNeverThrows() {
        assertNull(ProjectLumenOriginProbe.signHex("", expectedCanonical))
        assertNull(ProjectLumenOriginProbe.signHex("abc", expectedCanonical))
        assertNull(ProjectLumenOriginProbe.signHex("zz", expectedCanonical))
    }

    @Test
    fun originDropsTheApiPathButKeepsThePort() {
        assertEquals(
            "https://chloemlla.com/",
            ProjectLumenOriginProbe.originOf("https://chloemlla.com/api/lumen")?.toString(),
        )
        assertEquals(
            "https://chloemlla.com:8443/",
            ProjectLumenOriginProbe.originOf("https://chloemlla.com:8443/api/lumen/v1")?.toString(),
        )
    }
}
