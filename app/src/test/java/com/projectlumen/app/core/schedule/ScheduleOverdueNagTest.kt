package com.projectlumen.app.core.schedule

import com.projectlumen.app.core.database.entities.ScheduleOccurrenceEntity
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ScheduleOverdueNag] works in raw epoch milliseconds, so these cases behave the same on a UTC CI
 * runner and on a developer machine.
 *
 * The exception is the morning/evening pair ([ScheduleOverdueNag.endedInMorning],
 * [ScheduleOverdueNag.nextEveningNagAt]): those are defined against local wall-clock time and
 * therefore take an explicit [ZoneId]. Every zone-dependent case names its zone, so the outcome is
 * still independent of the runner's own default zone.
 */
class ScheduleOverdueNagTest {
    private val now = 1_760_000_000_000L
    private val minute = 60_000L
    private val day = 86_400_000L

    private fun occurrence(
        endAt: Long,
        completed: Boolean = false,
        deletedAt: Long = 0L,
        startAt: Long = endAt - minute,
    ): ScheduleOccurrenceEntity = ScheduleOccurrenceEntity(
        title = "Test",
        startAt = startAt,
        endAt = endAt,
        originalStartAt = startAt,
        completed = completed,
        deletedAt = deletedAt,
        createdAt = startAt,
        updatedAt = startAt,
    )

    // N-1
    @Test
    fun overdueWhenEndPassedAndStillOpen() {
        val subject = occurrence(endAt = now - minute)

        assertTrue(ScheduleOverdueNag.isOverdue(subject, now))
    }

    // N-2
    @Test
    fun notOverdueWhenEndEqualsNow() {
        val subject = occurrence(endAt = now)

        assertFalse(ScheduleOverdueNag.isOverdue(subject, now))
    }

    // N-3
    @Test
    fun notOverdueWhenEndInFuture() {
        val subject = occurrence(endAt = now + minute)

        assertFalse(ScheduleOverdueNag.isOverdue(subject, now))
    }

    // N-4
    @Test
    fun notOverdueWhenCompleted() {
        val subject = occurrence(endAt = now - minute, completed = true)

        assertFalse(ScheduleOverdueNag.isOverdue(subject, now))
    }

    // N-5
    @Test
    fun notOverdueWhenSoftDeleted() {
        val subject = occurrence(endAt = now - minute, deletedAt = now - minute)

        assertFalse(ScheduleOverdueNag.isOverdue(subject, now))
    }

    // N-6
    @Test
    fun notOverdueWhenOlderThanMaxAge() {
        val subject = occurrence(endAt = now - ScheduleOverdueNag.MAX_OVERDUE_AGE_DAYS * day - 1L)

        assertFalse(ScheduleOverdueNag.isOverdue(subject, now))
    }

    /**
     * The age cutoff is `<=`, so `now - endAt` of exactly 30 days is still inside it. This pins the
     * boundary that N-6 steps one millisecond past; without it a `>` -vs- `>=` slip would go unnoticed.
     */
    @Test
    fun stillOverdueAtExactlyMaxAge() {
        val subject = occurrence(endAt = now - ScheduleOverdueNag.MAX_OVERDUE_AGE_DAYS * day)

        assertTrue(ScheduleOverdueNag.isOverdue(subject, now))
    }

    // N-7
    @Test
    fun overdueJustInsideMaxAge() {
        val subject = occurrence(endAt = now - 29 * day)

        assertTrue(ScheduleOverdueNag.isOverdue(subject, now))
    }

    // N-8
    @Test
    fun overdueItemsSortByEndAndDropEverythingElse() {
        val newest = occurrence(endAt = now - minute)
        val oldest = occurrence(endAt = now - 3 * day)
        val middle = occurrence(endAt = now - day)
        val completed = occurrence(endAt = now - 2 * day, completed = true)
        val deleted = occurrence(endAt = now - 2 * day, deletedAt = now - day)
        val future = occurrence(endAt = now + day)

        val result = ScheduleOverdueNag.overdueItems(
            listOf(newest, completed, oldest, middle, future, deleted),
            now,
        )

        assertEquals(listOf(oldest.endAt, middle.endAt, newest.endAt), result.map { it.endAt })
    }

