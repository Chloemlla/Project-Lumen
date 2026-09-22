package com.projectlumen.app.core.schedule

import com.projectlumen.app.core.database.daos.ScheduleOccurrencesDao
import com.projectlumen.app.core.database.daos.ScheduleSeriesDao
import com.projectlumen.app.core.database.entities.ScheduleOccurrenceEntity
import com.projectlumen.app.core.time.LumenTimeZone
import java.time.ZoneId

/**
 * Keeps `schedule_occurrences` in sync with the series rules for a rolling window around "now".
 *
 * Only generated rows are touched: an occurrence the user edited ([ScheduleOccurrenceEntity.detached])
 * or completed is never regenerated or pruned away, because its `originalStartAt` stays in the
 * "already materialized" set and its row is excluded from the prune query.
 */
class ScheduleMaterializer(
    private val seriesDao: ScheduleSeriesDao,
    private val occurrencesDao: ScheduleOccurrencesDao,
    private val zoneId: () -> ZoneId = LumenTimeZone::zoneId,
) {
    suspend fun materializeAll(nowMillis: Long = System.currentTimeMillis()) {
        seriesDao.getActive().forEach { series -> materializeSeries(series.id, nowMillis) }
        // Standalone entries (seriesId = 0) are left untouched by design: nothing to expand or prune.
    }

    suspend fun materializeSeries(seriesId: Long, nowMillis: Long = System.currentTimeMillis()) {
        val series = seriesDao.get(seriesId) ?: return
        if (series.deletedAt != 0L) return

        val from = nowMillis - PAST_WINDOW_DAYS * MILLIS_PER_DAY
        val to = nowMillis + FUTURE_WINDOW_DAYS * MILLIS_PER_DAY
        val expected = ScheduleRecurrenceExpander.expand(series, from, to, zoneId())
        val existing = occurrencesDao.getOriginalStartAts(seriesId).toHashSet()
        val duration = series.endAt - series.startAt

        val missing = expected
            .filterNot { it in existing }
            .map { startAt ->
                ScheduleOccurrenceEntity(
                    seriesId = series.id,
                    title = series.title,
                    category = series.category,
                    allDay = series.allDay,
                    startAt = startAt,
                    endAt = startAt + duration,
                    reminderMinutesBefore = series.reminderMinutesBefore,
                    reminderMethod = series.reminderMethod,
                    recurrence = series.recurrence,
                    recurrenceUntil = series.recurrenceUntil,
                    originalStartAt = startAt,
                    createdAt = nowMillis,
                    updatedAt = nowMillis,
                )
            }
        if (missing.isNotEmpty()) occurrencesDao.insertAll(missing)

        occurrencesDao.pruneGeneratedOutsideWindow(seriesId, from, to, nowMillis)
    }

    companion object {
        const val PAST_WINDOW_DAYS = 7L
        const val FUTURE_WINDOW_DAYS = 60L

        private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
    }
}
