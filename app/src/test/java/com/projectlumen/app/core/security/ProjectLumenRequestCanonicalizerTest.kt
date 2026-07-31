package com.projectlumen.app.core.security

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectLumenRequestCanonicalizerTest {
    @Test
    fun canonicalPayloadMatchesBackendContractVector() {
        val canonical = ProjectLumenRequestCanonicalizer.canonicalPayload(
            method = "post",
            url = "https://eye.chloemlla.com/api/v1/sync/push?channel=stable&cursor=7".toHttpUrl(),
            bodyText = "{\"a\":1}",
            timestamp = "1720000000",
            nonce = "00112233445566778899aabbccddeeff",
        )

        assertEquals(
            """
            bodySha256=015abd7f5cc57a2dd94b7590f04ad8084273905ee33ec5cebeae62276a97f862
            method=POST
            nonce=00112233445566778899aabbccddeeff
            path=/api/v1/sync/push
            query=channel=stable&cursor=7
            timestamp=1720000000
            """.trimIndent(),
            canonical,
        )
    }
}
