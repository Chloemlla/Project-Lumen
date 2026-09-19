package com.projectlumen.app.core.quarkkeeper

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The dashboard, the statistics and the VIP estimate are all derived from the same log, so these
 * cases pin the derivation rather than any particular screen: a change that moves a milestone badge
 * has to show up here first.
 */
class QuarkKeeperCalendarTest {

    private val anchor = "2026-01-01"

    private fun record(dateKey: String, millis: Long = 1_000L) =
        QuarkKeeperCheckinRecord(dateKey = dateKey, checkedInAtMillis = millis)

    /** A consecutive run of check-ins starting at [fromDate], which is how a real streak looks. */
    private fun run(fromDate: String, days: Int): List<QuarkKeeperCheckinRecord> {
        val start = LocalDate.parse(fromDate)
        return (0 until days).map { offset ->
            record(start.plusDays(offset.toLong()).toString())
        }
    }

    @Test
    fun dateKeysParseOnlyWhenTheyAreRealDates() {
        assertEquals(LocalDate.of(2026, 1, 1), QuarkKeeperCalendar.parseDateKey("2026-01-01"))
        assertNull(QuarkKeeperCalendar.parseDateKey("not-a-date"))
        assertNull(QuarkKeeperCalendar.parseDateKey(""))
    }

    @Test
    fun theAnchorIsTheEarliestRecordedDay() {
        assertEquals(
            "2026-01-05",
            QuarkKeeperCalendar.anchorDateKey(listOf(record("2026-03-01"), record("2026-01-05"))),
        )
        assertNull(QuarkKeeperCalendar.anchorDateKey(emptyList()))
    }

    @Test
    fun cycleDayCountsFromTheAnchorAndWrapsAfterThirtyDays() {
        assertEquals(1, QuarkKeeperCalendar.cycleDay("2026-01-01", anchor))
        assertEquals(7, QuarkKeeperCalendar.cycleDay("2026-01-07", anchor))
        assertEquals(30, QuarkKeeperCalendar.cycleDay("2026-01-30", anchor))
        assertEquals(1, QuarkKeeperCalendar.cycleDay("2026-01-31", anchor))
        assertEquals(0, QuarkKeeperCalendar.cycleIndex("2026-01-30", anchor))
        assertEquals(1, QuarkKeeperCalendar.cycleIndex("2026-01-31", anchor))
    }

    @Test
    fun theMilestoneBadgesSitOnTheRewardDays() {
        assertEquals(listOf(7, 14, 21, 30), QuarkKeeperCalendar.MILESTONE_DAYS)

        val cycle = QuarkKeeperCalendar.buildCycle(run(anchor, 7), "2026-01-07", anchor)

        assertEquals(30, cycle.cells.size)
        assertEquals(7, cycle.completedDays)
        assertEquals(1, cycle.cells[6].milestoneVipDays)
        assertEquals(1, cycle.cells[13].milestoneVipDays)
        assertEquals(2, cycle.cells[20].milestoneVipDays)
        assertEquals(7, cycle.cells[29].milestoneVipDays)
        assertEquals(0, cycle.cells[0].milestoneVipDays)
    }

    @Test
    fun aCellIsCheckedInBeforeItIsJudgedPastOrFuture() {
        val cycle = QuarkKeeperCalendar.buildCycle(run(anchor, 7), "2026-01-03", anchor)

        assertEquals(QuarkKeeperDayState.CHECKED_IN, cycle.cells[2].state)
        assertEquals(QuarkKeeperDayState.CHECKED_IN, cycle.cells[6].state)
        assertEquals(QuarkKeeperDayState.UPCOMING, cycle.cells[7].state)
    }

    @Test
    fun gapsInTheCycleReadAsMissedAndTodayReadsAsPending() {
        val records = listOf(record("2026-01-01"), record("2026-01-02"))
        val cycle = QuarkKeeperCalendar.buildCycle(records, "2026-01-05", anchor)

        assertEquals(QuarkKeeperDayState.CHECKED_IN, cycle.cells[0].state)
        assertEquals(QuarkKeeperDayState.MISSED, cycle.cells[2].state)
        assertEquals(QuarkKeeperDayState.MISSED, cycle.cells[3].state)
        assertEquals(QuarkKeeperDayState.PENDING, cycle.cells[4].state)
        assertEquals(QuarkKeeperDayState.UPCOMING, cycle.cells[5].state)
        assertEquals(2, cycle.completedDays)
    }

