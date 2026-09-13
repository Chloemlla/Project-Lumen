package com.projectlumen.app.core.schedule

import com.projectlumen.app.core.database.entities.ScheduleSeriesEntity
import com.projectlumen.app.core.enums.ScheduleCategory
import com.projectlumen.app.core.enums.ScheduleRecurrence
import com.projectlumen.app.core.enums.ScheduleReminderMethod
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Every case pins its own [ZoneId]: CI runs in UTC while a developer machine is usually not, so a
 * test relying on the default zone would diverge.
 */
class ScheduleRecurrenceExpanderTest {
    private val shanghai = ZoneId.of("Asia/Shanghai")
    private val utc = ZoneId.of("UTC")
    private val newYork = ZoneId.of("America/New_York")

    private val oneHour = 3_600_000L

    private fun at(
        zoneId: ZoneId,
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 0,
        minute: Int = 0,
        second: Int = 0,
        nano: Int = 0,
    ): Long = ZonedDateTime.of(LocalDateTime.of(year, month, day, hour, minute, second, nano), zoneId)
        .toInstant()
        .toEpochMilli()

    private fun series(
        startAt: Long,
        endAt: Long,
        recurrence: ScheduleRecurrence,
        recurrenceUntil: Long = 0L,
    ): ScheduleSeriesEntity = ScheduleSeriesEntity(
        id = 1L,
        title = "Test",
        category = ScheduleCategory.PERSONAL.name,
        allDay = false,
        startAt = startAt,
        endAt = endAt,
        recurrence = recurrence.name,
        recurrenceUntil = recurrenceUntil,
        reminderMinutesBefore = -1,
        reminderMethod = ScheduleReminderMethod.NOTIFICATION.name,
        enabled = true,
        createdAt = startAt,
        updatedAt = startAt,
        deletedAt = 0L,
    )

    private fun zoned(zoneId: ZoneId, millis: Long): ZonedDateTime =
        Instant.ofEpochMilli(millis).atZone(zoneId)

    // T-1
    @Test
    fun dailyOccurrencesKeepWallClockTimeAndDuration() {
        val start = at(shanghai, 2026, 9, 13, 6, 30)
        val end = at(shanghai, 2026, 9, 13, 7, 30)
        val subject = series(start, end, ScheduleRecurrence.DAILY)

        val result = ScheduleRecurrenceExpander.expand(
            subject,
            fromMillis = start,
            toMillis = at(shanghai, 2026, 9, 16),
            zoneId = shanghai,
        )

        assertEquals(3, result.size)
        assertEquals(listOf(13, 14, 15), result.map { zoned(shanghai, it).dayOfMonth })
        result.forEach { occurrenceStart ->
            val occurrenceStartLocal = zoned(shanghai, occurrenceStart)
            val occurrenceEndLocal = zoned(shanghai, occurrenceStart + (end - start))
            assertEquals(6, occurrenceStartLocal.hour)
            assertEquals(30, occurrenceStartLocal.minute)
            assertEquals(7, occurrenceEndLocal.hour)
            assertEquals(30, occurrenceEndLocal.minute)
        }
    }

    // T-2
    @Test
    fun weekdaysSeriesAnchoredOnSaturdayStartsOnMonday() {
        val saturday = at(shanghai, 2026, 9, 12, 9, 0)
        val subject = series(saturday, saturday + oneHour, ScheduleRecurrence.WEEKDAYS)

        val result = ScheduleRecurrenceExpander.expand(
            subject,
            fromMillis = saturday,
            toMillis = at(shanghai, 2026, 9, 19, 9, 0),
            zoneId = shanghai,
        )

        assertEquals(5, result.size)
        result.forEach { millis ->
            val day = zoned(shanghai, millis).dayOfWeek
            assertFalse(day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY)
        }
        val first = zoned(shanghai, result.first())
        assertEquals(DayOfWeek.MONDAY, first.dayOfWeek)
        assertEquals(14, first.dayOfMonth)
        assertEquals(9, first.hour)
    }

    // T-3
    @Test
    fun weeklyOccurrencesStayOnTheAnchorWeekdayAndTime() {
        val anchor = at(shanghai, 2026, 9, 13, 12, 0)
        val subject = series(anchor, anchor + oneHour, ScheduleRecurrence.WEEKLY)

        val result = ScheduleRecurrenceExpander.expand(
            subject,
            fromMillis = anchor,
            toMillis = at(shanghai, 2026, 10, 11, 12, 0),
            zoneId = shanghai,
        )

        assertEquals(4, result.size)
        assertEquals(listOf(13, 20, 27), result.take(3).map { zoned(shanghai, it).dayOfMonth })
        result.forEach { millis ->
            val local = zoned(shanghai, millis)
            assertEquals(DayOfWeek.SUNDAY, local.dayOfWeek)
            assertEquals(12, local.hour)
            assertEquals(0, local.minute)
        }
    }

    // T-4
    @Test
    fun monthlySkipsMonthsThatHaveNoSuchDayOfMonth() {
        val anchor = at(utc, 2026, 1, 31, 8, 0)
        val subject = series(anchor, anchor + oneHour, ScheduleRecurrence.MONTHLY)

        val result = ScheduleRecurrenceExpander.expand(
            subject,
            fromMillis = at(utc, 2026, 1, 1),
            toMillis = at(utc, 2026, 4, 15),
            zoneId = utc,
        )

        // February has no 31st, so it is skipped rather than clamped to Feb 28.
        assertEquals(listOf(1, 3), result.map { zoned(utc, it).monthValue })
        result.forEach { assertEquals(31, zoned(utc, it).dayOfMonth) }
    }

