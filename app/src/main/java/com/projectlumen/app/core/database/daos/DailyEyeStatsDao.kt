package com.projectlumen.app.core.database.daos

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.projectlumen.app.core.database.entities.DailyEyeStatsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyEyeStatsDao {
    @Query("SELECT * FROM daily_eye_stats ORDER BY statDate DESC")
    fun observeAll(): Flow<List<DailyEyeStatsEntity>>

    @Query("SELECT * FROM daily_eye_stats WHERE statDate = :date")
    suspend fun get(date: String): DailyEyeStatsEntity?

    @Upsert
    suspend fun upsert(stat: DailyEyeStatsEntity)
}
