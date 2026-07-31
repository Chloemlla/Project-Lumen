package com.projectlumen.app.app

import com.projectlumen.app.core.database.entities.AppNetworkControlEntity
import com.projectlumen.app.core.shizuku.ShizukuNetworkAppTypes
import com.projectlumen.app.core.shizuku.ShizukuNetworkPolicyResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectLumenAppNetworkControlStateTest {
    @Test
    fun delegatedOnlyRestrictionIsPersistedAsActive() {
        val nowMillis = 2_000L

        val entity = policyResult(
            networkRestricted = false,
            delegatedGuardApplied = true,
        ).toRestrictedEntity(nowMillis)

        assertTrue(entity.hasActiveNetworkRestriction)
        assertEquals(nowMillis, entity.restrictedAt)
        assertEquals(0L, entity.restoredAt)
    }

    @Test
    fun restoreUsesUidOnlyStateWhenDelegatedGuardClearSucceeds() {
        val restored = activeRecord().withRestoreResult(
            result = policyResult(
                networkRestricted = true,
                delegatedGuardApplied = false,
            ),
            nowMillis = 3_000L,
        )

        assertTrue(restored.networkRestricted)
        assertTrue(restored.uidPolicyApplied)
        assertFalse(restored.delegatedGuardApplied)
        assertTrue(restored.hasActiveNetworkRestriction)
        assertEquals(0L, restored.restoredAt)
    }

    @Test
    fun restoreUsesDelegatedOnlyStateWhenUidPolicyClearSucceeds() {
        val restored = activeRecord().withRestoreResult(
            result = policyResult(
                networkRestricted = false,
                delegatedGuardApplied = true,
            ),
            nowMillis = 4_000L,
        )

        assertFalse(restored.networkRestricted)
        assertFalse(restored.uidPolicyApplied)
        assertTrue(restored.delegatedGuardApplied)
        assertTrue(restored.hasActiveNetworkRestriction)
        assertEquals(0L, restored.restoredAt)
    }

    @Test
    fun restoreCompletesOnlyAfterUidPolicyAndDelegatedGuardAreClear() {
        val nowMillis = 5_000L

        val restored = activeRecord().withRestoreResult(
            result = policyResult(
                networkRestricted = false,
                delegatedGuardApplied = false,
            ),
            nowMillis = nowMillis,
        )

        assertFalse(restored.hasActiveNetworkRestriction)
        assertEquals(nowMillis, restored.restoredAt)
    }

    @Test
    fun delegatedGuardDisplayShowsClearedAfterSuccessfulRestore() {
        val record = activeRecord().copy(
            networkRestricted = false,
            uidPolicyApplied = false,
            delegatedGuardApplied = false,
            lastError = "",
            restoredAt = 6_000L,
        )

        assertEquals(DelegatedNetworkGuardDisplayStatus.CLEARED, record.delegatedNetworkGuardDisplayStatus)
    }

    @Test
    fun delegatedGuardDisplayShowsClearedWhenOnlyUidRestoreFails() {
        val record = activeRecord().copy(
            delegatedGuardApplied = false,
            lastError = "UID policy: Android netpolicy command failed.",
        )

        assertEquals(DelegatedNetworkGuardDisplayStatus.CLEARED, record.delegatedNetworkGuardDisplayStatus)
    }

    @Test
    fun delegatedGuardDisplayKeepsUnsupportedForGuardFailure() {
        val record = activeRecord().copy(
            delegatedGuardApplied = false,
            lastError = "Delegated guard: operation is not supported",
        )

        assertEquals(DelegatedNetworkGuardDisplayStatus.UNSUPPORTED, record.delegatedNetworkGuardDisplayStatus)
    }

    private fun activeRecord(): AppNetworkControlEntity {
        return AppNetworkControlEntity(
            packageName = PACKAGE_NAME,
            uid = UID,
            appType = ShizukuNetworkAppTypes.USER,
            networkRestricted = true,
            uidPolicyApplied = true,
            delegatedGuardAttempted = true,
            delegatedGuardApplied = true,
            restrictedAt = 1_000L,
            updatedAt = 1_500L,
        )
    }

    private fun policyResult(
        networkRestricted: Boolean,
        delegatedGuardApplied: Boolean,
    ): ShizukuNetworkPolicyResult {
        return ShizukuNetworkPolicyResult(
            packageName = PACKAGE_NAME,
            uid = UID,
            appType = ShizukuNetworkAppTypes.USER,
            networkRestricted = networkRestricted,
            uidPolicyApplied = networkRestricted,
            delegatedGuardAttempted = true,
            delegatedGuardApplied = delegatedGuardApplied,
            output = "",
            error = if (networkRestricted || delegatedGuardApplied) "Partial restore." else "",
        )
    }

    private companion object {
        private const val PACKAGE_NAME = "com.example.target"
        private const val UID = 10_321
    }
}
