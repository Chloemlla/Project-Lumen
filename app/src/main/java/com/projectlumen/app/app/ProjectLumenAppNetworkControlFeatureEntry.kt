package com.projectlumen.app.app

import com.projectlumen.app.core.database.entities.AppNetworkControlEntity
import com.projectlumen.app.core.repositories.AppNetworkControlRepository
import com.projectlumen.app.core.shizuku.ShizukuCapabilityManager
import com.projectlumen.app.core.shizuku.ShizukuNetworkApp
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
            if (repository.get(app.packageName)?.hasActiveNetworkRestriction == true) return@launch
            val nowMillis = System.currentTimeMillis()
            val result = shizuku.restrictAppNetwork(app)
            repository.upsert(result.toRestrictedEntity(nowMillis))
            refreshAppCache()
        }
    }

    fun restoreApp(record: AppNetworkControlEntity) {
        scope.launch {
            val currentRecord = repository.get(record.packageName) ?: record
            if (!currentRecord.hasActiveNetworkRestriction) return@launch
            val nowMillis = System.currentTimeMillis()
            val result = shizuku.restoreAppNetwork(
                packageName = currentRecord.packageName,
                uid = currentRecord.uid,
                appType = currentRecord.appType,
                previousNetworkRestricted = currentRecord.networkRestricted,
                previousDelegatedGuardApplied = currentRecord.delegatedGuardApplied,
            )
            repository.upsert(currentRecord.withRestoreResult(result, nowMillis))
            refreshAppCache()
        }
    }

    private suspend fun refreshAppCache() {
        _networkApps.value = shizuku.listNetworkControllableApps()
    }
}
