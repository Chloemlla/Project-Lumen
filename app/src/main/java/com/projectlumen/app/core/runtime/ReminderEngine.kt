package com.projectlumen.app.core.runtime

import com.projectlumen.app.core.database.entities.AppSettingsEntity
import com.projectlumen.app.core.database.entities.RuntimeStateEntity
import com.projectlumen.app.core.enums.ActiveEngine
import com.projectlumen.app.core.enums.ReminderPhase
import com.projectlumen.app.core.time.QuietHours
import com.projectlumen.app.core.time.coerceElapsedSecondsSince
import kotlin.math.max

class ReminderEngine {
    fun newWorkingState(settings: AppSettingsEntity, nowMillis: Long): RuntimeStateEntity {
        val startAt = if (QuietHours.isPauseTimerActive(settings, nowMillis)) {
            QuietHours.activeEndMillis(settings, nowMillis)
        } else {
            nowMillis
        }
        return newWorkingStateFrom(settings, startAt, nowMillis)
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
        val continuousSeconds = continuousWorkingSeconds(state, nowMillis)
        return RuntimeTransition(
            nextRuntime = state.copy(
                activeEngine = ActiveEngine.REMINDER.name,
                reminderPhase = ReminderPhase.RESTING.name,
                breakStartedAt = nowMillis,
                breakEndAt = nowMillis + settings.restDurationSeconds * 1000L,
                lastStatsTickAt = nowMillis,
                updatedAt = nowMillis,
            ),
            eyeStatsDelta = EyeStatsDelta(
                workingSeconds = elapsedWorkingSeconds(state, nowMillis),
                maxContinuousWorkSeconds = continuousSeconds,
            ),
            audioEvent = AudioEvent.ReminderTone(
                enabled = settings.soundEnabled && settings.restStartSoundEnabled,
                path = settings.restStartSoundPath,
                volumePercent = settings.restStartVolumePercent,
                vibrate = settings.vibrationEnabled,
            ),
        )
    }

    fun skipBreak(
        settings: AppSettingsEntity,
        state: RuntimeStateEntity,
        nowMillis: Long,
    ): RuntimeTransition {
        val continuousSeconds = continuousWorkingSeconds(state, nowMillis)
        return RuntimeTransition(
            nextRuntime = newWorkingState(settings, nowMillis),
            eyeStatsDelta = EyeStatsDelta(
                workingSeconds = elapsedWorkingSeconds(state, nowMillis),
                skipCount = 1,
                maxContinuousWorkSeconds = continuousSeconds,
            ),
        )
    }

    fun advance(
        settings: AppSettingsEntity,
        state: RuntimeStateEntity,
        nowMillis: Long,
    ): RuntimeTransition? {
        if (QuietHours.isPauseTimerActive(settings, nowMillis) && state.reminderPhase in activeWorkPhases) {
            val workEndAt = QuietHours.activeStartMillis(settings, nowMillis).coerceAtMost(nowMillis)
            return RuntimeTransition(
                nextRuntime = newWorkingStateFrom(
                    settings = settings,
                    startAt = QuietHours.activeEndMillis(settings, nowMillis),
                    updatedAt = nowMillis,
                ),
                eyeStatsDelta = EyeStatsDelta(
                    workingSeconds = workEndAt.coerceElapsedSecondsSince(max(state.reminderStartedAt, state.lastStatsTickAt)),
                    maxContinuousWorkSeconds = workEndAt.coerceElapsedSecondsSince(state.reminderStartedAt),
                ),
            )
        }

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
                        audioEvent = AudioEvent.ReminderTone(
                            enabled = settings.soundEnabled,
                            path = settings.restSoundPath,
                            volumePercent = settings.restEndVolumePercent,
                            vibrate = settings.vibrationEnabled,
                        ),
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
        if (QuietHours.isPauseTimerActive(settings, nowMillis) && state.reminderPhase in activeWorkPhases) {
            return newWorkingStateFrom(settings, QuietHours.activeEndMillis(settings, nowMillis), nowMillis)
        }
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

    private fun newWorkingStateFrom(
        settings: AppSettingsEntity,
        startAt: Long,
        updatedAt: Long,
    ): RuntimeStateEntity {
        val reminderAt = startAt + settings.warnIntervalMinutes * 60_000L
        val preAlertAt = if (settings.preAlertEnabled) {
            reminderAt - settings.preAlertSeconds * 1000L
        } else {
            reminderAt
        }
        return RuntimeStateEntity(
            activeEngine = ActiveEngine.REMINDER.name,
            reminderPhase = ReminderPhase.WORKING.name,
            reminderStartedAt = startAt,
            nextPreAlertAt = preAlertAt.coerceAtLeast(startAt),
            nextReminderAt = reminderAt,
            lastStatsTickAt = startAt,
            updatedAt = updatedAt,
        )
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
                audioEvent = AudioEvent.ReminderTone(
                    enabled = settings.soundEnabled && settings.preAlertSoundEnabled,
                    volumePercent = settings.preAlertVolumePercent,
                    vibrate = settings.vibrationEnabled,
                ),
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
        val continuousSeconds = continuousWorkingSeconds(state, nowMillis)
        if (QuietHours.recordOnlyActive(settings, nowMillis)) {
            return RuntimeTransition(
                nextRuntime = newWorkingState(settings, nowMillis),
                eyeStatsDelta = EyeStatsDelta(
                    workingSeconds = elapsedWorkingSeconds(state, nowMillis),
                    maxContinuousWorkSeconds = continuousSeconds,
                ),
            )
        }

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
            eyeStatsDelta = EyeStatsDelta(
                workingSeconds = elapsedWorkingSeconds(state, nowMillis),
                maxContinuousWorkSeconds = continuousSeconds,
            ),
            audioEvent = if (settings.askBeforeBreak) {
                AudioEvent.ReminderTone(
                    enabled = settings.soundEnabled,
                    volumePercent = settings.restStartVolumePercent,
                    vibrate = settings.vibrationEnabled,
                )
            } else {
                AudioEvent.ReminderTone(
                    enabled = settings.soundEnabled && settings.restStartSoundEnabled,
                    path = settings.restStartSoundPath,
                    volumePercent = settings.restStartVolumePercent,
                    vibrate = settings.vibrationEnabled,
                )
            },
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

    private fun continuousWorkingSeconds(state: RuntimeStateEntity, nowMillis: Long): Long {
        return nowMillis.coerceElapsedSecondsSince(state.reminderStartedAt)
    }

    private companion object {
        private val activeWorkPhases = setOf(
            ReminderPhase.WORKING.name,
            ReminderPhase.PRE_ALERT.name,
            ReminderPhase.AWAITING_ACTION.name,
        )
    }
}
