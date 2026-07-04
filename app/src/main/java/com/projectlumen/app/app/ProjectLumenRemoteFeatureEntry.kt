package com.projectlumen.app.app

import android.os.Build
import com.projectlumen.app.BuildConfig
import com.projectlumen.app.core.api.ProjectLumenApiClient
import com.projectlumen.app.core.api.RemoteConfigPolicy
import com.projectlumen.app.core.api.RemoteConfigTemplate
import com.projectlumen.app.core.api.RemoteEntitlement
import com.projectlumen.app.core.api.RemoteFeatureFlag
import com.projectlumen.app.core.api.RemoteSyncChange
import com.projectlumen.app.core.database.entities.EntitlementEntity
import com.projectlumen.app.core.database.entities.FeatureFlagEntity
import com.projectlumen.app.core.database.entities.TipTemplateEntity
import com.projectlumen.app.core.enums.PlanTier
import com.projectlumen.app.core.enums.TemplateBackgroundType
import com.projectlumen.app.core.repositories.EntitlementRepository
import com.projectlumen.app.core.repositories.FeatureFlagRepository
import com.projectlumen.app.core.repositories.SettingsRepository
import com.projectlumen.app.core.repositories.TipTemplateRepository
import com.projectlumen.app.core.security.SecureCredentialStore
import com.projectlumen.app.core.services.DataBackupService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

internal data class ProjectLumenRemoteUiState(
    val signedInEmail: String = "",
    val sessionAvailable: Boolean = false,
    val pendingEmail: String = "",
    val pendingRequestId: String = "",
    val devCode: String = "",
    val cloudTier: String = "",
    val entitlementCount: Int = 0,
    val syncCursor: Long = 0L,
    val latestBackupUploadedAt: Long = 0L,
    val latestBackupSchemaVersion: Int = 0,
    val lastOperation: String = "",
    val busy: Boolean = false,
    val errorMessage: String = "",
) {
    val signedIn: Boolean get() = sessionAvailable || signedInEmail.isNotBlank()
    val waitingForCode: Boolean get() = pendingRequestId.isNotBlank()
}

