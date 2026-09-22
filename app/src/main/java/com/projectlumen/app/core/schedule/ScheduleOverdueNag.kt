package com.projectlumen.app.core.schedule

import com.projectlumen.app.core.database.entities.ScheduleOccurrenceEntity
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Decides which to-do occurrences deserve an overdue nag, and when the next one is due.
 *
 * Pure Kotlin with no Android dependency, so it runs in plain JVM unit tests. The overdue comparison
 * itself is in epoch milliseconds and never consults a zone: "overdue" is a comparison against the
 * instant an occurrence ends, not a calendar day.
 *
 * [endedInMorning] and [nextEveningNagAt] do take a [ZoneId], and that is unavoidable rather than a
 * convenience: whether an end time fell "in the morning" and when "the evening" arrives are
 * inherently local-wall-clock questions, and an occurrence ending at 07:30 in Asia/Shanghai is not a
 * morning occurrence everywhere on earth. Those two are the only zone-aware members; the interval
 * schedule stays zone-free so it keeps firing at the same rhythm wherever the device thinks it is.
 */
object ScheduleOverdueNag {
    /**
     * Smallest interval the user can pick. Doze throttles `setAndAllowWhileIdle` to roughly one alarm
     * per 9 minutes, so a shorter interval would read as "the setting did nothing" rather than as a
     * faster nag.
     */
    const val MIN_INTERVAL_MINUTES = 5
    const val MAX_INTERVAL_MINUTES = 360
    const val DEFAULT_INTERVAL_MINUTES = 120

    /** Without this ceiling a to-do left unchecked a year ago would keep ringing forever. */
    const val MAX_OVERDUE_AGE_DAYS = 30L

    /** Spreads a sweep's first round so a backlog of overdue items does not fire all at once. */
    const val SWEEP_STAGGER_MILLIS = 15_000L

    /**
     * End of the morning, as minutes from local midnight. A to-do that ended before this was meant to
     * be finished early in the day, so it gets the extra evening follow-up; noon itself does not
     * count as morning.
     */
    const val MORNING_END_MINUTE = 12 * 60

    /** Default evening follow-up time — 21:30 as minutes from local midnight. */
    const val DEFAULT_EVENING_MINUTE = 21 * 60 + 30

    private const val MILLIS_PER_MINUTE = 60_000L
    private const val MILLIS_PER_DAY = 86_400_000L

    /**
     * True when [occurrence] has slipped past its end instant and is still open.
     *
     * The comparison is strict: the millisecond at [ScheduleOccurrenceEntity.endAt] itself is still
     * inside the to-do, so a to-do ending at 18:00 does not nag at 18:00:00.000.
     */
    fun isOverdue(occurrence: ScheduleOccurrenceEntity, nowMillis: Long): Boolean {
        if (occurrence.deletedAt != 0L) return false
        if (occurrence.completed) return false
        if (occurrence.endAt >= nowMillis) return false
        return nowMillis - occurrence.endAt <= MAX_OVERDUE_AGE_DAYS * MILLIS_PER_DAY
    }

    /**
     * The overdue subset of [occurrences], oldest end first so the longest-neglected to-do is nagged
     * first. Deliberately not truncated: a backlog is supposed to be nagged as a whole.
     */
    fun overdueItems(
        occurrences: List<ScheduleOccurrenceEntity>,
        nowMillis: Long,
    ): List<ScheduleOccurrenceEntity> =
        occurrences.filter { isOverdue(it, nowMillis) }.sortedBy { it.endAt }

    /**
     * Clamping lives here rather than in the settings UI so an out-of-range value already persisted by
     * an older build is caught on read as well.
     */
    fun clampIntervalMinutes(minutes: Int): Int =
        minutes.coerceIn(MIN_INTERVAL_MINUTES, MAX_INTERVAL_MINUTES)

    fun nextNagAt(nowMillis: Long, intervalMinutes: Int): Long =
        nowMillis + clampIntervalMinutes(intervalMinutes) * MILLIS_PER_MINUTE

    /**
     * True when [occurrence] ended before noon *local time in [zoneId]*, i.e. it was a morning to-do
     * that also deserves the once-a-night follow-up.
     *
     * The zone conversion is the whole point: [ScheduleOccurrenceEntity.endAt] is epoch
     * milliseconds, so `endAt % 86_400_000` would yield the offset within the *UTC* day and score an
     * Asia/Shanghai 07:30 as 23:30 — exactly backwards.
     */
    fun endedInMorning(occurrence: ScheduleOccurrenceEntity, zoneId: ZoneId): Boolean {
        val local = Instant.ofEpochMilli(occurrence.endAt).atZone(zoneId)
        return local.hour * 60 + local.minute < MORNING_END_MINUTE
    }

    /**
     * The next instant at which [eveningSecondOfDay] (seconds from local midnight) comes around in
     * [zoneId]: today's if it is still ahead of [nowMillis], otherwise tomorrow's.
     *
     * Built from `LocalTime` + `ZoneId` rather than by adding 24 hours of milliseconds, so a DST
     * transition in between keeps the nag pinned to the configured wall-clock second instead of
     * drifting it by an hour.
     */
    fun nextEveningNagAt(nowMillis: Long, eveningSecondOfDay: Int, zoneId: ZoneId): Long {
        val secondOfDay = eveningSecondOfDay.coerceIn(0, 86_399)
        val evening = LocalTime.ofSecondOfDay(secondOfDay.toLong())
        val now = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
        val today = now.toLocalDate().atTime(evening).atZone(zoneId)
        val next = if (today.toInstant().toEpochMilli() <= nowMillis) {
            now.toLocalDate().plusDays(1).atTime(evening).atZone(zoneId)
        } else {
            today
        }
        return next.toInstant().toEpochMilli()
    }
}
