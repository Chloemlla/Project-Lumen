package com.projectlumen.app.core.schedule

import com.projectlumen.app.core.database.entities.ScheduleSeriesEntity
import com.projectlumen.app.core.enums.ScheduleRecurrence
import java.time.DayOfWeek
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Expands a repeating series into the concrete start times inside a window.
 *
 * Pure Kotlin on top of `java.time`: no Android dependency, so it runs in plain JVM unit tests.
 * All arithmetic goes through [ZonedDateTime] so wall-clock times survive DST transitions (`06:30`
 * stays `06:30` even when the UTC offset changes). Adding raw milliseconds would drift by an hour.
 */
object ScheduleRecurrenceExpander {
    const val MAX_OCCURRENCES_PER_SERIES = 400

    /** Iteration budget per produced occurrence, so a mostly-skipping rule cannot spin forever. */
    private const val ITERATION_FACTOR = 12
    private const val ITERATION_SLACK = 32

    /**
     * Returns the ascending start times this series produces inside `[fromMillis, toMillis)`.
     *
     * [limit] caps the returned list, which is what keeps `recurrenceUntil = 0` bounded.
     */
    fun expand(
        series: ScheduleSeriesEntity,
        fromMillis: Long,
        toMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
        limit: Int = MAX_OCCURRENCES_PER_SERIES,
    ): List<Long> {
        if (limit <= 0 || toMillis <= fromMillis) return emptyList()

        val recurrence = recurrenceOf(series.recurrence)
        if (recurrence == ScheduleRecurrence.NONE) {
            return if (series.startAt >= fromMillis && series.startAt < toMillis) {
                listOf(series.startAt)
            } else {
                emptyList()
            }
        }

        val anchor = Instant.ofEpochMilli(series.startAt).atZone(zoneId)
        val starts = ArrayList<Long>()
        val maxIterations = limit.toLong() * ITERATION_FACTOR + ITERATION_SLACK
        var index = startIndex(anchor, recurrence, fromMillis, zoneId)
        var iterations = 0L

        while (starts.size < limit && iterations < maxIterations) {
            iterations++
            val occurrence = occurrenceAt(anchor, recurrence, index, zoneId)
            index++
            // A step that lands on a day the month does not have (Jan 31 -> February) is skipped
            // entirely rather than clamped to the month end, matching Google Calendar BYMONTHDAY.
            if (occurrence == null || isExcludedWeekday(recurrence, occurrence)) continue
            val startMillis = occurrence.toInstant().toEpochMilli()
            if (series.recurrenceUntil > 0L && startMillis > series.recurrenceUntil) break
            if (startMillis >= toMillis) break
            if (startMillis >= fromMillis) starts.add(startMillis)
        }
        return starts
    }

    private fun recurrenceOf(value: String): ScheduleRecurrence =
        ScheduleRecurrence.entries.firstOrNull { it.name == value } ?: ScheduleRecurrence.NONE

    private fun isExcludedWeekday(recurrence: ScheduleRecurrence, occurrence: ZonedDateTime): Boolean {
        if (recurrence != ScheduleRecurrence.WEEKDAYS) return false
        val day = occurrence.dayOfWeek
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY
    }

    /**
     * Step index to start iterating from: the first step at or just before the window, never before
     * the anchor. One step is deliberately backed off so an alignment rounding error cannot drop an
     * occurrence that sits exactly on the window edge. Without this a series anchored years ago
     * would be walked day by day from its anchor.
     */
    private fun startIndex(
        anchor: ZonedDateTime,
        recurrence: ScheduleRecurrence,
        fromMillis: Long,
        zoneId: ZoneId,
    ): Long {
        val from = Instant.ofEpochMilli(fromMillis).atZone(zoneId)
        val offset = when (recurrence) {
            ScheduleRecurrence.NONE -> 0L
            ScheduleRecurrence.DAILY, ScheduleRecurrence.WEEKDAYS ->
                ChronoUnit.DAYS.between(anchor.toLocalDate(), from.toLocalDate())

            ScheduleRecurrence.WEEKLY ->
                ChronoUnit.DAYS.between(anchor.toLocalDate(), from.toLocalDate()) / 7L

            ScheduleRecurrence.MONTHLY ->
                ChronoUnit.MONTHS.between(YearMonth.from(anchor), YearMonth.from(from))

            ScheduleRecurrence.YEARLY -> (from.year - anchor.year).toLong()
        }
        return if (offset <= 1L) 0L else offset - 1L
    }

    private fun occurrenceAt(
        anchor: ZonedDateTime,
        recurrence: ScheduleRecurrence,
        index: Long,
        zoneId: ZoneId,
    ): ZonedDateTime? = when (recurrence) {
        ScheduleRecurrence.NONE -> anchor
        ScheduleRecurrence.DAILY, ScheduleRecurrence.WEEKDAYS -> anchor.plusDays(index)
        ScheduleRecurrence.WEEKLY -> anchor.plusWeeks(index)

        // Rebuilt from year/month/day instead of plusMonths/plusYears: those clamp the day of month
        // (Jan 31 + 1 month = Feb 28), which would silently shift every later occurrence.
        ScheduleRecurrence.MONTHLY -> if (index == 0L) anchor else monthOccurrence(anchor, index, zoneId)
        ScheduleRecurrence.YEARLY -> if (index == 0L) anchor else yearOccurrence(anchor, index, zoneId)
    }

    private fun monthOccurrence(anchor: ZonedDateTime, index: Long, zoneId: ZoneId): ZonedDateTime? {
        val month = YearMonth.from(anchor).plusMonths(index)
        if (!month.isValidDay(anchor.dayOfMonth)) return null
        return month.atDay(anchor.dayOfMonth).atTime(anchor.toLocalTime()).atZone(zoneId)
    }

    private fun yearOccurrence(anchor: ZonedDateTime, index: Long, zoneId: ZoneId): ZonedDateTime? {
        val month = YearMonth.of(anchor.year + index.toInt(), anchor.monthValue)
        if (!month.isValidDay(anchor.dayOfMonth)) return null
        return month.atDay(anchor.dayOfMonth).atTime(anchor.toLocalTime()).atZone(zoneId)
    }
}
