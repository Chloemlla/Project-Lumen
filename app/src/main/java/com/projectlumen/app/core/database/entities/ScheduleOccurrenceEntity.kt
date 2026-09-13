package com.projectlumen.app.core.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.projectlumen.app.core.enums.ScheduleCategory
import com.projectlumen.app.core.enums.ScheduleRecurrence
import com.projectlumen.app.core.enums.ScheduleReminderMethod

/**
 * One concrete occurrence of a schedule. The home list and the detail screen both read this table.
 *
 * [originalStartAt] is the identity of the occurrence inside its series: the start time the series
 * rule produced. It does not change when the user edits this single occurrence, so the materializer
 * can tell "already generated" from "new". For a standalone entry (`seriesId == 0`) it equals
 * [startAt].
 *
 * No unique index on `(seriesId, originalStartAt)`: standalone entries all share `seriesId = 0`, so
 * two unrelated to-dos at the same time would fail to insert. De-duplication happens in
 * `ScheduleMaterializer` against a set of existing `originalStartAt` values instead.
 */
@Entity(
    tableName = "schedule_occurrences",
    indices = [
        Index("seriesId"),
        Index("startAt"),
        Index("deletedAt"),
    ],
)
data class ScheduleOccurrenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val seriesId: Long = 0L,
    val title: String,
    val category: String = ScheduleCategory.PERSONAL.name,
    val allDay: Boolean = false,
    val startAt: Long,
    val endAt: Long,
    val reminderMinutesBefore: Int = -1,
    val reminderMethod: String = ScheduleReminderMethod.NOTIFICATION.name,
    val recurrence: String = ScheduleRecurrence.NONE.name,
    val recurrenceUntil: Long = 0L,
    val originalStartAt: Long,
    val detached: Boolean = false,
    val completed: Boolean = false,
    val completedAt: Long = 0L,
    val reminderFiredAt: Long = 0L,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long = 0L,
)
