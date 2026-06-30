package com.projectlumen.app.core.repositories

import com.projectlumen.app.core.database.daos.ReminderPlansDao
import com.projectlumen.app.core.database.entities.ReminderPlanEntity
import kotlinx.coroutines.flow.Flow

class ReminderPlansRepository(private val dao: ReminderPlansDao) {
    fun observeActive(): Flow<List<ReminderPlanEntity>> = dao.observeActive()

    suspend fun getActive(): List<ReminderPlanEntity> = dao.getActive()

    suspend fun upsert(plan: ReminderPlanEntity) {
        dao.upsert(plan)
    }
}
