package com.projectlumen.app.core.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.projectlumen.app.ProjectLumenApplication
import com.projectlumen.app.core.database.entities.AppSettingsEntity
import com.projectlumen.app.core.database.entities.RuntimeStateEntity
import com.projectlumen.app.core.enums.ReminderPhase
import com.projectlumen.app.core.overlay.EyeProtectionOverlayService
import com.projectlumen.app.core.repositories.RuntimeRepository
import com.projectlumen.app.core.repositories.StatisticsRepository
import com.projectlumen.app.core.runtime.AudioEvent
import com.projectlumen.app.core.runtime.ReminderEngine
import com.projectlumen.app.core.runtime.RuntimeTransition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            val app = context.applicationContext as? ProjectLumenApplication
            runCatching {
                app ?: return@runCatching
                val db = app.database
                val settings = app.settingsRepository().get()
                val runtimeRepository = app.runtimeRepository()
                val statisticsRepository = StatisticsRepository(db.dailyEyeStatsDao(), db.dailyPomodoroStatsDao())
                val reminderEngine = ReminderEngine()
                val now = System.currentTimeMillis()
                when (intent.action) {
                    ACTION_START_BREAK -> {
                        if (settings != null) {
                            // Serialised against the timer loop so the snapshot cannot go stale
                            // between the read and the write.
                            RuntimeAdvanceGate.withAdvanceLock {
                                applyEngineAction(
                                    app, runtimeRepository, statisticsRepository, settings, now,
                                ) { current -> reminderEngine.startBreak(settings, current, now) }
                            }
                        }
                    }

                    ACTION_SKIP_BREAK -> {
                        if (settings != null) {
                            RuntimeAdvanceGate.withAdvanceLock {
                                applyEngineAction(
                                    app, runtimeRepository, statisticsRepository, settings, now,
                                ) { current -> reminderEngine.skipBreak(settings, current, now) }
                            }
                        }
                    }

                    ACTION_STOP_ALL -> {
                        RuntimeAdvanceGate.withAdvanceLock { runtimeRepository.reset(now) }
                        app.notifications.cancelAllScheduled()
                        app.notifications.cancelOngoingStatus()
                        context.stopService(Intent(context, TimerForegroundService::class.java))
                    }
                }
            }
                .onFailure { throwable -> app?.recordHandledFailure(throwable) }
            pendingResult.finish()
        }
    }

    private suspend fun applyEngineAction(
        app: ProjectLumenApplication,
        runtimeRepository: RuntimeRepository,
        statisticsRepository: StatisticsRepository,
        settings: AppSettingsEntity,
        now: Long,
        transitionOf: (RuntimeStateEntity) -> RuntimeTransition,
    ) {
        // The engine runs inside the repository lock so the transition is derived from the row that
        // is about to be replaced, instead of a snapshot read before the lock was taken.
        var transition: RuntimeTransition? = null
        runtimeRepository.update { current ->
            val computed = transitionOf(current)
            transition = computed
            computed.nextRuntime
        }
        val applied = transition ?: return
        statisticsRepository.applyEyeDelta(settings.statsEnabled, now, applied.eyeStatsDelta)
        playAudioEvent(app, applied.audioEvent)
        if (settings.globalOverlayEnabled && applied.nextRuntime.reminderPhase == ReminderPhase.RESTING.name) {
            EyeProtectionOverlayService.show(
                context = app,
                title = app.getString(com.projectlumen.app.R.string.overlay_break_title),
                message = app.getString(com.projectlumen.app.R.string.overlay_break_message),
                durationSeconds = settings.restDurationSeconds.coerceAtLeast(settings.overlayRestDurationSeconds),
            )
        }
        refreshAfterAction(app, settings, applied.nextRuntime)
    }

    private fun playAudioEvent(app: ProjectLumenApplication, event: AudioEvent) {
        when (event) {
            AudioEvent.None -> Unit
            is AudioEvent.ReminderTone -> app.audio.playReminderTone(event)
        }
    }

    private fun refreshAfterAction(
        app: ProjectLumenApplication,
        settings: AppSettingsEntity,
        runtime: RuntimeStateEntity,
    ) {
        if (settings.keepAliveEnabled) app.startTimerService()
        app.notifications.syncRuntimeAlarms(settings, runtime)
        if (!settings.notificationEnabled) return
        app.notifications.showOngoingStatus(runtime)
    }

    companion object {
        const val ACTION_START_BREAK = "com.projectlumen.app.action.START_BREAK"
        const val ACTION_SKIP_BREAK = "com.projectlumen.app.action.SKIP_BREAK"
        const val ACTION_STOP_ALL = "com.projectlumen.app.action.STOP_ALL"
    }
}
