package com.projectlumen.app.core.repositories

import com.projectlumen.app.core.database.daos.ScheduleOccurrencesDao
import com.projectlumen.app.core.database.daos.ScheduleSeriesDao
import com.projectlumen.app.core.database.entities.ScheduleOccurrenceEntity
import com.projectlumen.app.core.database.entities.ScheduleSeriesEntity
import com.projectlumen.app.core.enums.ScheduleEditScope
import com.projectlumen.app.core.enums.ScheduleRecurrence
import com.projectlumen.app.core.schedule.ScheduleDraft
import com.projectlumen.app.core.schedule.ScheduleMaterializer
import kotlinx.coroutines.flow.Flow

/**
 * Write side of the to-do / schedule feature plus the reads the reminder chain needs.
 *
 * Deliberately free of Android dependencies (no `AlarmManager`, no `Context`): after every write it
 * only reports through [onScheduleChanged], and the caller owning the context re-arms alarms.
 */
class ScheduleRepository(
    private val seriesDao: ScheduleSeriesDao,
    private val occurrencesDao: ScheduleOccurrencesDao,
    private val materializer: ScheduleMaterializer,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val onScheduleChanged: () -> Unit = {},
) {
    fun observeAll(): Flow<List<ScheduleOccurrenceEntity>> = occurrencesDao.observeAll()

    suspend fun get(id: Long): ScheduleOccurrenceEntity? = occurrencesDao.get(id)

    suspend fun getSeries(id: Long): ScheduleSeriesEntity? = seriesDao.get(id)

    /** Creates a standalone entry when [ScheduleDraft.recurrence] is `NONE`, otherwise a series. */
    suspend fun create(draft: ScheduleDraft): Long {
        val timestamp = now()
        if (draft.recurrence == ScheduleRecurrence.NONE) {
            val id = occurrencesDao.insert(standaloneOccurrence(draft, timestamp))
            onScheduleChanged()
            return id
        }

        val seriesId = seriesDao.upsert(
            ScheduleSeriesEntity(
                title = draft.title,
                category = draft.category.name,
                allDay = draft.allDay,
                startAt = draft.startAt,
                endAt = draft.endAt,
                recurrence = draft.recurrence.name,
                recurrenceUntil = draft.recurrenceUntil,
                reminderMinutesBefore = draft.reminderMinutesBefore,
                reminderMethod = draft.reminderMethod.name,
                createdAt = timestamp,
                updatedAt = timestamp,
            ),
        )
        materializer.materializeSeries(seriesId, timestamp)
        onScheduleChanged()

        // Callers must not depend on a non-zero result: an anchor older than the materialization
        // window produces no occurrence for this start time (design K-7).
        val occurrences = occurrencesDao.getBySeries(seriesId)
        return occurrences.firstOrNull { it.originalStartAt == draft.startAt }?.id
            ?: occurrences.minByOrNull { it.startAt }?.id
            ?: 0L
    }

    /** Applies [draft] to the occurrence [id], with [scope] deciding how far the change reaches. */
    suspend fun update(id: Long, draft: ScheduleDraft, scope: ScheduleEditScope): Long {
        val target = occurrencesDao.get(id) ?: return 0L
        val timestamp = now()

        if (target.seriesId == 0L) {
            // A standalone entry has no rule to split, so every scope behaves the same.
            if (draft.recurrence != ScheduleRecurrence.NONE) {
                occurrencesDao.softDelete(id, timestamp)
                return create(draft)
            }
            occurrencesDao.update(
                target.copy(
                    title = draft.title,
                    category = draft.category.name,
                    allDay = draft.allDay,
                    startAt = draft.startAt,
                    endAt = draft.endAt,
                    reminderMinutesBefore = draft.reminderMinutesBefore,
                    reminderMethod = draft.reminderMethod.name,
                    recurrence = ScheduleRecurrence.NONE.name,
                    recurrenceUntil = 0L,
                    originalStartAt = draft.startAt,
                    updatedAt = timestamp,
                ),
            )
            onScheduleChanged()
            return id
        }

        val series = seriesDao.get(target.seriesId)
        if (series == null) {
            occurrencesDao.update(
                target.copy(
                    title = draft.title,
                    category = draft.category.name,
                    allDay = draft.allDay,
                    startAt = draft.startAt,
                    endAt = draft.endAt,
                    reminderMinutesBefore = draft.reminderMinutesBefore,
                    reminderMethod = draft.reminderMethod.name,
                    detached = true,
                    updatedAt = timestamp,
                ),
            )
            onScheduleChanged()
            return id
        }

        return when (scope) {
            ScheduleEditScope.THIS_ONLY -> updateThisOnly(target, draft, timestamp)
            ScheduleEditScope.THIS_AND_FUTURE -> updateThisAndFuture(target, series, draft, timestamp)
            ScheduleEditScope.ALL -> updateAll(target, series, draft, timestamp)
        }
    }

    /** Deletes the occurrence [id], with [scope] deciding how far the deletion reaches. */
    suspend fun delete(id: Long, scope: ScheduleEditScope) {
        val target = occurrencesDao.get(id) ?: return
        val timestamp = now()
        val series = if (target.seriesId == 0L) null else seriesDao.get(target.seriesId)

        if (series == null) {
            occurrencesDao.softDelete(id, timestamp)
            onScheduleChanged()
            return
        }

        when (scope) {
            ScheduleEditScope.THIS_ONLY -> occurrencesDao.softDelete(id, timestamp)

            ScheduleEditScope.THIS_AND_FUTURE ->
                if (target.originalStartAt <= series.startAt) {
                    deleteSeries(series.id, timestamp)
                } else {
                    seriesDao.truncate(series.id, target.originalStartAt - 1L, timestamp)
                    occurrencesDao.softDeleteSeriesFrom(series.id, target.originalStartAt, timestamp)
                }

            ScheduleEditScope.ALL -> deleteSeries(series.id, timestamp)
        }
        onScheduleChanged()
    }

    suspend fun setCompleted(id: Long, completed: Boolean) {
        val timestamp = now()
        occurrencesDao.setCompleted(id, completed, if (completed) timestamp else 0L, timestamp)
        onScheduleChanged()
    }

    /** Upcoming occurrences that still owe a reminder, ascending by start time, capped at [limit]. */
    suspend fun upcomingReminders(nowMillis: Long, limit: Int): List<ScheduleOccurrenceEntity> =
        occurrencesDao.getUpcoming(nowMillis)
            .asSequence()
            .filter { !it.completed && it.reminderMinutesBefore >= 0 }
            .take(limit)
            .toList()

    suspend fun markReminderFired(id: Long, firedAt: Long) = occurrencesDao.markReminderFired(id, firedAt)

    /** Re-fills the materialization window; call on start-up, after boot and when the day rolls over. */
    suspend fun refreshWindow(nowMillis: Long = now()) {
        materializer.materializeAll(nowMillis)
        onScheduleChanged()
    }

    /**
     * Edits one occurrence and detaches it. The recurrence rule is intentionally not taken from
     * [draft]: a single occurrence is not the rule holder, and the UI disables those controls under
     * this scope (design K-4).
     */
    private suspend fun updateThisOnly(
        target: ScheduleOccurrenceEntity,
        draft: ScheduleDraft,
        timestamp: Long,
    ): Long {
        occurrencesDao.update(
            target.copy(
                title = draft.title,
                category = draft.category.name,
                allDay = draft.allDay,
                startAt = draft.startAt,
                endAt = draft.endAt,
                reminderMinutesBefore = draft.reminderMinutesBefore,
                reminderMethod = draft.reminderMethod.name,
                detached = true,
                updatedAt = timestamp,
            ),
        )
        onScheduleChanged()
        return target.id
    }

    /** Splits the series: the old rule stops before [target], a new one starts at it. */
    private suspend fun updateThisAndFuture(
        target: ScheduleOccurrenceEntity,
        series: ScheduleSeriesEntity,
        draft: ScheduleDraft,
        timestamp: Long,
    ): Long {
        // Editing the series' own first occurrence is indistinguishable from editing all of them.
        if (target.originalStartAt <= series.startAt) return updateAll(target, series, draft, timestamp)

        seriesDao.truncate(series.id, target.originalStartAt - 1L, timestamp)
        occurrencesDao.softDeleteSeriesFrom(series.id, target.originalStartAt, timestamp)

        // "This and future" reaching a non-repeating rule collapses the tail into one entry.
        if (draft.recurrence == ScheduleRecurrence.NONE) {
            return insertStandaloneReplacingTarget(draft, timestamp, target.completed)
        }

        val newSeriesId = seriesDao.upsert(
            ScheduleSeriesEntity(
                title = draft.title,
                category = draft.category.name,
                allDay = draft.allDay,
                startAt = draft.startAt,
                endAt = draft.startAt + (draft.endAt - draft.startAt),
                recurrence = draft.recurrence.name,
                recurrenceUntil = draft.recurrenceUntil,
                reminderMinutesBefore = draft.reminderMinutesBefore,
                reminderMethod = draft.reminderMethod.name,
                createdAt = timestamp,
                updatedAt = timestamp,
            ),
        )
        materializer.materializeSeries(newSeriesId, timestamp)

        val created = occurrencesDao.getBySeries(newSeriesId)
        val anchorOccurrence = created.firstOrNull { it.originalStartAt == draft.startAt }
        if (target.completed && anchorOccurrence != null) {
            occurrencesDao.setCompleted(anchorOccurrence.id, true, timestamp, timestamp)
        }
        onScheduleChanged()
        return anchorOccurrence?.id ?: created.minByOrNull { it.startAt }?.id ?: 0L
    }

    /** Moves the whole series: the rule is retargeted and shifted by the edit's offset. */
    private suspend fun updateAll(
        target: ScheduleOccurrenceEntity,
        series: ScheduleSeriesEntity,
        draft: ScheduleDraft,
        timestamp: Long,
    ): Long {
        // Shifting is measured from originalStartAt so an already-detached occurrence still yields
        // the right offset when its own startAt was moved.
        val delta = draft.startAt - target.originalStartAt

        if (draft.recurrence == ScheduleRecurrence.NONE) {
            deleteSeries(series.id, timestamp)
            return insertStandaloneReplacingTarget(draft, timestamp, target.completed)
        }

        seriesDao.upsert(
            series.copy(
                title = draft.title,
                category = draft.category.name,
                allDay = draft.allDay,
                startAt = series.startAt + delta,
                endAt = series.endAt + delta,
                recurrence = draft.recurrence.name,
                recurrenceUntil = draft.recurrenceUntil,
                reminderMinutesBefore = draft.reminderMinutesBefore,
                reminderMethod = draft.reminderMethod.name,
                updatedAt = timestamp,
            ),
        )
        occurrencesDao.softDeleteSeriesGenerated(series.id, timestamp)
        materializer.materializeSeries(series.id, timestamp)
        onScheduleChanged()

        val regenerated = occurrencesDao.getBySeries(series.id)
        return regenerated.firstOrNull { it.originalStartAt == draft.startAt }?.id
            ?: regenerated.minByOrNull { it.startAt }?.id
            ?: series.id
    }

    private suspend fun deleteSeries(seriesId: Long, timestamp: Long) {
        occurrencesDao.softDeleteSeriesAll(seriesId, timestamp)
        seriesDao.softDelete(seriesId, timestamp)
    }

    private suspend fun insertStandaloneReplacingTarget(
        draft: ScheduleDraft,
        timestamp: Long,
        wasCompleted: Boolean,
    ): Long {
        val id = occurrencesDao.insert(standaloneOccurrence(draft, timestamp))
        if (wasCompleted) occurrencesDao.setCompleted(id, true, timestamp, timestamp)
        onScheduleChanged()
        return id
    }

    private fun standaloneOccurrence(draft: ScheduleDraft, timestamp: Long): ScheduleOccurrenceEntity =
        ScheduleOccurrenceEntity(
            seriesId = 0L,
            title = draft.title,
            category = draft.category.name,
            allDay = draft.allDay,
            startAt = draft.startAt,
            endAt = draft.endAt,
            reminderMinutesBefore = draft.reminderMinutesBefore,
            reminderMethod = draft.reminderMethod.name,
            recurrence = ScheduleRecurrence.NONE.name,
            recurrenceUntil = 0L,
            originalStartAt = draft.startAt,
            createdAt = timestamp,
            updatedAt = timestamp,
        )
}
