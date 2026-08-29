package com.projectlumen.app.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClashPartnerCompatTest {

    @Test
    fun apiVersion3TiersAreReadFromAccessTier() {
        assertEquals(ClashAccess.Denied, parseClashAccess(mapOf("accessTier" to "denied")))
        assertEquals(ClashAccess.Basic, parseClashAccess(mapOf("accessTier" to "basic")))
        assertEquals(ClashAccess.Full, parseClashAccess(mapOf("accessTier" to "full")))
    }

    @Test
    fun legacyRepliesWithoutAccessTierCountAsFullOnlyWhenNonEmpty() {
        assertEquals(ClashAccess.Full, parseClashAccess(mapOf("vpnRunning" to true)))
        assertEquals(ClashAccess.Unavailable, parseClashAccess(emptyMap()))
    }

    @Test
    fun everyCmfaDenialReasonMapsToDistinctActionableText() {
        val reasons = listOf(
            "pending_user_approval",
            "denied_by_user",
            "signer_unverified",
            "not_partner",
            "no_signature",
        )
        val described = reasons.map(::describeDeniedReason)

        // 拼错任何一个 key 都会退到 "Clash 返回原因：" 分支，这里正是要卡住那种情况。
        for (text in described) {
            assertFalse(text.contains("Clash 返回原因"))
            assertTrue(text.isNotEmpty())
        }
        assertEquals(reasons.size, described.toSet().size)
    }

    @Test
    fun unknownReasonsArePassedThroughAndNullIsHonest() {
        assertTrue(describeDeniedReason("brand_new_reason").contains("brand_new_reason"))
        assertEquals("Clash 未说明原因", describeDeniedReason(null))
    }
}
