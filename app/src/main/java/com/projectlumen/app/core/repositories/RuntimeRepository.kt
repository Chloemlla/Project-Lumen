package com.projectlumen.app.core.repositories

import com.projectlumen.app.core.database.daos.RuntimeStateDao
import com.projectlumen.app.core.database.entities.RuntimeStateEntity
import kotlinx.coroutines.flow.Flow

class RuntimeRepository(private val dao: RuntimeStateDao) {
    fun observe(): Flow<RuntimeStateEntity?> = dao.observe()

    suspend fun get(): RuntimeStateEntity? = dao.get()

    suspend fun getOrDefault(): RuntimeStateEntity = dao.get() ?: RuntimeStateEntity()

    suspend fun ensureDefault() {
        if (dao.get() == null) dao.upsert(RuntimeStateEntity())
    }

    suspend fun upsert(state: RuntimeStateEntity): RuntimeStateEntity {
        dao.upsert(state)
        return state
    }

    suspend fun reset(nowMillis: Long): RuntimeStateEntity {
        return upsert(RuntimeStateEntity(updatedAt = nowMillis))
    }
}