    // N-9
    @Test
    fun clampIntervalMinutesHoldsRange() {
        assertEquals(5, ScheduleOverdueNag.clampIntervalMinutes(1))
        assertEquals(5, ScheduleOverdueNag.clampIntervalMinutes(5))
        assertEquals(120, ScheduleOverdueNag.clampIntervalMinutes(120))
        assertEquals(360, ScheduleOverdueNag.clampIntervalMinutes(360))
        assertEquals(360, ScheduleOverdueNag.clampIntervalMinutes(9999))
    }

    // N-10
    @Test
    fun nextNagAtAddsClampedInterval() {
        assertEquals(now + 120 * minute, ScheduleOverdueNag.nextNagAt(now, 120))
        assertEquals(now + ScheduleOverdueNag.MIN_INTERVAL_MINUTES * minute, ScheduleOverdueNag.nextNagAt(now, 0))
    }

    private val shanghai: ZoneId = ZoneId.of("Asia/Shanghai")
    private val newYork: ZoneId = ZoneId.of("America/New_York")

    /** The instant at which the wall clock in [zone] reads the given local date and time. */
    private fun at(zone: ZoneId, year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    private fun localTimeOf(millis: Long, zone: ZoneId): LocalTime =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalTime()

    // N-11
    @Test
    fun endedInMorningForLocalMorningEnd() {
        val subject = occurrence(endAt = at(shanghai, 2026, 5, 12, 7, 30))

        assertTrue(ScheduleOverdueNag.endedInMorning(subject, shanghai))
    }

    // N-12
    @Test
    fun notEndedInMorningAtLocalNoon() {
        val subject = occurrence(endAt = at(shanghai, 2026, 5, 12, 12, 0))

        assertFalse(ScheduleOverdueNag.endedInMorning(subject, shanghai))
    }

    // N-13
    @Test
    fun notEndedInMorningForLocalEveningEnd() {
        val subject = occurrence(endAt = at(shanghai, 2026, 5, 12, 21, 0))

        assertFalse(ScheduleOverdueNag.endedInMorning(subject, shanghai))
    }

    /**
     * One instant, two verdicts. This is the case that pins "local time, not `endAt % 86_400_000`":
     * modulo arithmetic would read this instant's UTC offset (23:30) and call it a night-time end in
     * *both* zones.
     */
    // N-14
    @Test
    fun endedInMorningDependsOnZoneNotOnUtcOffset() {
        val endAt = at(shanghai, 2026, 5, 12, 7, 30)
        val subject = occurrence(endAt = endAt)

        assertTrue(ScheduleOverdueNag.endedInMorning(subject, shanghai))
        assertFalse(ScheduleOverdueNag.endedInMorning(subject, newYork))
        assertEquals(LocalTime.of(19, 30), localTimeOf(endAt, newYork))
    }

    // N-15
    @Test
    fun nextEveningNagIsTodayWhenStillAhead() {
        val nowMillis = at(shanghai, 2026, 5, 12, 9, 0)

        val result = ScheduleOverdueNag.nextEveningNagAt(nowMillis, 1290, shanghai)

        assertEquals(at(shanghai, 2026, 5, 12, 21, 30), result)
    }

    // N-16
    @Test
    fun nextEveningNagRollsToTomorrowWhenAlreadyPast() {
        val nowMillis = at(shanghai, 2026, 5, 12, 22, 0)

        val result = ScheduleOverdueNag.nextEveningNagAt(nowMillis, 1290, shanghai)

        assertEquals(at(shanghai, 2026, 5, 13, 21, 30), result)
    }

    /**
     * 2026-03-08 is the US spring-forward day. The night before it, at 23:00 EST, the next 21:30 is
     * only 21.5 hours away instead of 22.5 — an implementation that adds wall-clock minutes or a
     * fixed 24 hours would land on 20:30 or 22:30. The assertion is on the local reading, so it
     * fails unless the nag is still pinned to 21:30 local.
     */
    // N-17
    @Test
    fun nextEveningNagKeepsWallClockAcrossDstTransition() {
        val nowMillis = at(newYork, 2026, 3, 7, 23, 0)
        assertEquals(ZoneOffset.ofHours(-5), ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), newYork).offset)

        val result = ScheduleOverdueNag.nextEveningNagAt(nowMillis, 1290, newYork)

        val resultLocal = Instant.ofEpochMilli(result).atZone(newYork)
        assertEquals(LocalTime.of(21, 30), resultLocal.toLocalTime())
        assertEquals(2026, resultLocal.year)
        assertEquals(3, resultLocal.monthValue)
        assertEquals(8, resultLocal.dayOfMonth)
        assertEquals(ZoneOffset.ofHours(-4), resultLocal.offset)
    }
}
