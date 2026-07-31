package com.projectlumen.app.app

import com.projectlumen.app.core.database.entities.AppNetworkControlEntity
import com.projectlumen.app.core.shizuku.ShizukuNetworkPolicyResult

internal val AppNetworkControlEntity.hasActiveNetworkRestriction: Boolean
    get() = networkRestricted || delegatedGuardApplied

private val ShizukuNetworkPolicyResult.hasActiveNetworkRestriction: Boolean
    get() = networkRestricted || delegatedGuardApplied

internal fun ShizukuNetworkPolicyResult.toRestrictedEntity(nowMillis: Long): AppNetworkControlEntity {
    return AppNetworkControlEntity(
        packageName = packageName,
        uid = uid,
        appType = appType,
        networkRestricted = networkRestricted,
        uidPolicyApplied = uidPolicyApplied,
        delegatedGuardAttempted = delegatedGuardAttempted,
        delegatedGuardApplied = delegatedGuardApplied,
        lastCommandOutput = output,
        lastError = error,
        restrictedAt = if (hasActiveNetworkRestriction) nowMillis else 0L,
        restoredAt = 0L,
        updatedAt = nowMillis,
    )
}

internal fun AppNetworkControlEntity.withRestoreResult(
    result: ShizukuNetworkPolicyResult,
    nowMillis: Long,
): AppNetworkControlEntity {
    val restrictionRemainsActive = result.hasActiveNetworkRestriction
    val reconciledRestrictedAt = when {
        !restrictionRemainsActive || restrictedAt > 0L -> restrictedAt
        updatedAt > 0L -> updatedAt
        else -> nowMillis
    }
    return copy(
        uid = result.uid,
        appType = result.appType,
        networkRestricted = result.networkRestricted,
        uidPolicyApplied = result.uidPolicyApplied,
        delegatedGuardAttempted = result.delegatedGuardAttempted,
        delegatedGuardApplied = result.delegatedGuardApplied,
        lastCommandOutput = result.output,
        lastError = result.error,
        restrictedAt = reconciledRestrictedAt,
        restoredAt = if (restrictionRemainsActive) 0L else nowMillis,
        updatedAt = nowMillis,
    )
}