    // T-5
    @Test
    fun monthlyProducesEveryMonthForAnExistingDayOfMonth() {
        val anchor = at(utc, 2026, 1, 15, 8, 0)
        val subject = series(anchor, anchor + oneHour, ScheduleRecurrence.MONTHLY)

        val result = ScheduleRecurrenceExpander.expand(
            subject,
            fromMillis = at(utc, 2026, 1, 1),
            toMillis = at(utc, 2026, 4, 1),
            zoneId = utc,
        )

        assertEquals(listOf(1, 2, 3), result.map { zoned(utc, it).monthValue })
        result.forEach { assertEquals(15, zoned(utc, it).dayOfMonth) }
    }

    // T-6
    @Test
    fun yearlyLeapDaySkipsCommonYears() {
        val anchor = at(utc, 2024, 2, 29, 10, 0)
        val subject = series(anchor, anchor + oneHour, ScheduleRecurrence.YEARLY)

        val result = ScheduleRecurrenceExpander.expand(
            subject,
            fromMillis = at(utc, 2024, 1, 1),
            toMillis = at(utc, 2029, 1, 1),
            zoneId = utc,
        )

        assertEquals(listOf(2024, 2028), result.map { zoned(utc, it).year })
        result.forEach { millis ->
            val local = zoned(utc, millis)
            assertEquals(2, local.monthValue)
            assertEquals(29, local.dayOfMonth)
            assertEquals(10, local.hour)
        }
    }

    // T-7
    @Test
    fun recurrenceUntilCoversTheWholeFinalDay() {
        val anchor = at(utc, 2026, 1, 1, 9, 0)
        val subject = series(
            anchor,
            anchor + oneHour,
            ScheduleRecurrence.DAILY,
            recurrenceUntil = at(utc, 2026, 1, 3, 23, 59, 59, 999_000_000),
        )

        val result = ScheduleRecurrenceExpander.expand(
            subject,
            fromMillis = at(utc, 2026, 1, 1),
            toMillis = at(utc, 2026, 1, 10),
            zoneId = utc,
        )

        assertEquals(listOf(1, 2, 3), result.map { zoned(utc, it).dayOfMonth })
    }

    // T-8
    @Test
    fun neverEndingSeriesIsBoundedByLimit() {
        val anchor = at(utc, 2026, 1, 1, 9, 0)
        val subject = series(anchor, anchor + oneHour, ScheduleRecurrence.DAILY, recurrenceUntil = 0L)

        val capped = ScheduleRecurrenceExpander.expand(
            subject,
            fromMillis = anchor,
            toMillis = at(utc, 2200, 1, 1),
            zoneId = utc,
            limit = 10,
        )
        assertEquals(10, capped.size)

        val defaulted = ScheduleRecurrenceExpander.expand(
            subject,
            fromMillis = anchor,
            toMillis = at(utc, 2200, 1, 1),
            zoneId = utc,
        )
        assertEquals(ScheduleRecurrenceExpander.MAX_OCCURRENCES_PER_SERIES, defaulted.size)
    }

    // T-9
    @Test
    fun windowInsideTheSeriesReturnsOnlyThatSliceAlignedToTheAnchor() {
        val anchor = at(shanghai, 2026, 1, 1, 6, 30)
        val subject = series(anchor, anchor + oneHour, ScheduleRecurrence.DAILY)

        val result = ScheduleRecurrenceExpander.expand(
            subject,
            fromMillis = at(shanghai, 2026, 3, 15),
            toMillis = at(shanghai, 2026, 3, 18),
            zoneId = shanghai,
        )

        assertEquals(listOf(15, 16, 17), result.map { zoned(shanghai, it).dayOfMonth })
        result.forEach { millis ->
            val local = zoned(shanghai, millis)
            assertEquals(6, local.hour)
            assertEquals(30, local.minute)
        }
        // Jan 1 + 73 days lands on Mar 15; the jump must not shift the occurrence.
        assertEquals(anchor + 73L * 24L * 3_600_000L, result.first())
    }

    // T-10
    @Test
    fun dailyKeepsLocalTimeAcrossDaylightSavingTransitions() {
        val anchor = at(newYork, 2026, 3, 6, 6, 30)
        val subject = series(anchor, anchor + oneHour, ScheduleRecurrence.DAILY)

        val result = ScheduleRecurrenceExpander.expand(
            subject,
            fromMillis = anchor,
            toMillis = at(newYork, 2026, 3, 10),
            zoneId = newYork,
        )

        assertEquals(4, result.size)
        result.forEach { millis ->
            val local = zoned(newYork, millis)
            assertEquals(6, local.hour)
            assertEquals(30, local.minute)
        }
        assertEquals("-05:00", zoned(newYork, result.first()).offset.id)
        assertEquals("-04:00", zoned(newYork, result.last()).offset.id)
        // The clock jumps forward on Mar 8 2026, so that step is 23 hours of real time.
        assertEquals(24L * 3_600_000L, result[1] - result[0])
        assertEquals(23L * 3_600_000L, result[2] - result[1])
    }
}
