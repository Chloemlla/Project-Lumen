package com.projectlumen.app.core.runtime

import com.projectlumen.app.core.database.entities.AppSettingsEntity
import com.projectlumen.app.core.database.entities.RuntimeStateEntity
import com.projectlumen.app.core.enums.ActiveEngine
import com.projectlumen.app.core.enums.PomodoroPhase
import kotlin.math.max

class PomodoroEngine {
    fun start(settings: AppSettingsEntity, nowMillis: Long): RuntimeTransition {
        return RuntimeTransition(
            nextRuntime = RuntimeStateEntity(
                activeEngine = ActiveEngine.POMODORO.name,
                pomodoroPhase = PomodoroPhase.FOCUS.name,
                pomodoroPhaseStartedAt = nowMillis,
                pomodoroPhaseEndAt = nowMillis + settings.pomodoroWorkMinutes * 60_000L,
                pomodoroCycleIndex = 1,
                updatedAt = nowMillis,
            ),
            audioEvent = AudioEvent.ReminderTone(
                settings.soundEnabled && settings.pomodoroWorkStartSoundEnabled,
                settings.pomodoroWorkStartSoundPath,
                settings.pomodoroWorkStartVolumePercent,
                settings.vibrationEnabled,
            ),
        )
    }

    fun stop(state: RuntimeStateEntity, nowMillis: Long): RuntimeTransition {
        val shouldCountRestart = state.activeEngine == ActiveEngine.POMODORO.name &&
            state.pomodoroPhase != PomodoroPhase.IDLE.name
        return RuntimeTransition(
            nextRuntime = RuntimeStateEntity(updatedAt = nowMillis),
            pomodoroStatsDelta = if (shouldCountRestart) {
                PomodoroStatsDelta(restartCount = 1)
            } else {
                PomodoroStatsDelta()
            },
        )
    }

    fun advance(
        settings: AppSettingsEntity,
        state: RuntimeStateEntity,
        nowMillis: Long,
    ): RuntimeTransition? {
        if (state.pomodoroPhaseEndAt <= 0L || nowMillis < state.pomodoroPhaseEndAt) return null
        return when (state.pomodoroPhase) {
            PomodoroPhase.FOCUS.name -> {
                val isLongBreak = state.pomodoroCycleIndex >= 4
                val nextPhase = if (isLongBreak) PomodoroPhase.LONG_BREAK else PomodoroPhase.SHORT_BREAK
                val breakMinutes = if (isLongBreak) settings.pomodoroLongBreakMinutes else settings.pomodoroShortBreakMinutes
                RuntimeTransition(
                    nextRuntime = state.copy(
                        pomodoroPhase = nextPhase.name,
                        pomodoroPhaseStartedAt = nowMillis,
                        pomodoroPhaseEndAt = nowMillis + breakMinutes * 60_000L,
                        updatedAt = nowMillis,
                    ),
                    pomodoroStatsDelta = PomodoroStatsDelta(
                        completedTomatoCount = if (isLongBreak) 1 else 0,
                        completedFocusSessions = 1,
                        totalFocusSeconds = max(0L, (state.pomodoroPhaseEndAt - state.pomodoroPhaseStartedAt) / 1000L),
                    ),
                    audioEvent = AudioEvent.ReminderTone(
                        settings.soundEnabled && settings.pomodoroWorkEndSoundEnabled,
                        settings.pomodoroWorkEndSoundPath,
                        settings.pomodoroWorkEndVolumePercent,
                        settings.vibrationEnabled,
                    ),
                )
            }

            PomodoroPhase.SHORT_BREAK.name,
            PomodoroPhase.LONG_BREAK.name -> {
                val wasLongBreak = state.pomodoroPhase == PomodoroPhase.LONG_BREAK.name
                RuntimeTransition(
                    nextRuntime = state.copy(
                        pomodoroPhase = PomodoroPhase.FOCUS.name,
                        pomodoroPhaseStartedAt = nowMillis,
                        pomodoroPhaseEndAt = nowMillis + settings.pomodoroWorkMinutes * 60_000L,
                        pomodoroCycleIndex = if (wasLongBreak) 1 else state.pomodoroCycleIndex + 1,
                        updatedAt = nowMillis,
                    ),
                    pomodoroStatsDelta = PomodoroStatsDelta(
                        totalBreakSeconds = max(0L, (state.pomodoroPhaseEndAt - state.pomodoroPhaseStartedAt) / 1000L),
                    ),
                    audioEvent = AudioEvent.ReminderTone(
                        settings.soundEnabled && settings.pomodoroWorkStartSoundEnabled,
                        settings.pomodoroWorkStartSoundPath,
                        settings.pomodoroWorkStartVolumePercent,
                        settings.vibrationEnabled,
                    ),
                )
            }

            else -> null
        }
    }

    fun adjustForSettings(
        state: RuntimeStateEntity,
        settings: AppSettingsEntity,
        nowMillis: Long,
    ): RuntimeStateEntity {
        if (!settings.pomodoroEnabled) return RuntimeStateEntity(updatedAt = nowMillis)
        val durationMinutes = when (state.pomodoroPhase) {
            PomodoroPhase.FOCUS.name -> settings.pomodoroWorkMinutes
            PomodoroPhase.SHORT_BREAK.name -> settings.pomodoroShortBreakMinutes
            PomodoroPhase.LONG_BREAK.name -> settings.pomodoroLongBreakMinutes
            else -> return state
        }
        val phaseStartedAt = state.pomodoroPhaseStartedAt.takeIf { it > 0L } ?: nowMillis
        return state.copy(
            pomodoroPhaseStartedAt = phaseStartedAt,
            pomodoroPhaseEndAt = phaseStartedAt + durationMinutes * 60_000L,
            updatedAt = nowMillis,
        )
    }
}
