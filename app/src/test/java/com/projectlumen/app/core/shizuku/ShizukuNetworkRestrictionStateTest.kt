package com.projectlumen.app.core.shizuku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShizukuNetworkRestrictionStateTest {
    @Test
    fun commandErrorsIdentifyTheFailedRestrictionChannel() {
        assertEquals(
            "UID policy: uid failed",
            shizukuNetworkPolicyErrors(
                uidPolicySucceeded = false,
                uidPolicyError = "uid failed",
                delegatedGuardAttempted = true,
                delegatedGuardApplied = true,
                delegatedGuardError = "",
            ),
        )
        assertEquals(
            "Delegated guard: guard failed",
            shizukuNetworkPolicyErrors(
                uidPolicySucceeded = true,
                uidPolicyError = "",
                delegatedGuardAttempted = true,
                delegatedGuardApplied = false,
                delegatedGuardError = "guard failed",
            ),
        )
    }

    @Test
    fun restrictKeepsDelegatedGuardWhenUidPolicyFails() {
        val state = resolveShizukuNetworkRestrictionState(
            restrict = true,
            previousNetworkRestricted = false,
            previousDelegatedGuardApplied = false,
            uidPolicyCommandSucceeded = false,
            delegatedGuardCommandSucceeded = true,
        )

        assertFalse(state.networkRestricted)
        assertTrue(state.delegatedGuardApplied)
        assertTrue(state.active)
    }

    @Test
    fun delegatedOnlyRestoreDoesNotInventUidRestrictionWhenUidClearFails() {
        val state = resolveShizukuNetworkRestrictionState(
            restrict = false,
            previousNetworkRestricted = false,
            previousDelegatedGuardApplied = true,
            uidPolicyCommandSucceeded = false,
            delegatedGuardCommandSucceeded = true,
        )

        assertFalse(state.networkRestricted)
        assertFalse(state.delegatedGuardApplied)
        assertFalse(state.active)
    }

    @Test
    fun restoreKeepsOnlyUidPolicyWhenDelegatedGuardClearSucceeds() {
        val state = resolveShizukuNetworkRestrictionState(
            restrict = false,
            previousNetworkRestricted = true,
            previousDelegatedGuardApplied = true,
            uidPolicyCommandSucceeded = false,
            delegatedGuardCommandSucceeded = true,
        )

        assertTrue(state.networkRestricted)
        assertFalse(state.delegatedGuardApplied)
        assertTrue(state.active)
    }

    @Test
    fun restoreKeepsOnlyDelegatedGuardWhenUidPolicyClearSucceeds() {
        val state = resolveShizukuNetworkRestrictionState(
            restrict = false,
            previousNetworkRestricted = true,
            previousDelegatedGuardApplied = true,
            uidPolicyCommandSucceeded = true,
            delegatedGuardCommandSucceeded = false,
        )

        assertFalse(state.networkRestricted)
        assertTrue(state.delegatedGuardApplied)
        assertTrue(state.active)
    }
}
