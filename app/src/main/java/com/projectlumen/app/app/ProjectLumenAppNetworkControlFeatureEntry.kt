package com.projectlumen.app.app

import com.projectlumen.app.core.database.entities.AppNetworkControlEntity
import com.projectlumen.app.core.repositories.AppNetworkControlRepository
import com.projectlumen.app.core.shizuku.ShizukuCapabilityManager
import com.projectlumen.app.core.shizuku.ShizukuNetworkApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class ProjectLumenAppNetworkControlFeatureEntry(
    private val scope: CoroutineScope,
    private val repository: AppNetworkControlRepository,
    private val shizuku: ShizukuCapabilityManager,
) {
    private val _networkApps = MutableStateFlow<List<ShizukuNetworkApp>>(emptyList())

    // The idempotence guard reads the store before the elevated shell returns, so a second tap
    // would pass it; one lock per package keeps restrict/restore from crossing each other.
    private val inFlightPackages = mutableSetOf<String>()
    private val inFlightMutex = Mutex()

    val networkApps = _networkApps.asStateFlow()
    val records = repository.observeAll()

    fun refreshApps() {
        scope.launch {
            refreshAppCache()
        }
    }

    fun restrictApp(app: ShizukuNetworkApp) {
        scope.launch {
            if (!tryAcquire(app.packageName)) return@launch
            try {
                if (repository.get(app.packageName)?.hasActiveNetworkRestriction == true) return@launch
                val result = shizuku.restrictAppNetwork(app)
                repository.upsert(result.toRestrictedEntity(System.currentTimeMillis()))
                refreshAppCache()
            } finally {
                release(app.packageName)
            }
        }
    }

    fun restoreApp(record: AppNetworkControlEntity) {
        scope.launch {
            if (!tryAcquire(record.packageName)) return@launch
            try {
                val currentRecord = repository.get(record.packageName) ?: record
                if (!currentRecord.hasActiveNetworkRestriction) return@launch
                val result = shizuku.restoreAppNetwork(
                    packageName = currentRecord.packageName,
                    uid = currentRecord.uid,
                    appType = currentRecord.appType,
                    previousNetworkRestricted = currentRecord.networkRestricted,
                    previousDelegatedGuardApplied = currentRecord.delegatedGuardApplied,
                )
                repository.upsert(currentRecord.withRestoreResult(result, System.currentTimeMillis()))
                refreshAppCache()
            } finally {
                release(record.packageName)
            }
        }
    }

    private suspend fun tryAcquire(packageName: String): Boolean {
        return inFlightMutex.withLock { inFlightPackages.add(packageName) }
    }

    private suspend fun release(packageName: String) {
        inFlightMutex.withLock { inFlightPackages.remove(packageName) }
    }

    private suspend fun refreshAppCache() {
        _networkApps.value = shizuku.listNetworkControllableApps()
    }
}
