package com.projectlumen.app.core.enums

/** Repetition rule of a schedule series. Stored by [name] in Room; labels live in the UI layer. */
enum class ScheduleRecurrence {
    NONE,
    DAILY,
    WEEKDAYS,
    WEEKLY,
    MONTHLY,
    YEARLY,
}
