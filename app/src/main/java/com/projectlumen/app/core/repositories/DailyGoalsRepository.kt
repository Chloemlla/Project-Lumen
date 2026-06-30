package com.projectlumen.app.core.repositories

import com.projectlumen.app.core.database.daos.DailyGoalsDao
import com.projectlumen.app.core.database.entities.DailyGoalEntity
import kotlinx.coroutines.flow.Flow

class DailyGoalsRepository(private val dao: DailyGoalsDao) {
    fun observe(): Flow<DailyGoalEntity?> = dao.observe()

    suspend fun getOrDefault(): DailyGoalEntity = dao.get() ?: DailyGoalEntity()

    suspend fun ensureDefault() {
        if (dao.get() == null) dao.upsert(DailyGoalEntity())
    }

    suspend fun update(
        nowMillis: Long = System.currentTimeMillis(),
        transform: (DailyGoalEntity) -> DailyGoalEntity,
    ): DailyGoalEntity {
        val updated = transform(getOrDefault()).copy(id = 1, updatedAt = nowMillis)
        dao.upsert(updated)
        return updated
    }
}
