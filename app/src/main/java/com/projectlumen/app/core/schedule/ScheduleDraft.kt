package com.projectlumen.app.core.schedule

import com.projectlumen.app.core.enums.ScheduleCategory
import com.projectlumen.app.core.enums.ScheduleRecurrence
import com.projectlumen.app.core.enums.ScheduleReminderMethod

/** Editable shape of a schedule, shared between the detail screen and [com.projectlumen.app.core.repositories.ScheduleRepository]. */
data class ScheduleDraft(
    val title: String,
    val category: ScheduleCategory,
    val allDay: Boolean,
    val startAt: Long,
    val endAt: Long,
    val reminderMinutesBefore: Int,
    val reminderMethod: ScheduleReminderMethod,
    val recurrence: ScheduleRecurrence,
    val recurrenceUntil: Long,
)
