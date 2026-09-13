package com.projectlumen.app.core.services

import com.projectlumen.app.ProjectLumenApplication
import com.projectlumen.app.core.repositories.ScheduleRepository

/**
 * Re-arms the exact alarms for upcoming schedule occurrences.
 *
 * Pending intents do not survive a reboot or a force-stop, so this runs on every recovery point
 * (boot, package replace) and after the user edits the schedule.
 */
object ScheduleAlarmRestore {

    /**
     * AlarmManager caps how many exact alarms an app may hold and Doze downgrades the overflow, so
     * only the nearest occurrences are armed. Anything past this many is picked up on the next app
     * launch or reboot.
     */
    const val MAX_SCHEDULED_ALARMS = 12

    suspend fun rearm(
        app: ProjectLumenApplication,
        repository: ScheduleRepository,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        val scheduler = ScheduleReminderScheduler(app)
        val upcoming = repository.upcomingReminders(nowMillis, MAX_SCHEDULED_ALARMS)
        upcoming.forEach { occurrence ->
            val triggerAt = occurrence.startAt - occurrence.reminderMinutesBefore * 60_000L
            if (
                occurrence.reminderMinutesBefore >= 0 &&
                triggerAt > nowMillis &&
                occurrence.reminderFiredAt == 0L
            ) {
                scheduler.schedule(occurrence, triggerAt)
            }
        }
    }
}
