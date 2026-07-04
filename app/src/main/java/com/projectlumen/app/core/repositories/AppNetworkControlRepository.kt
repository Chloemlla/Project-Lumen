package com.projectlumen.app.core.repositories

import com.projectlumen.app.core.database.daos.AppNetworkControlsDao
import com.projectlumen.app.core.database.entities.AppNetworkControlEntity
import kotlinx.coroutines.flow.Flow

class AppNetworkControlRepository(
    private val dao: AppNetworkControlsDao,
) {
    fun observeAll(): Flow<List<AppNetworkControlEntity>> = dao.observeAll()

    suspend fun get(packageName: String): AppNetworkControlEntity? = dao.get(packageName)

    suspend fun getAll(): List<AppNetworkControlEntity> = dao.getAll()

    suspend fun upsert(control: AppNetworkControlEntity) {
        val normalized = control.copy(packageName = control.packageName.trim())
        if (normalized.packageName.isBlank()) return
        dao.upsert(normalized)
    }
}
