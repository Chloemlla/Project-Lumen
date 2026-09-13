package com.projectlumen.app.core.enums

/** Category of a to-do / schedule entry. Stored by [name] in Room; labels live in the UI layer. */
enum class ScheduleCategory {
    PERSONAL,
    WORK,
    STUDY,
    OTHER,
}
