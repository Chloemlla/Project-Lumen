package com.projectlumen.app.core.quarkkeeper

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * One configurable reminder node of the day.
 *
 * [minuteOfDay] is the local minute (`0..1439`) rather than an instant, for the same reason the rest of
 * the settings are: the guard is a daily habit, so the alarm time must follow the user across travel
 * and daylight-saving changes instead of sticking to an absolute instant.
 *
 * [enabled] is separate from deleting the entry on purpose. A reminder the user wants to mute for a
 * while is not the same as one whose time they have forgotten, and a list that could only add and
 * remove would make silencing a node cost the time they picked for it.
 */
data class QuarkKeeperReminder(
    val minuteOfDay: Int,
    val enabled: Boolean = true,
)

/**
 * User-visible configuration for the Quark check-in guard.
 *
 * [reminders] is a list rather than a single node because the guard's job starts long before the
 * evening: a check-in habit needs a nudge in the morning and at noon to stand any chance of not
 * becoming a scramble at 22:30. The list is what the settings screen edits and what the alarm layer
 * arms one slot per enabled entry for.
 *
 * [snoozeCutoffMinuteOfDay] is separate from [forcedMinuteOfDay] on purpose — the forced alert may
 * legitimately fire late (a device that booted at 23:00 still needs the catch-up alert), while the
 * snooze chain has to stop early enough that the last re-alert still leaves time to check in.
 */
data class QuarkKeeperSettings(
    val enabled: Boolean = false,
    val reminders: List<QuarkKeeperReminder> = DEFAULT_REMINDERS,
    val forcedMinuteOfDay: Int = DEFAULT_FORCED_MINUTE_OF_DAY,
    val snoozeMinutes: Int = DEFAULT_SNOOZE_MINUTES,
    val snoozeCutoffMinuteOfDay: Int = DEFAULT_SNOOZE_CUTOFF_MINUTE_OF_DAY,
    val soundEnabled: Boolean = true,
) {
    /**
     * The minutes that actually arm an alarm, ascending, one alarm slot each.
     *
     * Sorted here rather than relied on from the caller: the alarm layer assigns slot *n* to entry *n*
     * of this list, so an unsorted list would arm a different slot every reconcile and leave the
     * previous one to fire as well.
     */
    val enabledReminderMinutes: List<Int>
        get() = reminders.filter { it.enabled }.map { it.minuteOfDay }.sorted()

    /**
     * The minute the evening begins as far as the countdown is concerned.
     *
     * The earliest enabled reminder, because that is the first moment the guard has told the user
     * anything today, and a countdown posted before it would describe a deadline nobody has been
     * reminded of yet. A list with nothing enabled falls back to the deadline so the countdown still
     * exists for the users who kept only the forced alert.
     */
    val countdownFromMinuteOfDay: Int
        get() = enabledReminderMinutes.firstOrNull() ?: forcedMinuteOfDay

    /** Clamps every field into its legal window; used on both read and write so stored garbage self-heals. */
    fun sanitized(): QuarkKeeperSettings {
        // The deadline is clamped on its own first, against constants rather than against the list
        // below: it is the wall every reminder has to stay behind, so its bounds cannot depend on the
        // values that are about to be clamped against it.
        val forced = forcedMinuteOfDay.coerceIn(
            MIN_MINUTE_OF_DAY + MIN_NODE_GAP_MINUTES,
            MAX_MINUTE_OF_DAY,
        )
        // Every reminder is pulled back behind the deadline rather than the deadline being pushed back.
        // `coerceIn` throws on an inverted range, so a list-dependent floor for `forced` would blow up
        // the moment the two disagreed; and moving the deadline instead would silently undo the user's
        // last edit while leaving the reminder where they could not see the conflict. The other
        // direction is visible: a reminder that jumps to an earlier time is right there in the list.
        val ceiling = forced - MIN_NODE_GAP_MINUTES
        val reminders = reminders
            .map { it.copy(minuteOfDay = it.minuteOfDay.coerceIn(MIN_MINUTE_OF_DAY, ceiling)) }
            // Two entries driven onto the same minute by the clamp above collapse into one. The first
            // wins its `enabled` flag, which is the entry the user can still see in the list order.
            .distinctBy { it.minuteOfDay }
            .sortedBy { it.minuteOfDay }
            .take(MAX_REMINDERS)
        val cutoff = snoozeCutoffMinuteOfDay.coerceIn(forced, MAX_MINUTE_OF_DAY)
        return copy(
            reminders = reminders,
            forcedMinuteOfDay = forced,
            snoozeMinutes = snoozeMinutes.coerceIn(MIN_SNOOZE_MINUTES, MAX_SNOOZE_MINUTES),
            snoozeCutoffMinuteOfDay = cutoff,
        )
    }

    companion object {
        /**
         * Morning, noon and night, all on. Three is the number that makes the guard's day legible —
         * one before work, one at lunch, one as the evening starts — and each is one tap to silence.
         */
        val DEFAULT_REMINDERS: List<QuarkKeeperReminder> = listOf(
            QuarkKeeperReminder(6 * 60 + 30),
            QuarkKeeperReminder(12 * 60),
            QuarkKeeperReminder(21 * 60 + 30),
        )

        const val DEFAULT_FORCED_MINUTE_OF_DAY = 22 * 60 + 30
        const val DEFAULT_SNOOZE_MINUTES = 15
        const val DEFAULT_SNOOZE_CUTOFF_MINUTE_OF_DAY = 23 * 60 + 30

        const val MIN_MINUTE_OF_DAY = 0
        const val MAX_MINUTE_OF_DAY = 23 * 60 + 55
        /** Smallest allowed distance between a reminder node and the forced node. */
        const val MIN_NODE_GAP_MINUTES = 30
        const val MIN_SNOOZE_MINUTES = 5
        const val MAX_SNOOZE_MINUTES = 60
        /** Entries the list will hold. Each one is an alarm slot, so the cap is also a slot budget. */
        const val MAX_REMINDERS = 6
    }
}

