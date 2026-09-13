package com.projectlumen.app.core.database.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.projectlumen.app.core.database.entities.ScheduleOccurrenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleOccurrencesDao {
    @Query("SELECT * FROM schedule_occurrences WHERE deletedAt = 0 ORDER BY startAt ASC, id ASC")
    fun observeAll(): Flow<List<ScheduleOccurrenceEntity>>

    @Query("SELECT * FROM schedule_occurrences WHERE deletedAt = 0 AND startAt < :toMillis AND endAt >= :fromMillis ORDER BY startAt ASC, id ASC")
    fun observeRange(fromMillis: Long, toMillis: Long): Flow<List<ScheduleOccurrenceEntity>>

    @Query("SELECT * FROM schedule_occurrences WHERE id = :id AND deletedAt = 0")
    suspend fun get(id: Long): ScheduleOccurrenceEntity?

    @Query("SELECT * FROM schedule_occurrences WHERE seriesId = :seriesId AND deletedAt = 0")
    suspend fun getBySeries(seriesId: Long): List<ScheduleOccurrenceEntity>

    @Query("SELECT * FROM schedule_occurrences WHERE deletedAt = 0 AND startAt >= :fromMillis ORDER BY startAt ASC, id ASC")
    suspend fun getUpcoming(fromMillis: Long): List<ScheduleOccurrenceEntity>

    /** Identity keys already materialized for the series, including detached and completed rows. */
    @Query("SELECT originalStartAt FROM schedule_occurrences WHERE seriesId = :seriesId AND deletedAt = 0")
    suspend fun getOriginalStartAts(seriesId: Long): List<Long>

    @Insert
    suspend fun insert(occurrence: ScheduleOccurrenceEntity): Long

    @Insert
    suspend fun insertAll(occurrences: List<ScheduleOccurrenceEntity>): List<Long>

    @Update
    suspend fun update(occurrence: ScheduleOccurrenceEntity)

    @Query("UPDATE schedule_occurrences SET completed = :completed, completedAt = :completedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setCompleted(id: Long, completed: Boolean, completedAt: Long, updatedAt: Long)

    @Query("UPDATE schedule_occurrences SET reminderFiredAt = :firedAt, updatedAt = :firedAt WHERE id = :id")
    suspend fun markReminderFired(id: Long, firedAt: Long)

    @Query("UPDATE schedule_occurrences SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: Long)

    @Query("UPDATE schedule_occurrences SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE seriesId = :seriesId AND detached = 0 AND completed = 0 AND deletedAt = 0 AND originalStartAt >= :fromOriginalStartAt")
    suspend fun softDeleteSeriesFrom(seriesId: Long, fromOriginalStartAt: Long, deletedAt: Long)

    @Query("UPDATE schedule_occurrences SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE seriesId = :seriesId AND detached = 0 AND completed = 0 AND deletedAt = 0")
    suspend fun softDeleteSeriesGenerated(seriesId: Long, deletedAt: Long)

    @Query("UPDATE schedule_occurrences SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE seriesId = :seriesId AND deletedAt = 0")
    suspend fun softDeleteSeriesAll(seriesId: Long, deletedAt: Long)

    @Query("UPDATE schedule_occurrences SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE seriesId = :seriesId AND detached = 0 AND completed = 0 AND deletedAt = 0 AND (originalStartAt < :fromMillis OR originalStartAt >= :toMillis)")
    suspend fun pruneGeneratedOutsideWindow(seriesId: Long, fromMillis: Long, toMillis: Long, deletedAt: Long)
}
