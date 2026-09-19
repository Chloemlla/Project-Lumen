package com.projectlumen.app.core.services

object NotificationChannels {
    const val REMINDER = "reminder"
    const val POMODORO = "pomodoro"
    const val STATUS = "status"
    const val PROXIMITY = "proximity"
    const val SCHEDULE_NOTIFICATION = "schedule_reminder"
    const val SCHEDULE_ALARM = "schedule_alarm"
    const val SCHEDULE_OVERDUE = "schedule_overdue"
    /** Silent channel for the ongoing countdown, which is re-posted every minute and must not beep. */
    const val QUARK_KEEPER_ONGOING = "quark_keeper_ongoing"
    /** Audible channel for the deadline alert, kept separate so the countdown can be muted on its own. */
    const val QUARK_KEEPER_ALARM = "quark_keeper_alarm"
    /** Silent channel for the evening reminder, which asks once and must not sound like the alert. */
    const val QUARK_KEEPER_REMINDER = "quark_keeper_reminder"
}
