package com.projectlumen.app.core.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_eye_stats")
data class DailyEyeStatsEntity(
    @PrimaryKey val statDate: String,
    val workingSeconds: Long = 0L,
    val restSeconds: Long = 0L,
    val skipCount: Int = 0,
    val completedBreakCount: Int = 0,
    val preAlertCount: Int = 0,
    val maxContinuousWorkSeconds: Long = 0L,
    val proximityWarningCount: Int = 0,
    val proximityCloseSeconds: Long = 0L,
    val eyeDryWarningCount: Int = 0,
    val lowLightWarningCount: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
)
