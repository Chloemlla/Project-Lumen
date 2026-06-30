package com.projectlumen.app.core.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.projectlumen.app.core.enums.ActiveEngine
import com.projectlumen.app.core.enums.PomodoroPhase
import com.projectlumen.app.core.enums.ReminderPhase

@Entity(tableName = "runtime_state")
data class RuntimeStateEntity(
    @PrimaryKey val id: Int = 1,
    val activeEngine: String = ActiveEngine.IDLE.name,
    val reminderPhase: String = ReminderPhase.IDLE.name,
    val reminderStartedAt: Long = 0L,
    val nextPreAlertAt: Long = 0L,
    val nextReminderAt: Long = 0L,
    val breakStartedAt: Long = 0L,
    val breakEndAt: Long = 0L,
    val pomodoroPhase: String = PomodoroPhase.IDLE.name,
    val pomodoroPhaseStartedAt: Long = 0L,
    val pomodoroPhaseEndAt: Long = 0L,
    val pomodoroCycleIndex: Int = 1,
    val isManuallyPaused: Boolean = false,
    val pausedAt: Long = 0L,
    val suspendedUntil: Long = 0L,
    val lastForegroundAt: Long = 0L,
    val lastBackgroundAt: Long = 0L,
    val lastStatsTickAt: Long = 0L,
    val proximityMonitoringActive: Boolean = false,
    val proximityTooClose: Boolean = false,
    val proximityLastFaceAt: Long = 0L,
    val proximityCloseStartedAt: Long = 0L,
    val proximityCloseTickAt: Long = 0L,
    val proximityLastWarningAt: Long = 0L,
    val proximityLastRatioPercent: Int = 0,
    val blinkLastBlinkAt: Long = 0L,
    val blinkLastWarningAt: Long = 0L,
    val blinkLastEyeOpenProbabilityPercent: Int = 0,
    val ambientLastLux: Float = 0f,
    val ambientTooDark: Boolean = false,
    val ambientLastWarningAt: Long = 0L,
    val proximityDebugInferenceMillis: Long = 0L,
    val proximityDebugCameraLatencyMillis: Long = 0L,
    val proximityDebugFaceWidthPx: Int = 0,
    val foregroundServiceStartedAt: Long = 0L,
    val foregroundServiceStoppedAt: Long = 0L,
    val foregroundServiceLastTaskRemovedAt: Long = 0L,
    val foregroundServiceLastStickyRestartAt: Long = 0L,
    val developerLastLowMemorySimulatedAt: Long = 0L,
    val sensorPitchDegrees: Float = 0f,
    val sensorRollDegrees: Float = 0f,
    val sensorYawDegrees: Float = 0f,
    val sensorLastAccelerationMagnitude: Float = 0f,
    val updatedAt: Long = System.currentTimeMillis(),
)