    /**
     * With no log there is no anchor, so the grid cannot know where a cycle starts. It still renders
     * a full, empty cycle anchored on today rather than failing: the screen has to have something to
     * show before the first check-in exists, and today has to be the "pending" square the user acts on.
     */
    @Test
    fun anEmptyLogRendersAFullCycleAnchoredOnToday() {
        val cycle = QuarkKeeperCalendar.buildCycle(emptyList(), "2026-01-05", null)

        assertEquals(0, cycle.cycleIndex)
        assertEquals(30, cycle.cells.size)
        assertEquals(0, cycle.completedDays)
        assertEquals("2026-01-05", cycle.cells.first().dateKey)
        assertEquals(QuarkKeeperDayState.PENDING, cycle.cells.first().state)
        assertTrue(cycle.cells.none { cell -> cell.state == QuarkKeeperDayState.CHECKED_IN })
    }

    @Test
    fun anEmptyLogProducesZeroedStatistics() {
        val stats = QuarkKeeperCalendar.buildStats(emptyList(), "2026-01-05", null)

        assertEquals(0, stats.currentStreakDays)
        assertEquals(0, stats.longestStreakDays)
        assertEquals(0, stats.totalCheckinDays)
        assertEquals(0, stats.estimatedVipDays)
        assertTrue(stats.history.isEmpty())
    }

    @Test
    fun statisticsCountConsecutiveDaysNotRecords() {
        val stats = QuarkKeeperCalendar.buildStats(run(anchor, 5), "2026-01-05", anchor)

        assertEquals(5, stats.currentStreakDays)
        assertEquals(5, stats.longestStreakDays)
        assertEquals(5, stats.totalCheckinDays)
        assertEquals(0, stats.estimatedVipDays)
    }

    @Test
    fun aGapBreaksBothTheLongestAndTheCurrentStreak() {
        val records = listOf(
            record("2026-01-01"),
            record("2026-01-02"),
            record("2026-01-04"),
            record("2026-01-05"),
        )
        val stats = QuarkKeeperCalendar.buildStats(records, "2026-01-05", anchor)

        assertEquals(2, stats.longestStreakDays)
        assertEquals(2, stats.currentStreakDays)
        assertEquals(4, stats.totalCheckinDays)
    }

    /**
     * An unfinished today must not read as a lost streak: the streak only breaks once a whole day has
     * gone by without a check-in, so the count walks back from yesterday.
     */
    @Test
    fun theCurrentStreakSurvivesAnUnfinishedToday() {
        val stats = QuarkKeeperCalendar.buildStats(run(anchor, 4), "2026-01-05", anchor)
        assertEquals(4, stats.currentStreakDays)
    }

    @Test
    fun milestonesAreAwardedOncePerCycle() {
        val stats = QuarkKeeperCalendar.buildStats(run(anchor, 30), "2026-01-30", anchor)

        assertEquals(30, stats.currentStreakDays)
        assertEquals(11, stats.estimatedVipDays)
    }

    /**
     * Two check-ins a week are still two separate streaks of one, and the estimate has to fall out of
     * the same walk: the second cycle's day 7 is a reward even though the first cycle never finished.
     */
    @Test
    fun aSecondCycleStillAwardsItsMilestones() {
        val records = run("2026-01-01", 7) + run("2026-01-31", 7)
        val stats = QuarkKeeperCalendar.buildStats(records, "2026-02-06", anchor)

        assertEquals(7, stats.currentStreakDays)
        assertEquals(7, stats.longestStreakDays)
        assertEquals(14, stats.totalCheckinDays)
        assertEquals(2, stats.estimatedVipDays)
    }

    @Test
    fun theHistoryLogIsNewestFirst() {
        val records = listOf(
            record("2026-01-01", 1_000L),
            record("2026-01-03", 3_000L),
            record("2026-01-02", 2_000L),
        )
        val stats = QuarkKeeperCalendar.buildStats(records, "2026-01-03", anchor)

        assertEquals(
            listOf("2026-01-03", "2026-01-02", "2026-01-01"),
            stats.history.map { entry -> entry.dateKey },
        )
        assertEquals(3_000L, stats.history.first().checkedInAtMillis)
    }
}