internal class ProjectLumenRemoteFeatureEntry(
    private val scope: CoroutineScope,
    private val apiClient: ProjectLumenApiClient,
    private val credentials: SecureCredentialStore,
    private val backup: DataBackupService,
    private val settingsRepository: SettingsRepository,
    private val entitlementRepository: EntitlementRepository,
    private val featureFlagRepository: FeatureFlagRepository,
    private val tipTemplateRepository: TipTemplateRepository,
    private val nativeProtectionSummary: () -> String,
) {
    private val _state = MutableStateFlow(ProjectLumenRemoteUiState())
    val state: StateFlow<ProjectLumenRemoteUiState> = _state.asStateFlow()

    init {
        val session = runCatching { credentials.load() }.getOrNull()
        if (session != null) {
            _state.value = _state.value.copy(
                signedInEmail = session.userEmail,
                sessionAvailable = true,
                syncCursor = credentials.remoteSyncCursor(),
                lastOperation = "Stored session loaded",
            )
        }
    }

    fun checkHealth() = launchRemote("API health checked") {
        val health = apiClient.health()
        _state.value = _state.value.copy(lastOperation = "${health.service} ${health.status} ${health.version}".trim())
    }

    fun startEmailLogin(email: String) = launchRemote("Verification code requested") {
        val normalized = email.trim()
        require(normalized.isNotBlank()) { "Email is required." }
        val start = apiClient.startEmailLogin(normalized)
        _state.value = _state.value.copy(
            pendingEmail = normalized,
            pendingRequestId = start.requestId,
            devCode = start.devCode.orEmpty(),
            lastOperation = "Verification code sent",
        )
    }

    fun verifyEmailLogin(code: String) = launchRemote("Signed in") {
        val current = _state.value
        require(current.pendingEmail.isNotBlank() && current.pendingRequestId.isNotBlank()) {
            "Request a verification code first."
        }
        val session = apiClient.verifyEmailLogin(
            email = current.pendingEmail,
            requestId = current.pendingRequestId,
            code = code.trim(),
            deviceInstallationId = credentials.deviceInstallationId(),
        )
        credentials.save(session)
        _state.value = current.copy(
            signedInEmail = session.user.email,
            sessionAvailable = true,
            pendingRequestId = "",
            devCode = "",
            lastOperation = "Signed in as ${session.user.email}",
        )
        refreshAccountWithAccessToken(session.accessToken)
    }

    fun refreshAccount() = launchRemote("Account refreshed") {
        refreshAccountWithAccessToken(requireAccessToken())
    }

    private suspend fun refreshAccountWithAccessToken(accessToken: String) {
        val user = apiClient.fetchMe(accessToken)
        val deviceFingerprint = credentials.deviceInstallationId()
        apiClient.registerDevice(
            accessToken = accessToken,
            deviceInstallationId = deviceFingerprint,
            deviceFingerprint = deviceFingerprint,
            model = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            versionCode = BuildConfig.VERSION_CODE.toLong(),
            localSecurityConfig = localSecurityConfig(),
        )
        val entitlements = apiClient.fetchEntitlements(accessToken)
        val featureFlags = apiClient.fetchFeatureFlags(accessToken)
        saveRemoteEntitlements(entitlements.entitlements)
        saveRemoteFeatureFlags(featureFlags.flags, featureFlags.fetchedAt)
        val configApplied = syncRemoteConfig()
        settingsRepository.update { current ->
            current.copy(
                planTier = entitlements.tier,
                lastEntitlementSyncAt = entitlements.syncedAt,
            )
        }
        _state.value = _state.value.copy(
            signedInEmail = user.email,
            sessionAvailable = true,
            cloudTier = entitlements.tier,
            entitlementCount = entitlements.entitlements.size,
            lastOperation = "Account, entitlements, feature flags, config ($configApplied), and plan tier refreshed",
        )
    }

    fun syncNow() = launchRemote("Sync completed") {
        requireCloudSyncEntitlement()
        val accessToken = requireAccessToken()
        val deviceId = credentials.deviceInstallationId()
        val configApplied = syncRemoteConfig()
        val startingCursor = credentials.remoteSyncCursor()
        val beforePush = apiClient.fetchSyncChanges(accessToken, startingCursor)
        val pulledBeforePush = applyRemoteSyncChanges(beforePush.changes, deviceId)
        val backupJson = backup.exportBackupJson()
        val changes = backupJsonToSyncChanges(backupJson)
        val pushResult = apiClient.pushSyncChanges(
            accessToken = accessToken,
            deviceInstallationId = deviceId,
            cursor = beforePush.nextCursor,
            changes = changes,
        )
        val afterPush = apiClient.fetchSyncChanges(accessToken, beforePush.nextCursor)
        val pulledAfterPush = applyRemoteSyncChanges(afterPush.changes, deviceId)
        val nextCursor = maxOf(beforePush.nextCursor, pushResult.nextCursor, afterPush.nextCursor)
        credentials.saveRemoteSyncCursor(nextCursor)
        _state.value = _state.value.copy(
            syncCursor = nextCursor,
            lastOperation = "Applied $configApplied config updates; pulled ${pulledBeforePush + pulledAfterPush} remote changes; pushed ${pushResult.accepted} local records",
        )
    }

    fun uploadCloudBackup() = launchRemote("Cloud backup uploaded") {
        requireCloudSyncEntitlement()
        val metadata = apiClient.uploadBackup(
            accessToken = requireAccessToken(),
            deviceInstallationId = credentials.deviceInstallationId(),
            backupJson = backup.exportBackupJson(),
        )
        _state.value = _state.value.copy(
            latestBackupUploadedAt = metadata.uploadedAt,
            latestBackupSchemaVersion = metadata.schemaVersion,
            lastOperation = "Cloud backup uploaded",
        )
    }

    fun restoreLatestCloudBackup() = launchRemote("Latest backup restored") {
        requireCloudSyncEntitlement()
        val remoteBackup = apiClient.fetchLatestBackup(requireAccessToken())
            ?: error("No cloud backup is available.")
        val summary = backup.importBackupJson(remoteBackup.backup)
        _state.value = _state.value.copy(
            latestBackupUploadedAt = remoteBackup.metadata.uploadedAt,
            latestBackupSchemaVersion = summary.schemaVersion,
            lastOperation = "Restored ${summary.eyeStatDays} eye-stat days and ${summary.templateCount} templates",
        )
    }

    fun verifyGooglePurchase(productId: String, purchaseToken: String) = launchRemote("Google purchase verified") {
        val normalizedProductId = productId.trim()
        val normalizedPurchaseToken = purchaseToken.trim()
        require(normalizedProductId.isNotBlank()) { "Product ID is required." }
        require(normalizedPurchaseToken.isNotBlank()) { "Purchase token is required." }
        val verification = apiClient.verifyGooglePurchase(
            accessToken = requireAccessToken(),
            productId = normalizedProductId,
            purchaseToken = normalizedPurchaseToken,
            deviceInstallationId = credentials.deviceInstallationId(),
        )
        verification.entitlement?.let { entitlement ->
            saveRemoteEntitlements(listOf(entitlement))
        }
        val entitlementCount = entitlementRepository.getAll().size
        settingsRepository.update(verification.verifiedAt.takeIf { it > 0L } ?: System.currentTimeMillis()) { current ->
            current.copy(
                planTier = verification.tier,
                entitlementExpiresAt = verification.entitlement?.expiresAt ?: current.entitlementExpiresAt,
                lastEntitlementSyncAt = verification.verifiedAt,
            )
        }
        _state.value = _state.value.copy(
            cloudTier = verification.tier,
            entitlementCount = entitlementCount,
            lastOperation = "Google purchase ${verification.status}",
        )
    }

    fun signOut() {
        credentials.clear()
        _state.value = ProjectLumenRemoteUiState(
            sessionAvailable = false,
            lastOperation = "Signed out",
        )
    }

    private fun launchRemote(successMessage: String, block: suspend () -> Unit) {
        scope.launch {
            _state.value = _state.value.copy(busy = true, errorMessage = "")
            runCatching { block() }
                .onFailure { throwable ->
                    _state.value = _state.value.copy(
                        busy = false,
                        errorMessage = throwable.message ?: "Remote operation failed.",
                    )
                }
                .onSuccess {
                    _state.value = _state.value.copy(
                        busy = false,
                        errorMessage = "",
                        lastOperation = _state.value.lastOperation.ifBlank { successMessage },
                    )
                }
        }
    }

    private suspend fun requireAccessToken(): String {
        val stored = credentials.load() ?: error("Sign in before using cloud features.")
        val refreshMarginMillis = 60_000L
        val nowMillis = System.currentTimeMillis()
        if (stored.expiresAt <= 0L || stored.expiresAt > nowMillis + refreshMarginMillis) {
            _state.value = _state.value.copy(sessionAvailable = true)
            return stored.accessToken
        }
        if (stored.refreshExpiresAt > 0L && stored.refreshExpiresAt <= nowMillis) {
            credentials.clear()
            _state.value = _state.value.copy(sessionAvailable = false, signedInEmail = "")
            error("Stored session expired. Sign in again.")
        }
        val refreshed = apiClient.refreshSession(
            refreshToken = stored.refreshToken,
            deviceInstallationId = credentials.deviceInstallationId(),
        )
        credentials.save(refreshed)
        _state.value = _state.value.copy(
            signedInEmail = refreshed.user.email,
            sessionAvailable = true,
            lastOperation = "Session refreshed",
        )
        return refreshed.accessToken
    }

    private suspend fun requireCloudSyncEntitlement() {
        val tier = planTier(settingsRepository.getOrDefault())
        require(tier >= PlanTier.PLUS) {
            "Commercial Edition Plus is required for cloud sync and cloud backup."
        }
    }

    private fun localSecurityConfig(): String {
        return listOf(
            "signedRequests=native",
            "pinning=configured",
            nativeProtectionSummary(),
        ).joinToString(separator = ";")
    }

    private suspend fun saveRemoteEntitlements(entitlements: List<RemoteEntitlement>) {
        entitlements.forEach { remote ->
            entitlementRepository.upsert(
                EntitlementEntity(
                    source = remote.source,
                    productId = remote.productId,
                    purchaseToken = remote.purchaseToken,
                    tier = remote.tier,
                    status = remote.status,
                    purchasedAt = remote.purchasedAt,
                    expiresAt = remote.expiresAt,
                    lastVerifiedAt = remote.lastVerifiedAt,
                    rawPayloadJson = remote.rawPayloadJson,
                ),
            )
        }
        featureFlagRepository.upsert(
            FeatureFlagEntity(
                key = "remote_entitlements_synced",
                enabled = entitlements.isNotEmpty(),
                payloadJson = JSONObject().put("count", entitlements.size).toString(),
            ),
        )
    }

    private suspend fun syncRemoteConfig(): Int {
        val currentCursor = credentials.remoteConfigCursor()
        val snapshot = apiClient.fetchConfigSync(cursor = currentCursor)
        val appliedFlags = saveRemoteFeatureFlags(snapshot.featureFlags, snapshot.serverTime)
        val appliedPolicies = saveRemoteConfigPolicies(snapshot.policies)
        val appliedTemplates = saveRemoteConfigTemplates(snapshot.templates)
        credentials.saveRemoteConfigCursor(maxOf(currentCursor, snapshot.cursor))
        return appliedFlags + appliedPolicies + appliedTemplates
    }

    private suspend fun saveRemoteFeatureFlags(flags: List<RemoteFeatureFlag>, fetchedAt: Long): Int {
        flags.forEach { flag ->
            featureFlagRepository.upsert(
                FeatureFlagEntity(
                    key = flag.key,
                    enabled = flag.enabled,
                    payloadJson = flag.payload.toString(),
                    updatedAt = fetchedAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
                ),
            )
        }
        return flags.size
    }

    private suspend fun saveRemoteConfigPolicies(policies: List<RemoteConfigPolicy>): Int {
        policies.forEach { policy ->
            featureFlagRepository.upsert(
                FeatureFlagEntity(
                    key = "policy_${policy.key}",
                    enabled = policy.enabled,
                    payloadJson = policy.payload.toString(),
                    updatedAt = policy.updatedAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
                ),
            )
        }
        return policies.size
    }

    private suspend fun saveRemoteConfigTemplates(templates: List<RemoteConfigTemplate>): Int {
        var applied = 0
        templates.forEachIndexed { index, template ->
            val remoteId = template.id.trim()
            val name = template.name.trim()
            if (remoteId.isBlank() || name.isBlank()) return@forEachIndexed
            val existing = tipTemplateRepository.getByRemoteId(remoteId)
            val nowMillis = System.currentTimeMillis()
            val layoutJson = JSONObject(template.layoutJson.toString())
                .put("countdownStyle", template.countdownStyle)
                .put("locales", JSONArray(template.locales))
            val updatedAt = template.updatedAt.takeIf { it > 0L } ?: nowMillis
            val entity = (existing ?: TipTemplateEntity(
                name = name,
                isBuiltin = false,
                remoteId = remoteId,
                sortOrder = 10_000 + index,
                createdAt = updatedAt,
                updatedAt = updatedAt,
            )).copy(
                name = name,
                isBuiltin = false,
                backgroundType = TemplateBackgroundType.SOLID.name,
                backgroundValue = template.color,
                primaryColor = template.color,
                titleText = layoutJson.optString("titleText", existing?.titleText ?: "Time to rest"),
                subtitleText = layoutJson.optString(
                    "subtitleText",
                    existing?.subtitleText ?: "Look away from the screen and relax your eyes.",
                ),
                showSkipButton = layoutJson.optBoolean("showSkipButton", existing?.showSkipButton ?: true),
                layoutJson = layoutJson.toString(),
                isPremium = !template.tier.equals("FREE", ignoreCase = true),
                remoteId = remoteId,
                updatedAt = updatedAt,
                deletedAt = 0L,
            )
            tipTemplateRepository.upsert(entity)
            applied += 1
        }
        return applied
    }

    private suspend fun applyRemoteSyncChanges(
        changes: List<RemoteSyncChange>,
        deviceId: String,
    ): Int {
        val importableChanges = changes.filter { change ->
            !change.operation.equals("DELETE", ignoreCase = true) &&
                change.deviceInstallationId != deviceId
        }
        if (importableChanges.isEmpty()) return 0
        val backupJson = syncChangesToBackupJson(importableChanges)
        if (!backupJson.hasImportableSections()) return 0
        backup.importBackupJson(backupJson)
        return importableChanges.size
    }

    private fun syncChangesToBackupJson(changes: List<RemoteSyncChange>): JSONObject {
        val backupJson = JSONObject()
            .put("schemaVersion", 1)
            .put("exportedAt", System.currentTimeMillis())
        val arrays = mutableMapOf<String, JSONArray>()
        changes.forEach { change ->
            when (change.collection) {
                "settings" -> backupJson.put("settings", change.payload)
                "dailyGoal" -> backupJson.put("dailyGoal", change.payload)
                "templates" -> arrays.addItems("templates", change.payload)
                "dailyEyeStats" -> arrays.addItems("dailyEyeStats", change.payload)
                "dailyPomodoroStats" -> arrays.addItems("dailyPomodoroStats", change.payload)
                "entitlements" -> arrays.addItems("entitlements", change.payload)
                "featureFlags" -> arrays.addItems("featureFlags", change.payload)
                "reminderPlans" -> arrays.addItems("reminderPlans", change.payload)
            }
        }
        arrays.forEach { (key, value) -> backupJson.put(key, value) }
        return backupJson
    }

    private fun MutableMap<String, JSONArray>.addItems(key: String, payload: JSONObject) {
        val target = getOrPut(key) { JSONArray() }
        val items = payload.optJSONArray("items")
        if (items == null) {
            target.put(payload)
            return
        }
        for (index in 0 until items.length()) {
            target.put(items.opt(index))
        }
    }

    private fun JSONObject.hasImportableSections(): Boolean {
        return has("settings") ||
            has("dailyGoal") ||
            has("templates") ||
            has("dailyEyeStats") ||
            has("dailyPomodoroStats") ||
            has("entitlements") ||
            has("featureFlags") ||
            has("reminderPlans")
    }

    private fun backupJsonToSyncChanges(backupJson: JSONObject): List<RemoteSyncChange> {
        val now = System.currentTimeMillis()
        val deviceId = credentials.deviceInstallationId()
        val collections = listOf(
            "settings" to backupJson.optJSONObject("settings"),
            "dailyGoal" to backupJson.optJSONObject("dailyGoal"),
            "templates" to JSONObject().put("items", backupJson.optJSONArray("templates")),
            "dailyEyeStats" to JSONObject().put("items", backupJson.optJSONArray("dailyEyeStats")),
            "dailyPomodoroStats" to JSONObject().put("items", backupJson.optJSONArray("dailyPomodoroStats")),
            "entitlements" to JSONObject().put("items", backupJson.optJSONArray("entitlements")),
            "featureFlags" to JSONObject().put("items", backupJson.optJSONArray("featureFlags")),
            "reminderPlans" to JSONObject().put("items", backupJson.optJSONArray("reminderPlans")),
        )
        return collections.mapNotNull { (collection, payload) ->
            payload ?: return@mapNotNull null
            RemoteSyncChange(
                collection = collection,
                remoteId = "local-$collection",
                operation = "UPSERT",
                payload = payload,
                updatedAt = now,
                deviceInstallationId = deviceId,
            )
        }
    }
}
