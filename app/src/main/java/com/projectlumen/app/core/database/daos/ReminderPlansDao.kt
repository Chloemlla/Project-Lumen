package com.projectlumen.app.core.database.daos

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.projectlumen.app.core.database.entities.ReminderPlanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderPlansDao {
    @Query("SELECT * FROM reminder_plans WHERE deletedAt = 0 ORDER BY sortOrder ASC, id ASC")
    fun observeActive(): Flow<List<ReminderPlanEntity>>

    @Query("SELECT * FROM reminder_plans WHERE deletedAt = 0 ORDER BY sortOrder ASC, id ASC")
    suspend fun getActive(): List<ReminderPlanEntity>

    @Upsert
    suspend fun upsert(plan: ReminderPlanEntity)
}
