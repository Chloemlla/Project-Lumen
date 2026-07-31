package com.projectlumen.app.core.shizuku

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
