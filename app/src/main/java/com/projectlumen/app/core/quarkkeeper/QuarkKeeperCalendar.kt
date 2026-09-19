package com.projectlumen.app.core.quarkkeeper

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Turns the flat check-in log into the 30-day dashboard, the streak figures and the VIP estimate.
 *
 * A cycle is anchored on the first day the user ever checked in, not on the calendar month and not
 * on "today": an anchor that moves would renumber the whole grid under the user, and the milestone
 * badges (7/14/21/30) would then point at different dates every time the screen was opened.
 */
object QuarkKeeperCalendar {

    const val CYCLE_LENGTH_DAYS = 30

    /**
     * VIP days granted on each milestone. These mirror Quark's published reward tiers; the UI labels
     * the resulting total as an estimate because the grant itself is decided by Quark, not by us.
     */
    val MILESTONE_VIP_DAYS: Map<Int, Int> = mapOf(7 to 1, 14 to 1, 21 to 2, 30 to 7)

    val MILESTONE_DAYS: List<Int> = MILESTONE_VIP_DAYS.keys.sorted()

    fun parseDateKey(dateKey: String): LocalDate? = runCatching { LocalDate.parse(dateKey) }.getOrNull()

    /** Earliest recorded day, or null when nothing has ever been checked in. */
    fun anchorDateKey(records: List<QuarkKeeperCheckinRecord>): String? {
        return records.minOfOrNull { record -> record.dateKey }
    }

    /**
     * 1-based position of [dateKey] inside its cycle. Dates before the anchor are reported as day 1
     * rather than failing: the anchor is the earliest record, so this only happens if the log was
     * trimmed or a row survived an undo.
     */
    fun cycleDay(dateKey: String, anchorDateKey: String): Int {
        val date = parseDateKey(dateKey) ?: return 1
        val anchor = parseDateKey(anchorDateKey) ?: return 1
        val elapsed = ChronoUnit.DAYS.between(anchor, date)
        if (elapsed < 0L) return 1
        return (elapsed % CYCLE_LENGTH_DAYS).toInt() + 1
    }

    /** Cycle 0 is the anchored one; the number only ever appears in the progress caption. */
    fun cycleIndex(dateKey: String, anchorDateKey: String): Int {
        val date = parseDateKey(dateKey) ?: return 0
        val anchor = parseDateKey(anchorDateKey) ?: return 0
        val elapsed = ChronoUnit.DAYS.between(anchor, date)
        if (elapsed < 0L) return 0
        return (elapsed / CYCLE_LENGTH_DAYS).toInt()
    }

    /**
     * The cycle [todayKey] falls into, with one cell per cycle day.
     *
     * [todayKey] is passed in rather than read from the clock so the grid, the statistics and the
     * alarm decisions are all computed against the same "today" inside one recomposition.
     */
    fun buildCycle(
        records: List<QuarkKeeperCheckinRecord>,
        todayKey: String,
        anchorDateKey: String?,
    ): QuarkKeeperCycleSummary {
        val anchor = anchorDateKey ?: todayKey
        val index = cycleIndex(todayKey, anchor)
        val cycleStart = (parseDateKey(anchor) ?: return emptyCycle())
            .plusDays((index * CYCLE_LENGTH_DAYS).toLong())
        val checkedIn = records.mapTo(mutableSetOf()) { record -> record.dateKey }
        val today = parseDateKey(todayKey)
        var completed = 0
        val cells = (1..CYCLE_LENGTH_DAYS).map { day ->
            val dateKey = cycleStart.plusDays((day - 1).toLong()).toString()
            val state = when {
                dateKey in checkedIn -> {
                    completed += 1
                    QuarkKeeperDayState.CHECKED_IN
                }
                today != null && dateKey == todayKey -> QuarkKeeperDayState.PENDING
                today != null && cycleStart.plusDays((day - 1).toLong()) < today -> QuarkKeeperDayState.MISSED
                else -> QuarkKeeperDayState.UPCOMING
            }
            QuarkKeeperDayCell(
                cycleDay = day,
                dateKey = dateKey,
                state = state,
                milestoneVipDays = MILESTONE_VIP_DAYS[day] ?: 0,
            )
        }
        return QuarkKeeperCycleSummary(cycleIndex = index, cells = cells, completedDays = completed)
    }

    private fun emptyCycle(): QuarkKeeperCycleSummary {
        return QuarkKeeperCycleSummary(cycleIndex = 0, cells = emptyList(), completedDays = 0)
    }

    /**
     * Streaks, totals and the VIP estimate over the whole log.
     *
     * The streak is measured in consecutive calendar days, so it breaks on the first missing day
     * rather than on a count of records — two check-ins a week still form a streak of two.
     */
    fun buildStats(
        records: List<QuarkKeeperCheckinRecord>,
        todayKey: String,
        anchorDateKey: String?,
    ): QuarkKeeperStats {
        if (records.isEmpty()) {
            return QuarkKeeperStats(
                currentStreakDays = 0,
                longestStreakDays = 0,
                totalCheckinDays = 0,
                estimatedVipDays = 0,
                history = emptyList(),
            )
        }
        val sortedKeys = records.map { record -> record.dateKey }.distinct().sorted()
        val checkedIn = sortedKeys.toHashSet()

        var longest = 0
        var run = 0
        var previous: LocalDate? = null
        sortedKeys.forEach { dateKey ->
            val date = parseDateKey(dateKey)
            if (date == null) return@forEach
            run = if (previous != null && ChronoUnit.DAYS.between(previous, date) == 1L) run + 1 else 1
            if (run > longest) longest = run
            previous = date
        }

        val today = parseDateKey(todayKey)
        var current = 0
        if (today != null) {
            // Starts at today when today is done, otherwise at yesterday: a streak is only broken
            // once a whole day has passed without a check-in, so an unfinished today must not read
            // as "streak lost" all afternoon.
            var cursor = if (todayKey in checkedIn) today else today.minusDays(1)
            while (checkedIn.contains(cursor.toString())) {
                current += 1
                cursor = cursor.minusDays(1)
            }
        }

        val anchor = anchorDateKey ?: sortedKeys.first()
        val estimatedVipDays = sortedKeys.sumOf { dateKey ->
            MILESTONE_VIP_DAYS[cycleDay(dateKey, anchor)] ?: 0
        }

        val history = records
            .sortedWith(compareByDescending<QuarkKeeperCheckinRecord> { record -> record.dateKey }
                .thenByDescending { record -> record.checkedInAtMillis })
            .map { record -> QuarkKeeperHistoryEntry(record.dateKey, record.checkedInAtMillis) }

        return QuarkKeeperStats(
            currentStreakDays = current,
            longestStreakDays = longest,
            totalCheckinDays = sortedKeys.size,
            estimatedVipDays = estimatedVipDays,
            history = history,
        )
    }
}
