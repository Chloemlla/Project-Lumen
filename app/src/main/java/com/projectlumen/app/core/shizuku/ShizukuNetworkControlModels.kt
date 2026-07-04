package com.projectlumen.app.core.shizuku

data class ShizukuNetworkApp(
    val packageName: String,
    val uid: Int,
    val appType: String,
    val restrictedByUidPolicy: Boolean = false,
)

data class ShizukuNetworkPolicyResult(
    val packageName: String,
    val uid: Int,
    val appType: String,
    val networkRestricted: Boolean,
    val uidPolicyApplied: Boolean,
    val delegatedGuardAttempted: Boolean,
    val delegatedGuardApplied: Boolean,
    val output: String,
    val error: String,
)

object ShizukuNetworkAppTypes {
    const val SYSTEM = "system"
    const val USER = "user"
}
