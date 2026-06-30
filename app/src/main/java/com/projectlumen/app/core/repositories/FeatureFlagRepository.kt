package com.projectlumen.app.core.repositories

import com.projectlumen.app.core.database.daos.FeatureFlagsDao
import com.projectlumen.app.core.database.entities.FeatureFlagEntity
import kotlinx.coroutines.flow.Flow

class FeatureFlagRepository(private val dao: FeatureFlagsDao) {
    fun observeAll(): Flow<List<FeatureFlagEntity>> = dao.observeAll()

    suspend fun isEnabled(key: String): Boolean = dao.get(key)?.enabled == true

    suspend fun upsert(flag: FeatureFlagEntity) {
        dao.upsert(flag)
    }
}
