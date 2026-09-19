package com.projectlumen.app.core.constants

object NotificationIds {
    const val FOREGROUND_TIMER = 9001
    const val PRE_ALERT = 1001
    const val BREAK_DUE = 1002
    const val BREAK_DONE = 1003
    const val START_BREAK_ACTION = 1012
    const val SKIP_BREAK_ACTION = 1013
    const val POMODORO = 2001
    const val UPDATE_AVAILABLE = 3001
    const val STOP_TIMER_ACTION = 9010
    const val PROXIMITY_FOREGROUND = 9101
    const val PROXIMITY_WARNING = 9102
    const val EYE_DRY_WARNING = 9103
    const val LOW_LIGHT_FOREGROUND = 9201
    const val LOW_LIGHT_WARNING = 9202
    const val OVERLAY_FOREGROUND = 9301
    const val DEVELOPER_DEBUG_FOREGROUND = 9401
    const val GLOBAL_TOAST = 9501
    const val SCHEDULE_REMINDER_BASE = 9600
    const val SCHEDULE_REMINDER_RANGE = 300
    // Must not overlap the SCHEDULE_REMINDER range (9600..9899): the id doubles as the alarm
    // request code, so an overlap would let the two chains silently overwrite each other's alarms.
    const val SCHEDULE_OVERDUE_BASE = 9900
    const val SCHEDULE_OVERDUE_RANGE = 300
    // Quark Keeper owns a contiguous block from 11001 up, kept above every schedule range: an id
    // here doubles as an alarm request code, so an overlap would let one guard cancel the other's
    // alarm rather than merely reuse a notification slot.
    const val QUARK_KEEPER_DAILY = 11001
    const val QUARK_KEEPER_DEADLINE = 11002
    const val QUARK_KEEPER_SNOOZE = 11003
    const val QUARK_KEEPER_MIDNIGHT = 11004
    const val QUARK_KEEPER_RETURN_MONITOR = 11005
    /** The ongoing foreground-service notification; persistent until the day is checked in. */
    const val QUARK_KEEPER_ONGOING = 11006
    /** The deadline alert itself, posted alongside the overlay so a denied overlay still interrupts. */
    const val QUARK_KEEPER_FORCED_ALERT = 11007
    // Request codes for the alert's action buttons. These are notifications and never alarms, but
    // they still take their own ids: a PendingIntent's identity is (requestCode, filterEquals), and
    // sharing one with a slot above would let the last writer replace an armed alarm.
    const val QUARK_KEEPER_ACTION_GO_CHECK_IN = 11008
    const val QUARK_KEEPER_ACTION_SNOOZE = 11009
    const val QUARK_KEEPER_ACTION_MARK_DONE = 11010
    // Undo is deliberately not one of the alert's buttons. It belongs to the dashboard, which is the
    // only surface where the user can see which day they are about to reopen, and the guard's alerts
    // exist to end the day rather than to offer a way back into it. Nothing posts this id today — the
    // in-app undo reaches the store through its own feature entry — so it stays reserved for a
    // dashboard-side action and must never be attached to a notification.
    const val QUARK_KEEPER_ACTION_UNDO = 11011
}
