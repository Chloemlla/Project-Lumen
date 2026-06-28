package com.projectlumen.app.core.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_pomodoro_stats")
data class DailyPomodoroStatsEntity(
    @PrimaryKey val statDate: String,
    val completedTomatoCount: Int = 0,
    val restartCount: Int = 0,
    val completedFocusSessions: Int = 0,
    val totalFocusSeconds: Long = 0L,
    val totalBreakSeconds: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis(),
)
