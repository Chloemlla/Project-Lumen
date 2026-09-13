package com.projectlumen.app.core.enums

/** How a schedule reminder is delivered. Stored by [name] in Room; labels live in the UI layer. */
enum class ScheduleReminderMethod {
    NOTIFICATION,
    ALARM,
}
