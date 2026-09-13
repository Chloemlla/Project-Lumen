package com.projectlumen.app.core.services

import com.projectlumen.app.ProjectLumenApplication

/**
 * Turns a fired schedule alarm into a notification.
 *
 * Schedule reminders are gated on the system notification permission only. They are deliberately
 * NOT filtered by the in-app eye-care switches (`notificationEnabled`) or by quiet hours: a to-do
 * the user created themselves must not be swallowed by settings that exist to silence eye-care
 * nagging.
 */
object ScheduleReminderDispatcher {

    /** A reminder whose trigger time is further in the past than this is treated as missed. */
    const val STALE_AFTER_MILLIS = 15 * 60 * 1000L

    suspend fun dispatch(app: ProjectLumenApplication, occurrenceId: Long, nowMillis: Long) {
        val occurrencesDao = app.database.scheduleOccurrencesDao()
        val occurrence = occurrencesDao.get(occurrenceId) ?: return
        if (occurrence.deletedAt != 0L) return

        val triggerAt = occurrence.startAt - occurrence.reminderMinutesBefore * 60_000L
        // Catch-up after boot must not dump a pile of long-expired reminders on the user, but the
        // occurrence still has to be marked so it is not retried on every later recovery.
        if (nowMillis - triggerAt > STALE_AFTER_MILLIS) {
            occurrencesDao.markReminderFired(occurrence.id, nowMillis)
            return
        }
        if (occurrence.completed) return
        if (occurrence.reminderMinutesBefore < 0) return
        if (occurrence.reminderFiredAt != 0L) return

        app.notifications.showScheduleReminder(occurrence)
        occurrencesDao.markReminderFired(occurrence.id, nowMillis)
    }
}
