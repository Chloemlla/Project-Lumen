package com.projectlumen.app.core.database.daos

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.projectlumen.app.core.database.entities.ScheduleSeriesEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleSeriesDao {
    @Query("SELECT * FROM schedule_series WHERE deletedAt = 0 ORDER BY startAt ASC, id ASC")
    fun observeActive(): Flow<List<ScheduleSeriesEntity>>

    @Query("SELECT * FROM schedule_series WHERE deletedAt = 0 ORDER BY startAt ASC, id ASC")
    suspend fun getActive(): List<ScheduleSeriesEntity>

    @Query("SELECT * FROM schedule_series WHERE id = :id AND deletedAt = 0")
    suspend fun get(id: Long): ScheduleSeriesEntity?

    @Upsert
    suspend fun upsert(series: ScheduleSeriesEntity): Long

    @Query("UPDATE schedule_series SET recurrenceUntil = :until, updatedAt = :updatedAt WHERE id = :id AND deletedAt = 0")
    suspend fun truncate(id: Long, until: Long, updatedAt: Long)

    @Query("UPDATE schedule_series SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: Long)
}
