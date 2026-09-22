package com.projectlumen.app.core.services

import com.projectlumen.app.ProjectLumenApplication
import com.projectlumen.app.core.database.entities.AppSettingsEntity
import com.projectlumen.app.core.database.entities.ScheduleOccurrenceEntity
import com.projectlumen.app.core.schedule.ScheduleOverdueNag
import com.projectlumen.app.core.time.LumenTimeZone

/**
 * Turns one fired overdue-nag alarm into a notification, then re-arms the slot that fired.
 *
 * The key difference from [ScheduleReminderDispatcher] is that there is **no** stale/catch-up rule
 * here. A pre-start reminder is a one-shot cue where missing it is acceptable; the nag is the exact
 * opposite — a round missed while the device slept must still be delivered. Dropping a late round
 * would mean the chain quietly stops itself overnight, which contradicts "keep nagging until the
 * user checks it off". That applies to both entry points, the evening one included.
 *
 * `completed` is the only automatic stop condition, so it must be paired with cancelling the alarms
 * on the completion path (see `ProjectLumenApplication.rescheduleScheduleReminders`); otherwise a
 * checked-off item still fires one more round.
 *
 * Like the pre-start reminder, the nag is gated on the switch below and the notification permission
 * only — never on quiet hours or the eye-care notification switch.
 *
 * The two entry points post the **same notification id**, so the evening fire updates the interval
 * round's notification in place and re-alerts it instead of stacking a second copy of the same
 * to-do.
 */
object ScheduleOverdueNagDispatcher {

    suspend fun dispatch(app: ProjectLumenApplication, occurrenceId: Long, nowMillis: Long) {
        val (occurrence, settings) = resolveValidated(app, occurrenceId, nowMillis) ?: return

        app.notifications.ensureChannels()
        app.notifications.showScheduleOverdue(occurrence)

        // Only the interval slot: that is the slot this round fired for, and the only countdown this
        // round owns. The evening slot is left alone — arming it from here would mean re-deciding
        // `endedInMorning` in a second place, and a missed check would quietly start evening-nagging
        // afternoon-ended to-dos.
        ScheduleOverdueNagScheduler(app).schedule(
            occurrenceId,
            ScheduleOverdueNag.nextNagAt(nowMillis, settings.scheduleOverdueNagIntervalMinutes),
        )
    }

    suspend fun dispatchEvening(app: ProjectLumenApplication, occurrenceId: Long, nowMillis: Long) {
        val (occurrence, settings) = resolveValidated(app, occurrenceId, nowMillis) ?: return

        app.notifications.ensureChannels()
        app.notifications.showScheduleOverdue(occurrence)

        // Symmetrically, only the evening slot. Re-arming the interval slot here would count the
        // evening fire as an interval round and reset the user's interval countdown.
        ScheduleOverdueNagScheduler(app).scheduleEvening(
            occurrenceId,
            ScheduleOverdueNag.nextEveningNagAt(
                nowMillis,
                (settings.scheduleOverdueNagEveningMinute * 60 +
                    settings.scheduleOverdueNagEveningSecond).coerceIn(0, 86_399),
                LumenTimeZone.zoneId(),
            ),
        )
    }

    /**
     * The validation both entry points share, in order: fetch the row → `completed` → read settings
     * → switch off → `isOverdue`. Null means "exit silently".
     */
    private suspend fun resolveValidated(
        app: ProjectLumenApplication,
        occurrenceId: Long,
        nowMillis: Long,
    ): Pair<ScheduleOccurrenceEntity, AppSettingsEntity>? {
        // get() already excludes soft-deleted rows, so a missing row means the to-do is gone.
        val occurrence = app.database.scheduleOccurrencesDao().get(occurrenceId) ?: return null
        if (occurrence.completed) return null

        val settings = app.settingsRepository().getOrDefault()
        if (!settings.scheduleOverdueNagEnabled) return null
        if (!ScheduleOverdueNag.isOverdue(occurrence, nowMillis)) return null
        return occurrence to settings
    }
}
