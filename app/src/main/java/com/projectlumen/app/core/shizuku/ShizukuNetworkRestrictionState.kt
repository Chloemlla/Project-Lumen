package com.projectlumen.app.core.shizuku

internal const val SHIZUKU_UID_POLICY_ERROR_PREFIX = "UID policy:"
internal const val SHIZUKU_DELEGATED_GUARD_ERROR_PREFIX = "Delegated guard:"

internal data class ShizukuNetworkRestrictionState(
    val networkRestricted: Boolean,
    val delegatedGuardApplied: Boolean,
) {
    val active: Boolean
        get() = networkRestricted || delegatedGuardApplied
}

internal data class DelegatedNetworkGuardResult(
    val attempted: Boolean,
    val applied: Boolean,
    val output: String,
    val error: String,
)

internal fun shizukuNetworkPolicyErrors(
    uidPolicySucceeded: Boolean,
    uidPolicyError: String,
    delegatedGuardAttempted: Boolean,
    delegatedGuardApplied: Boolean,
    delegatedGuardError: String,
): String {
    return buildList {
        if (!uidPolicySucceeded) {
            add("$SHIZUKU_UID_POLICY_ERROR_PREFIX ${uidPolicyError.ifBlank { "Android netpolicy command failed." }}")
        }
        if (delegatedGuardAttempted && !delegatedGuardApplied) {
            add(
                "$SHIZUKU_DELEGATED_GUARD_ERROR_PREFIX ${
                    delegatedGuardError.ifBlank { "Delegated network guard is not supported on this device." }
                }",
            )
        }
    }.joinToString("\n")
}

internal fun resolveShizukuNetworkRestrictionState(
    restrict: Boolean,
    previousNetworkRestricted: Boolean,
    previousDelegatedGuardApplied: Boolean,
    uidPolicyCommandSucceeded: Boolean,
    delegatedGuardCommandSucceeded: Boolean,
): ShizukuNetworkRestrictionState {
    return if (restrict) {
        ShizukuNetworkRestrictionState(
            networkRestricted = uidPolicyCommandSucceeded,
            delegatedGuardApplied = delegatedGuardCommandSucceeded,
        )
    } else {
        ShizukuNetworkRestrictionState(
            networkRestricted = previousNetworkRestricted && !uidPolicyCommandSucceeded,
            delegatedGuardApplied = previousDelegatedGuardApplied && !delegatedGuardCommandSucceeded,
        )
    }
}
