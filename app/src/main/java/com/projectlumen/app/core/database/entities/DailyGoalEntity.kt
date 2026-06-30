package com.projectlumen.app.core.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_goals")
data class DailyGoalEntity(
    @PrimaryKey val id: Int = 1,
    val restBreakGoal: Int = 8,
    val maxContinuousWorkMinutes: Int = 45,
    val pomodoroGoal: Int = 4,
    val weeklyActiveDaysGoal: Int = 5,
    val updatedAt: Long = System.currentTimeMillis(),
)
