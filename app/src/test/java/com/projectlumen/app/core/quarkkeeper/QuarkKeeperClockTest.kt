package com.projectlumen.app.core.quarkkeeper

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [QuarkKeeperClock] is the only place the guard turns a wall-clock time into an instant, so every
 * case here names its zone: the runner's own default zone must not decide the outcome.
 */
class QuarkKeeperClockTest {

    private val zone = ZoneOffset.UTC
    private val day = LocalDate.of(2026, 1, 15)
    private val dayMillis = 86_400_000L

    private fun at(hour: Int, minute: Int): Long =
        day.atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun minuteOfDayReadsTheLocalWallClock() {
        assertEquals(0, QuarkKeeperClock.minuteOfDay(at(0, 0), zone))
        assertEquals(20 * 60, QuarkKeeperClock.minuteOfDay(at(20, 0), zone))
        assertEquals(22 * 60 + 30, QuarkKeeperClock.minuteOfDay(at(22, 30), zone))
        assertEquals(23 * 60 + 59, QuarkKeeperClock.minuteOfDay(at(23, 59), zone))
    }

    @Test
    fun nextOccurrenceStaysOnTheSameDayWhenTheNodeIsStillAhead() {
        assertEquals(at(20, 0), QuarkKeeperClock.nextOccurrenceOf(20 * 60, at(19, 59), zone))
        assertEquals(at(20, 0), QuarkKeeperClock.nextOccurrenceOf(20 * 60, at(0, 1), zone))
    }

    /**
     * The boundary is strict: a node that is exactly "now" has already passed. An alarm armed at the
     * current instant is in the past by the time the platform reads the clock and would never fire,
     * so rolling to tomorrow is the only answer that keeps the chain alive.
     */
    @Test
    fun nextOccurrenceRollsToTomorrowOnceTheNodeHasPassed() {
        assertEquals(at(20, 0) + dayMillis, QuarkKeeperClock.nextOccurrenceOf(20 * 60, at(20, 0), zone))
        assertEquals(at(20, 0) + dayMillis, QuarkKeeperClock.nextOccurrenceOf(20 * 60, at(23, 0), zone))
    }

    @Test
    fun endOfDayIsTheNextLocalMidnight() {
        assertEquals(at(0, 0) + dayMillis, QuarkKeeperClock.endOfDayMillis(at(0, 0), zone))
        assertEquals(at(0, 0) + dayMillis, QuarkKeeperClock.endOfDayMillis(at(22, 30), zone))
        assertEquals(at(0, 0) + dayMillis, QuarkKeeperClock.endOfDayMillis(at(23, 59), zone))
    }

    @Test
    fun remainingRoundsPartialMinutesUp() {
        val remaining = QuarkKeeperClock.remainingUntilEndOfDay(at(22, 30), zone)
        assertEquals(90, remaining.totalMinutes)
        assertEquals(1, remaining.hours)
        assertEquals(30, remaining.minutes)
    }

    /**
     * Rounding up is what keeps the copy honest: the last thirty seconds of the day are not "0 min
     * left", and a caption that reads zero while the streak is still alive would invite the user to
     * give up.
     */
    @Test
    fun remainingNeverRoundsTheFinalSecondsDownToZero() {
        val almostMidnight = at(23, 59) + 30_000L
        assertEquals(1, QuarkKeeperClock.remainingUntilEndOfDay(almostMidnight, zone).totalMinutes)
    }

    @Test
    fun snoozeStaysOpenUntilTheCutoffMinute() {
        val settings = QuarkKeeperSettings()
        assertTrue(QuarkKeeperClock.isSnoozeAllowed(settings, at(22, 30), zone))
        assertTrue(QuarkKeeperClock.isSnoozeAllowed(settings, at(23, 29), zone))
        assertFalse(QuarkKeeperClock.isSnoozeAllowed(settings, at(23, 30), zone))
        assertFalse(QuarkKeeperClock.isSnoozeAllowed(settings, at(23, 59), zone))
    }
}
