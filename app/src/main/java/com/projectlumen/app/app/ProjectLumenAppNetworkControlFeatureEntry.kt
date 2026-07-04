package com.projectlumen.app.app

import com.projectlumen.app.core.database.entities.AppNetworkControlEntity
import com.projectlumen.app.core.repositories.AppNetworkControlRepository
import com.projectlumen.app.core.shizuku.ShizukuCapabilityManager
import com.projectlumen.app.core.shizuku.ShizukuNetworkApp
import com.projectlumen.app.core.shizuku.ShizukuNetworkPolicyResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class ProjectLumenAppNetworkControlFeatureEntry(
    private val scope: CoroutineScope,
    private val repository: AppNetworkControlRepository,
    private val shizuku: ShizukuCapabilityManager,
) {
    private val _networkApps = MutableStateFlow<List<ShizukuNetworkApp>>(emptyList())

    val networkApps = _networkApps.asStateFlow()
    val records = repository.observeAll()

    fun refreshApps() {
        scope.launch {
            refreshAppCache()
        }
    }

    fun restrictApp(app: ShizukuNetworkApp) {
        scope.launch {
            val nowMillis = System.currentTimeMillis()
            val result = shizuku.restrictAppNetwork(app)
            repository.upsert(result.toRestrictedEntity(nowMillis))
            refreshAppCache()
        }
    }

    fun restoreApp(record: AppNetworkControlEntity) {
        scope.launch {
            val nowMillis = System.currentTimeMillis()
            val result = shizuku.restoreAppNetwork(
                packageName = record.packageName,
                uid = record.uid,
                appType = record.appType,
                restoreDelegatedGuard = record.delegatedGuardApplied,
            )
            repository.upsert(record.withRestoreResult(result, nowMillis))
            refreshAppCache()
        }
    }

    private suspend fun refreshAppCache() {
        _networkApps.value = shizuku.listNetworkControllableApps()
    }

    private fun ShizukuNetworkPolicyResult.toRestrictedEntity(nowMillis: Long): AppNetworkControlEntity {
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
            restrictedAt = if (networkRestricted) nowMillis else 0L,
            restoredAt = 0L,
            updatedAt = nowMillis,
        )
    }

    private fun AppNetworkControlEntity.withRestoreResult(
        result: ShizukuNetworkPolicyResult,
        nowMillis: Long,
    ): AppNetworkControlEntity {
        return copy(
            uid = result.uid,
            appType = result.appType,
            networkRestricted = result.networkRestricted,
            uidPolicyApplied = result.uidPolicyApplied,
            delegatedGuardAttempted = result.delegatedGuardAttempted,
            delegatedGuardApplied = if (result.networkRestricted) delegatedGuardApplied else result.delegatedGuardApplied,
            lastCommandOutput = result.output,
            lastError = result.error,
            restoredAt = if (result.networkRestricted) restoredAt else nowMillis,
            updatedAt = nowMillis,
        )
    }
}
