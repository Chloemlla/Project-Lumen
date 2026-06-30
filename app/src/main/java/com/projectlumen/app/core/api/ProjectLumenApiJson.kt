package com.projectlumen.app.core.api

import org.json.JSONArray
import org.json.JSONObject

internal fun JSONObject.toApiHealth(): ApiHealth = ApiHealth(
    status = optString("status"),
    service = optString("service"),
    version = optString("version"),
)

internal fun JSONObject.toEmailLoginStart(): EmailLoginStart = EmailLoginStart(
    requestId = optString("requestId"),
    expiresAt = optLong("expiresAt"),
    delivery = optString("delivery"),
    devCode = optNullableString("devCode"),
)

internal fun JSONObject.toAuthSession(): AuthSession = AuthSession(
    accessToken = optString("accessToken"),
    tokenType = optString("tokenType", "Bearer"),
    expiresAt = optLong("expiresAt"),
    user = getJSONObject("user").toApiUser(),
)

internal fun JSONObject.toApiUser(): ProjectLumenApiUser = ProjectLumenApiUser(
    id = optString("id"),
    email = optString("email"),
    createdAt = optLong("createdAt"),
    deviceInstallationId = optString("deviceInstallationId"),
)

internal fun JSONObject.toEntitlementSnapshot(): RemoteEntitlementSnapshot = RemoteEntitlementSnapshot(
    tier = optString("tier", "FREE"),
    syncedAt = optLong("syncedAt"),
    entitlements = optJSONArray("entitlements").toObjectList { it.toRemoteEntitlement() },
)

internal fun JSONObject.toRemotePurchaseVerification(): RemotePurchaseVerification = RemotePurchaseVerification(
    status = optString("status"),
    tier = optString("tier", "FREE"),
    verifiedAt = optLong("verifiedAt"),
    entitlement = optJSONObject("entitlement")?.toRemoteEntitlement(),
)

internal fun JSONObject.toRemoteEntitlement(): RemoteEntitlement = RemoteEntitlement(
    id = optString("id"),
    source = optString("source"),
    productId = optString("productId"),
    purchaseToken = optString("purchaseToken"),
    tier = optString("tier", "FREE"),
    status = optString("status"),
    purchasedAt = optLong("purchasedAt"),
    expiresAt = optLong("expiresAt"),
    lastVerifiedAt = optLong("lastVerifiedAt"),
    rawPayloadJson = optString("rawPayloadJson"),
)

internal fun JSONObject.toSyncPushResult(): RemoteSyncPushResult = RemoteSyncPushResult(
    accepted = optInt("accepted"),
    nextCursor = optLong("nextCursor"),
)

internal fun JSONObject.toSyncChangesPage(): RemoteSyncChangesPage = RemoteSyncChangesPage(
    changes = optJSONArray("changes").toObjectList { it.toRemoteSyncChange() },
    nextCursor = optLong("nextCursor"),
    serverTime = optLong("serverTime"),
)

internal fun JSONObject.toRemoteSyncChange(): RemoteSyncChange = RemoteSyncChange(
    collection = optString("collection"),
    remoteId = optString("remoteId"),
    operation = optString("operation"),
    payload = optJSONObject("payload") ?: JSONObject(),
    updatedAt = optLong("updatedAt"),
    deletedAt = optLong("deletedAt"),
    deviceInstallationId = optString("deviceInstallationId"),
)

internal fun RemoteSyncChange.toJson(): JSONObject = JSONObject()
    .put("collection", collection)
    .put("remoteId", remoteId)
    .put("operation", operation)
    .put("payload", payload)
    .put("updatedAt", updatedAt)
    .put("deletedAt", deletedAt)
    .put("deviceInstallationId", deviceInstallationId)

internal fun JSONObject.toRemoteBackupMetadata(): RemoteBackupMetadata = RemoteBackupMetadata(
    id = optString("id"),
    deviceInstallationId = optString("deviceInstallationId"),
    schemaVersion = optInt("schemaVersion"),
    exportedAt = optLong("exportedAt"),
    uploadedAt = optLong("uploadedAt"),
)

internal fun JSONObject.toRemoteBackup(): RemoteBackup = RemoteBackup(
    metadata = getJSONObject("metadata").toRemoteBackupMetadata(),
    backup = optJSONObject("backup") ?: JSONObject(),
)

internal fun JSONObject.optRemoteBackup(): RemoteBackup? = optJSONObject("backup")?.toRemoteBackup()

internal fun JSONObject.optNullableString(name: String): String? {
    if (!has(name) || isNull(name)) return null
    return optString(name)
}

private inline fun <T> JSONArray?.toObjectList(transform: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            optJSONObject(index)?.let { add(transform(it)) }
        }
    }
}
