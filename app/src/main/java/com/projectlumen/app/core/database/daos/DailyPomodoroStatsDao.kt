package com.projectlumen.app.core.database.daos

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.projectlumen.app.core.database.entities.DailyPomodoroStatsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyPomodoroStatsDao {
    @Query("SELECT * FROM daily_pomodoro_stats ORDER BY statDate DESC")
    fun observeAll(): Flow<List<DailyPomodoroStatsEntity>>

    @Query("SELECT * FROM daily_pomodoro_stats WHERE statDate = :date")
    suspend fun get(date: String): DailyPomodoroStatsEntity?

    @Query("SELECT * FROM daily_pomodoro_stats ORDER BY statDate DESC")
    suspend fun getAll(): List<DailyPomodoroStatsEntity>

    @Upsert
    suspend fun upsert(stat: DailyPomodoroStatsEntity)
}
