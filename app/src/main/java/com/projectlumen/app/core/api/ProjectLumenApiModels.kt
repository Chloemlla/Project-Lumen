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

data class RemoteConfigSyncSnapshot(
    val schemaVersion: Long,
    val cursor: Long,
    val serverTime: Long,
    val channel: String,
    val featureFlags: List<RemoteFeatureFlag>,
    val templates: List<RemoteConfigTemplate>,
    val policies: List<RemoteConfigPolicy>,
)

data class RemoteConfigTemplate(
    val id: String,
    val name: String,
    val tier: String,
    val countdownStyle: String,
    val color: String,
    val locales: List<String>,
    val layoutJson: JSONObject,
    val updatedAt: Long,
)

data class RemoteConfigPolicy(
    val key: String,
    val enabled: Boolean,
    val payload: JSONObject,
    val updatedAt: Long,
)

data class RemoteReleaseCheck(
    val updateAvailable: Boolean,
    val currentVersionCode: Long,
    val versionCode: Long,
    val versionName: String,
    val tagName: String,
    val releaseUrl: String,
    val abi: String,
    val channel: String,
    val fullApkUrl: String,
    val fullApkSha256: String,
    val fullApkSizeBytes: Long,
    val sha256: String,
    val rollout: String,
    val forceUpdate: Boolean,
    val checkedAt: Long,
    val createdAt: Long,
    val assets: List<RemoteReleaseAsset>,
    val patches: List<RemoteReleasePatch>,
)

data class RemoteReleaseAsset(
    val abi: String,
    val name: String,
    val url: String,
    val sha256: String,
    val sizeBytes: Long,
    val contentType: String,
)

data class RemoteReleasePatch(
    val fromVersionCode: Long,
    val fromSha256: String,
    val toSha256: String,
    val patchUrl: String,
    val patchSha256: String,
    val algorithm: String,
    val sizeBytes: Long,
)

data class RemoteDeviceRegistrationResult(
    val accepted: Boolean,
    val deviceInstallationId: String,
    val registeredAt: Long,
)
