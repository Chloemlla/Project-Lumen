package com.projectlumen.app.core.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminder_plans")
data class ReminderPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val enabled: Boolean = true,
    val warnIntervalMinutes: Int = 20,
    val restDurationSeconds: Int = 20,
    val quietHoursEnabled: Boolean = false,
    val quietStartMinute: Int = 1320,
    val quietEndMinute: Int = 420,
    val quietMode: String = "PAUSE_TIMER",
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long = 0L,
)
