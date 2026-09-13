package com.projectlumen.app.core.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.projectlumen.app.core.enums.ScheduleCategory
import com.projectlumen.app.core.enums.ScheduleRecurrence
import com.projectlumen.app.core.enums.ScheduleReminderMethod

/**
 * Repeating rule template. Occurrences are expanded from this row into `schedule_occurrences`.
 *
 * [startAt] / [endAt] are the anchors: the first occurrence of the series. [recurrenceUntil] holds
 * the end of the last repeating day at 23:59:59.999, so it is inclusive; `0` means "never ends".
 */
@Entity(tableName = "schedule_series")
data class ScheduleSeriesEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val category: String = ScheduleCategory.PERSONAL.name,
    val allDay: Boolean = false,
    val startAt: Long,
    val endAt: Long,
    val recurrence: String = ScheduleRecurrence.DAILY.name,
    val recurrenceUntil: Long = 0L,
    val reminderMinutesBefore: Int = -1,
    val reminderMethod: String = ScheduleReminderMethod.NOTIFICATION.name,
    val enabled: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long = 0L,
)
