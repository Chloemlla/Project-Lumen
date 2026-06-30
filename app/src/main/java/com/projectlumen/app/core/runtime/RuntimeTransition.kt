package com.projectlumen.app.core.runtime

import com.projectlumen.app.core.database.entities.RuntimeStateEntity

data class RuntimeTransition(
    val nextRuntime: RuntimeStateEntity,
    val eyeStatsDelta: EyeStatsDelta = EyeStatsDelta(),
    val pomodoroStatsDelta: PomodoroStatsDelta = PomodoroStatsDelta(),
    val audioEvent: AudioEvent = AudioEvent.None,
)

data class EyeStatsDelta(
    val workingSeconds: Long = 0L,
    val restSeconds: Long = 0L,
    val skipCount: Int = 0,
    val completedBreakCount: Int = 0,
    val preAlertCount: Int = 0,
    val maxContinuousWorkSeconds: Long = 0L,
) {
    val isEmpty: Boolean
        get() = workingSeconds == 0L &&
            restSeconds == 0L &&
            skipCount == 0 &&
            completedBreakCount == 0 &&
            preAlertCount == 0 &&
            maxContinuousWorkSeconds == 0L
}

data class PomodoroStatsDelta(
    val completedTomatoCount: Int = 0,
    val restartCount: Int = 0,
    val completedFocusSessions: Int = 0,
    val totalFocusSeconds: Long = 0L,
    val totalBreakSeconds: Long = 0L,
) {
    val isEmpty: Boolean
        get() = completedTomatoCount == 0 &&
            restartCount == 0 &&
            completedFocusSessions == 0 &&
            totalFocusSeconds == 0L &&
            totalBreakSeconds == 0L
}

sealed interface AudioEvent {
    data object None : AudioEvent

    data class ReminderTone(
        val enabled: Boolean,
        val path: String = "",
        val volumePercent: Int = 70,
        val vibrate: Boolean = false,
    ) : AudioEvent
}
