package com.projectlumen.app.core.repositories

import com.projectlumen.app.core.database.daos.AppSettingsDao
import com.projectlumen.app.core.database.entities.AppSettingsEntity
import kotlinx.coroutines.flow.Flow

class SettingsRepository(private val dao: AppSettingsDao) {
    fun observe(): Flow<AppSettingsEntity?> = dao.observe()

    suspend fun get(): AppSettingsEntity? = dao.get()

    suspend fun getOrDefault(): AppSettingsEntity = dao.get() ?: AppSettingsEntity()

    suspend fun ensureDefault() {
        if (dao.get() == null) dao.upsert(AppSettingsEntity())
    }

    suspend fun update(
        nowMillis: Long = System.currentTimeMillis(),
        transform: (AppSettingsEntity) -> AppSettingsEntity,
    ): AppSettingsEntity {
        val current = dao.get() ?: AppSettingsEntity()
        val updated = transform(current).copy(id = 1, updatedAt = nowMillis)
        dao.upsert(updated)
        return updated
    }
}
