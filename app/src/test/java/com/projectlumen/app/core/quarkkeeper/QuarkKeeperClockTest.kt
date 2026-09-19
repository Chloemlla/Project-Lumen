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

    /**
     * The sixty seconds are the spec's, so they are written out rather than read from the constant: a
     * window that quietly grew to two minutes would still pass a test that asked the code what its own
     * window was.
     *
     * The boundary itself is closed. At exactly sixty seconds the return monitor fires and the forced
     * alert takes the screen, so a card appearing at that same instant would be fighting the alert it is
     * meant to spare the user.
     */
    @Test
    fun returnConfirmationIsOpenForTheSixtySecondsAfterTheHandOff() {
        val handOff = at(22, 30)
        assertTrue(QuarkKeeperClock.isReturnConfirmationOpen(handOff, handOff + 5_000L))
        assertTrue(QuarkKeeperClock.isReturnConfirmationOpen(handOff, handOff + 59_999L))
        assertFalse(QuarkKeeperClock.isReturnConfirmationOpen(handOff, handOff + 60_000L))
        assertFalse(QuarkKeeperClock.isReturnConfirmationOpen(handOff, handOff + 60_001L))
    }

    @Test
    fun returnConfirmationIsClosedWhenNobodyWasSentToQuark() {
        assertFalse(QuarkKeeperClock.isReturnConfirmationOpen(0L, at(22, 30)))
    }

    /**
     * A stamp later than "now" means the system clock moved backwards under the guard. Without this the
     * card would stay open for however long the device took to catch up, and indefinitely if the clock
     * was set back by a year — a prompt with no way to expire is worse than no prompt.
     */
    @Test
    fun returnConfirmationIsClosedWhenTheClockMovedBackwards() {
        val handOff = at(22, 30)
        assertFalse(QuarkKeeperClock.isReturnConfirmationOpen(handOff, handOff - 1_000L))
    }

    @Test
    fun snoozeExpiryIsTheRequestedDelayWhileTheDayHasRoomForIt() {
        assertEquals(at(20, 5), QuarkKeeperClock.snoozeExpiry(at(20, 0), 5, zone))
        assertEquals(at(23, 29), QuarkKeeperClock.snoozeExpiry(at(22, 30), 59, zone))
    }

    /**
     * The cutoff is 23:30, but the longest snooze setting is an hour, so the cutoff alone does not keep
     * a postponement inside the day. A user who postpones at 23:29 is asking for 00:29 — a row that
     * belongs to today, stamped with an instant on tomorrow — and the alarm would then fire on the new
     * day and raise the forced alert at half past midnight with the whole evening still ahead. The day's
     * last millisecond is the honest maximum.
     */
    @Test
    fun snoozeExpiryNeverLandsPastTheEndOfTheDay() {
        val lastMillisecondOfDay = at(0, 0) + dayMillis - 1L
        assertEquals(lastMillisecondOfDay, QuarkKeeperClock.snoozeExpiry(at(23, 29), 60, zone))
        assertEquals(lastMillisecondOfDay, QuarkKeeperClock.snoozeExpiry(at(23, 59), 5, zone))
    }

    /**
     * The cap can only shorten a snooze, never push it out: the last millisecond of the day is always
     * still ahead of a "now" that is inside the day, so a granted postponement can never come back due
     * at the instant it was taken.
     */
    @Test
    fun snoozeExpiryStaysInTheFutureForAnyInstantInsideTheDay() {
        for (minute in listOf(0, 20 * 60, 23 * 60 + 29, 23 * 60 + 59)) {
            val now = at(minute / 60, minute % 60)
            assertTrue(
                "snooze asked for at minute $minute expired immediately",
                QuarkKeeperClock.snoozeExpiry(now, 5, zone) > now,
            )
            assertTrue(
                "snooze asked for at minute $minute expired immediately",
                QuarkKeeperClock.snoozeExpiry(now, 60, zone) > now,
            )
            assertTrue(QuarkKeeperClock.snoozeExpiry(now, 60, zone) <= QuarkKeeperClock.endOfDayMillis(now, zone))
        }
    }
}
