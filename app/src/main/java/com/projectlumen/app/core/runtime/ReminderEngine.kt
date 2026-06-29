package com.projectlumen.app.core.runtime

import com.projectlumen.app.core.database.entities.AppSettingsEntity
import com.projectlumen.app.core.database.entities.RuntimeStateEntity
import com.projectlumen.app.core.enums.ActiveEngine
import com.projectlumen.app.core.enums.ReminderPhase
import com.projectlumen.app.core.time.coerceElapsedSecondsSince
import kotlin.math.max

class ReminderEngine {
    fun newWorkingState(settings: AppSettingsEntity, nowMillis: Long): RuntimeStateEntity {
        val reminderAt = nowMillis + settings.warnIntervalMinutes * 60_000L
        val preAlertAt = if (settings.preAlertEnabled) {
            reminderAt - settings.preAlertSeconds * 1000L
        } else {
            reminderAt
        }
        return RuntimeStateEntity(
            activeEngine = ActiveEngine.REMINDER.name,
            reminderPhase = ReminderPhase.WORKING.name,
            reminderStartedAt = nowMillis,
            nextPreAlertAt = preAlertAt.coerceAtLeast(nowMillis),
            nextReminderAt = reminderAt,
            lastStatsTickAt = nowMillis,
            updatedAt = nowMillis,
        )
    }

    fun pause(state: RuntimeStateEntity, nowMillis: Long): RuntimeStateEntity {
        return state.copy(
            reminderPhase = ReminderPhase.PAUSED.name,
            isManuallyPaused = true,
            pausedAt = nowMillis,
            updatedAt = nowMillis,
        )
    }

    fun pauseForOneHour(state: RuntimeStateEntity, nowMillis: Long): RuntimeStateEntity {
        return state.copy(
            reminderPhase = ReminderPhase.PAUSED.name,
            isManuallyPaused = false,
            pausedAt = nowMillis,
            suspendedUntil = nowMillis + 60 * 60_000L,
            updatedAt = nowMillis,
        )
    }

    fun startBreak(
        settings: AppSettingsEntity,
        state: RuntimeStateEntity,
        nowMillis: Long,
    ): RuntimeTransition {
        return RuntimeTransition(
            nextRuntime = state.copy(
                activeEngine = ActiveEngine.REMINDER.name,
                reminderPhase = ReminderPhase.RESTING.name,
                breakStartedAt = nowMillis,
                breakEndAt = nowMillis + settings.restDurationSeconds * 1000L,
                lastStatsTickAt = nowMillis,
                updatedAt = nowMillis,
            ),
            eyeStatsDelta = EyeStatsDelta(workingSeconds = elapsedWorkingSeconds(state, nowMillis)),
        )
    }

    fun skipBreak(
        settings: AppSettingsEntity,
        state: RuntimeStateEntity,
        nowMillis: Long,
    ): RuntimeTransition {
        return RuntimeTransition(
            nextRuntime = newWorkingState(settings, nowMillis),
            eyeStatsDelta = EyeStatsDelta(
                workingSeconds = elapsedWorkingSeconds(state, nowMillis),
                skipCount = 1,
            ),
        )
    }

    fun advance(
        settings: AppSettingsEntity,
        state: RuntimeStateEntity,
        nowMillis: Long,
    ): RuntimeTransition? {
        return when (state.reminderPhase) {
            ReminderPhase.PAUSED.name -> {
                if (!state.isManuallyPaused && state.suspendedUntil > 0L && nowMillis >= state.suspendedUntil) {
                    RuntimeTransition(nextRuntime = newWorkingState(settings, nowMillis))
                } else {
                    null
                }
            }

            ReminderPhase.WORKING.name -> advanceWorking(settings, state, nowMillis)

            ReminderPhase.PRE_ALERT.name -> {
                if (nowMillis >= state.nextReminderAt) {
                    dueReminderTransition(settings, state, nowMillis)
                } else {
                    null
                }
            }

            ReminderPhase.RESTING.name -> {
                if (nowMillis >= state.breakEndAt) {
                    RuntimeTransition(
                        nextRuntime = newWorkingState(settings, nowMillis),
                        eyeStatsDelta = EyeStatsDelta(
                            restSeconds = elapsedRestSeconds(state, nowMillis),
                            completedBreakCount = 1,
                        ),
                        audioEvent = AudioEvent.ReminderTone(settings.soundEnabled, settings.restSoundPath),
                    )
                } else {
                    null
                }
            }

            else -> null
        }
    }