/** Today's row only. It is keyed by [dateKey] so a stale row from yesterday reads as "not checked in". */
data class QuarkKeeperTodayStatus(
    val dateKey: String = "",
    val checkedInAtMillis: Long = 0L,
    val snoozedUntilMillis: Long = 0L,
    /**
     * When the user was last handed off to Quark, or 0 when no hand-off is pending. Stored as the
     * instant rather than a boolean so the question expires on its own: only the window in
     * [QuarkKeeperClock.isReturnConfirmationOpen] decides whether it is still open, which means a
     * process killed while the user was in Quark still comes back to a deadline instead of a prompt
     * that never ends.
     */
    val awaitingReturnAtMillis: Long = 0L,
    val nagRound: Int = 0,
) {
    val checkedIn: Boolean get() = checkedInAtMillis > 0L

    /** True while a hand-off is on record. Whether it is still worth asking is the clock's call. */
    val awaitingReturn: Boolean get() = awaitingReturnAtMillis > 0L
}

/** One completed day. [checkedInAtMillis] is the instant the user confirmed it, kept to the minute. */
data class QuarkKeeperCheckinRecord(
    val dateKey: String,
    val checkedInAtMillis: Long,
)

/** Everything the UI and the alarms read; one immutable value so a reader never sees a half-updated day. */
data class QuarkKeeperSnapshot(
    val settings: QuarkKeeperSettings = QuarkKeeperSettings(),
    val today: QuarkKeeperTodayStatus = QuarkKeeperTodayStatus(),
    val records: List<QuarkKeeperCheckinRecord> = emptyList(),
) {
    fun checkedInDateKeys(): Set<String> = records.mapTo(mutableSetOf()) { record -> record.dateKey }
}

enum class QuarkKeeperDayState {
    CHECKED_IN,
    MISSED,
    PENDING,

    /** A day of the current cycle that has not started yet. */
    UPCOMING,
}

/** One square of the 30-day grid. */
data class QuarkKeeperDayCell(
    val cycleDay: Int,
    val dateKey: String,
    val state: QuarkKeeperDayState,
    /** Non-zero only on the milestone days that grant VIP time; see [QuarkKeeperCalendar.MILESTONE_VIP_DAYS]. */
    val milestoneVipDays: Int,
)

data class QuarkKeeperCycleSummary(
    val cycleIndex: Int,
    val cells: List<QuarkKeeperDayCell>,
    val completedDays: Int,
)

/** One line of the history log, newest first. */
data class QuarkKeeperHistoryEntry(
    val dateKey: String,
    val checkedInAtMillis: Long,
)

data class QuarkKeeperStats(
    val currentStreakDays: Int,
    val longestStreakDays: Int,
    val totalCheckinDays: Int,
    val estimatedVipDays: Int,
    val history: List<QuarkKeeperHistoryEntry>,
)

/** Countdown value with the fields a caller needs; the wording itself belongs to the string resources. */
data class QuarkKeeperRemaining(val totalMinutes: Int) {
    val hours: Int get() = totalMinutes / 60
    val minutes: Int get() = totalMinutes % 60
}

/**
 * Pure time arithmetic for the guard. Kept free of Android types so the same rules run in the
 * dispatcher, in the foreground service and in the Compose preview.
 */
object QuarkKeeperClock {

    /**
     * How long the guard keeps asking "did the check-in go through?" after handing the user to Quark.
     *
     * The same number arms the return monitor that re-raises the alert and bounds the question the UI
     * asks on the way back, so the two cannot drift into a state where the app is still asking about a
     * hand-off whose alarm has already fired.
     */
    const val RETURN_CONFIRMATION_WINDOW_MILLIS = 60_000L

