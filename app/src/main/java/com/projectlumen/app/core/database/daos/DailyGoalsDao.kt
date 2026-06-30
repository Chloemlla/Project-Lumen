package com.projectlumen.app.core.database.daos

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.projectlumen.app.core.database.entities.DailyGoalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyGoalsDao {
    @Query("SELECT * FROM daily_goals WHERE id = 1")
    fun observe(): Flow<DailyGoalEntity?>

    @Query("SELECT * FROM daily_goals WHERE id = 1")
    suspend fun get(): DailyGoalEntity?

    @Upsert
    suspend fun upsert(goal: DailyGoalEntity)
}