    fun adjustForSettings(
        state: RuntimeStateEntity,
        settings: AppSettingsEntity,
        nowMillis: Long,
    ): RuntimeStateEntity {
        if (!settings.reminderEnabled) return RuntimeStateEntity(updatedAt = nowMillis)
        return when (state.reminderPhase) {
            ReminderPhase.WORKING.name,
            ReminderPhase.PRE_ALERT.name,
            ReminderPhase.AWAITING_ACTION.name -> {
                val reminderStartedAt = state.reminderStartedAt.takeIf { it > 0L } ?: nowMillis
                val reminderAt = reminderStartedAt + settings.warnIntervalMinutes * 60_000L
                val preAlertAt = if (settings.preAlertEnabled) {
                    reminderAt - settings.preAlertSeconds * 1000L
                } else {
                    reminderAt
                }.coerceAtLeast(reminderStartedAt)
                val phase = when {
                    nowMillis >= reminderAt -> {
                        if (state.reminderPhase == ReminderPhase.AWAITING_ACTION.name && settings.askBeforeBreak) {
                            ReminderPhase.AWAITING_ACTION.name
                        } else {
                            ReminderPhase.WORKING.name
                        }
                    }
                    settings.preAlertEnabled && nowMillis >= preAlertAt -> ReminderPhase.PRE_ALERT.name
                    else -> ReminderPhase.WORKING.name
                }
                state.copy(
                    reminderPhase = phase,
                    reminderStartedAt = reminderStartedAt,
                    nextPreAlertAt = preAlertAt,
                    nextReminderAt = reminderAt,
                    updatedAt = nowMillis,
                )
            }
            ReminderPhase.RESTING.name -> {
                val breakStartedAt = state.breakStartedAt.takeIf { it > 0L } ?: nowMillis
                state.copy(
                    breakStartedAt = breakStartedAt,
                    breakEndAt = breakStartedAt + settings.restDurationSeconds * 1000L,
                    updatedAt = nowMillis,
                )
            }
            else -> state
        }
    }

    private fun advanceWorking(
        settings: AppSettingsEntity,
        state: RuntimeStateEntity,
        nowMillis: Long,
    ): RuntimeTransition? {
        return if (settings.preAlertEnabled && nowMillis >= state.nextPreAlertAt && nowMillis < state.nextReminderAt) {
            RuntimeTransition(
                nextRuntime = state.copy(reminderPhase = ReminderPhase.PRE_ALERT.name, updatedAt = nowMillis),
                eyeStatsDelta = EyeStatsDelta(preAlertCount = 1),
                audioEvent = AudioEvent.ReminderTone(settings.soundEnabled && settings.preAlertSoundEnabled),
            )
        } else if (nowMillis >= state.nextReminderAt) {
            dueReminderTransition(settings, state, nowMillis)
        } else {
            null
        }
    }

    private fun dueReminderTransition(
        settings: AppSettingsEntity,
        state: RuntimeStateEntity,
        nowMillis: Long,
    ): RuntimeTransition {
        val nextState = if (settings.askBeforeBreak) {
            state.copy(
                reminderPhase = ReminderPhase.AWAITING_ACTION.name,
                lastStatsTickAt = nowMillis,
                updatedAt = nowMillis,
            )
        } else {
            state.copy(
                reminderPhase = ReminderPhase.RESTING.name,
                breakStartedAt = nowMillis,
                breakEndAt = nowMillis + settings.restDurationSeconds * 1000L,
                lastStatsTickAt = nowMillis,
                updatedAt = nowMillis,
            )
        }
        return RuntimeTransition(
            nextRuntime = nextState,
            eyeStatsDelta = EyeStatsDelta(workingSeconds = elapsedWorkingSeconds(state, nowMillis)),
        )
    }

    private fun elapsedWorkingSeconds(state: RuntimeStateEntity, nowMillis: Long): Long {
        val start = max(state.reminderStartedAt, state.lastStatsTickAt)
        return nowMillis.coerceElapsedSecondsSince(start)
    }

    private fun elapsedRestSeconds(state: RuntimeStateEntity, nowMillis: Long): Long {
        val start = max(state.breakStartedAt, state.lastStatsTickAt)
        return nowMillis.coerceElapsedSecondsSince(start)
    }
}