    private const val MILLIS_PER_MINUTE = 60_000L

    fun zone(): ZoneId = ZoneId.systemDefault()

    fun minuteOfDay(nowMillis: Long, zoneId: ZoneId = zone()): Int {
        val time = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zoneId)
        return time.hour * 60 + time.minute
    }

    /**
     * The next time today's [minuteOfDay] occurs; tomorrow's occurrence when it already passed.
     *
     * Strictly greater than [nowMillis]: an alarm armed at exactly "now" is already in the past by the
     * time the platform reads the clock, so it would never fire.
     */
    fun nextOccurrenceOf(minuteOfDay: Int, nowMillis: Long, zoneId: ZoneId = zone()): Long {
        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zoneId)
        val candidate = now.toLocalDate().atTime(minuteOfDay / 60, minuteOfDay % 60).atZone(zoneId)
        val candidateMillis = candidate.toInstant().toEpochMilli()
        if (candidateMillis > nowMillis) return candidateMillis
        return candidate.plusDays(1).toInstant().toEpochMilli()
    }

    /** Midnight that closes [nowMillis]'s local day — the instant the streak would break. */
    fun endOfDayMillis(nowMillis: Long, zoneId: ZoneId = zone()): Long {
        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zoneId)
        return now.toLocalDate().plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    fun remainingUntilEndOfDay(nowMillis: Long, zoneId: ZoneId = zone()): QuarkKeeperRemaining {
        val remainingMillis = (endOfDayMillis(nowMillis, zoneId) - nowMillis).coerceAtLeast(0L)
        return QuarkKeeperRemaining(((remainingMillis + 59_999L) / 60_000L).toInt())
    }

    /** Minute-of-day for a stored instant, used to render "checked in at 21:04". */
    fun minutePrecisionLabel(nowMillis: Long, zoneId: ZoneId = zone()): Pair<Int, Int> {
        val time = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zoneId)
        return time.hour to time.minute
    }

    /** True once the snooze chain must stop offering another round. */
    fun isSnoozeAllowed(settings: QuarkKeeperSettings, nowMillis: Long, zoneId: ZoneId = zone()): Boolean {
        return minuteOfDay(nowMillis, zoneId) < settings.snoozeCutoffMinuteOfDay
    }

    /**
     * When a snooze of [snoozeMinutes] asked for at [nowMillis] should actually expire.
     *
     * Capped at the end of today, because a snooze is a postponement *of today's alert*: the cutoff
     * alone does not give that. The longest setting is an hour and the cutoff is half an hour before
     * midnight, so a user who postpones at 23:29 is asking for 00:29 — an instant on a day that has not
     * started, where the stored stamp belongs to a day that has ended. The alarm would then fire on the
     * new day and, the new day being unchecked, raise the forced alert at half past midnight with a
     * whole evening still ahead of it, having also spent the round the boot catch-up needed.
     *
     * The last millisecond of the day rather than midnight itself, so the fire can only ever land
     * inside the day it was asked for. [isSnoozeAllowed] stays the separate, fixed line the spec fixes
     * it at; this is the same "there is only so much of today left" applied continuously, not a second
     * cutoff — before 23:30 a postponement is still always granted, just not past the day's end.
     */
    fun snoozeExpiry(nowMillis: Long, snoozeMinutes: Int, zoneId: ZoneId = zone()): Long {
        val requested = nowMillis + snoozeMinutes * MILLIS_PER_MINUTE
        return minOf(requested, endOfDayMillis(nowMillis, zoneId) - 1L)
    }

    /**
     * True while a recorded hand-off to Quark is recent enough to still be worth asking about.
     *
     * The elapsed time is required to be non-negative, not merely within the window: a user who moves
     * the system clock backwards leaves [awaitingReturnAtMillis] in the future, and a plain
     * `elapsed <= window` would read that as "just left" forever — the card would follow them into the
     * next day. A future stamp is treated the same as an expired one, because the hand-off it describes
     * cannot have happened yet.
     *
     * The window is half-open, and the upper bound belongs to the monitor rather than to the card. That
     * same [RETURN_CONFIRMATION_WINDOW_MILLIS] is what arms the return monitor, so at exactly sixty
     * seconds the forced alert takes the screen; a card that also counted itself open at that instant
     * would be fighting the alert it exists to spare the user.
     */
    fun isReturnConfirmationOpen(awaitingReturnAtMillis: Long, nowMillis: Long): Boolean {
        if (awaitingReturnAtMillis <= 0L) return false
        val elapsedMillis = nowMillis - awaitingReturnAtMillis
        return elapsedMillis >= 0L && elapsedMillis < RETURN_CONFIRMATION_WINDOW_MILLIS
    }
}
