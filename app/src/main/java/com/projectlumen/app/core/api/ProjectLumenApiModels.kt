package com.projectlumen.app.core.api

import org.json.JSONObject

data class ApiHealth(
    val status: String,
    val service: String,
    val version: String,
)

data class EmailLoginStart(
    val requestId: String,
    val expiresAt: Long,
    val delivery: String,
    val devCode: String?,
)

data class ProjectLumenApiUser(
    val id: String,
    val email: String,
    val createdAt: Long,
    val deviceInstallationId: String,
)

data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val expiresAt: Long,
    val refreshExpiresAt: Long,
    val user: ProjectLumenApiUser,
)

data class RemoteEntitlement(
    val id: String,
    val source: String,
    val productId: String,
    val purchaseToken: String,
    val tier: String,
    val status: String,
    val purchasedAt: Long,
    val expiresAt: Long,
    val lastVerifiedAt: Long,
    val rawPayloadJson: String,
)

data class RemoteEntitlementSnapshot(
    val tier: String,
    val syncedAt: Long,
    val entitlements: List<RemoteEntitlement>,
)

data class RemotePurchaseVerification(
    val status: String,
    val tier: String,
    val verifiedAt: Long,
    val entitlement: RemoteEntitlement?,
)

data class RemoteSyncChange(
    val collection: String,
    val remoteId: String,
    val operation: String,
    val payload: JSONObject,
    val updatedAt: Long,
    val deletedAt: Long = 0L,
    val deviceInstallationId: String,
)

data class RemoteSyncPushResult(
    val accepted: Int,
    val nextCursor: Long,
)

data class RemoteSyncChangesPage(
    val changes: List<RemoteSyncChange>,
    val nextCursor: Long,
    val serverTime: Long,
)

data class RemoteBackupMetadata(
    val id: String,
    val deviceInstallationId: String,
    val schemaVersion: Int,
    val exportedAt: Long,
    val uploadedAt: Long,
)

data class RemoteBackup(
    val metadata: RemoteBackupMetadata,
    val backup: JSONObject,
)

data class RemoteFeatureFlag(
    val key: String,
    val enabled: Boolean,
    val payload: JSONObject,
)

data class RemoteFeatureFlagSnapshot(
    val fetchedAt: Long,
    val flags: List<RemoteFeatureFlag>,
)

data class RemoteReleaseCheck(
    val updateAvailable: Boolean,
    val currentVersionCode: Long,
    val versionCode: Long,
    val versionName: String,
    val sha256: String,
    val rollout: String,
    val forceUpdate: Boolean,
    val checkedAt: Long,
)

data class RemoteDeviceRegistrationResult(
    val accepted: Boolean,
    val deviceInstallationId: String,
    val registeredAt: Long,
)
