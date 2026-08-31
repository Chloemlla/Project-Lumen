package com.projectlumen.app.openapi

import com.projectlumen.app.ProjectLumenApplication
import com.projectlumen.app.R
import com.projectlumen.app.core.database.entities.AppSettingsEntity
import com.projectlumen.app.core.database.entities.RuntimeStateEntity
import com.projectlumen.app.core.enums.ActiveEngine
import com.projectlumen.app.core.enums.PomodoroPhase
import com.projectlumen.app.core.enums.ReminderPhase
import com.projectlumen.app.core.overlay.EyeProtectionOverlayService
import com.projectlumen.app.core.repositories.StatisticsRepository
import com.projectlumen.app.core.runtime.AudioEvent
import com.projectlumen.app.core.runtime.EyeStatsDelta
import com.projectlumen.app.core.runtime.PomodoroEngine
import com.projectlumen.app.core.runtime.PomodoroStatsDelta
import com.projectlumen.app.core.runtime.ReminderEngine
import com.projectlumen.app.core.runtime.RuntimeTransition
import com.projectlumen.app.core.time.todayKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class LumenOpenRuntimeController(
    private val app: ProjectLumenApplication,
) {
    private val telemetryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val settingsRepository by lazy {
        app.settingsRepository()
    }
    private val runtimeRepository by lazy { app.runtimeRepository() }
    private val statisticsRepository by lazy {
        StatisticsRepository(app.database.dailyEyeStatsDao(), app.database.dailyPomodoroStatsDao())
    }
    private val reminderEngine = ReminderEngine()
    private val pomodoroEngine = PomodoroEngine()

    suspend fun getEyeFatigueLevel(): Int = withContext(Dispatchers.IO) {
        val nowMillis = System.currentTimeMillis()
        val settings = settingsRepository.getOrDefault()
        val runtime = runtimeRepository.getOrDefault()
        val stats = app.database.dailyEyeStatsDao().get(todayKey(nowMillis))
        val continuousRatio = continuousScreenTimeMillis(runtime, nowMillis).toFloat() /
            (settings.warnIntervalMinutes.coerceAtLeast(1) * 60_000f)
        val baseFatigue = (continuousRatio.coerceIn(0f, 1.4f) * 65f).roundToInt()
        val proximityPenalty = if (runtime.proximityTooClose) 15 else 0
        val lowLightPenalty = if (runtime.ambientTooDark) 8 else 0
        val dryEyePenalty = if ((stats?.eyeDryWarningCount ?: 0) > 0) 8 else 0
        (baseFatigue + proximityPenalty + lowLightPenalty + dryEyePenalty).coerceIn(0, 100)
    }

    suspend fun getContinuousScreenTime(): Long = withContext(Dispatchers.IO) {
        continuousScreenTimeMillis(runtimeRepository.getOrDefault(), System.currentTimeMillis())
    }

    suspend fun isRestingNow(): Boolean = withContext(Dispatchers.IO) {
        val runtime = runtimeRepository.getOrDefault()
        runtime.activeEngine == ActiveEngine.REMINDER.name &&
            runtime.reminderPhase == ReminderPhase.RESTING.name
    }

    suspend fun startFocusSession(
        tag: String?,
        durationMs: Long,
        sourceApp: String?,
    ) = withContext(Dispatchers.IO) {
        val settings = settingsRepository.getOrDefault()
        val nowMillis = System.currentTimeMillis()
        val duration = durationMs
            .takeIf { it > 0L }
            ?: settings.pomodoroWorkMinutes.coerceAtLeast(1) * 60_000L
        app.notifications.cancelAllScheduled()
        var eyeDelta = EyeStatsDelta()
        var pomodoroDelta = PomodoroStatsDelta()
        val nextRuntime = runtimeRepository.update { current ->
            when (current.activeEngine) {
                ActiveEngine.REMINDER.name -> if (current.reminderPhase in reminderWorkPhases) {
                    // skipBreak is used purely as the settlement calculation; the skip counter
                    // belongs to the user pressing "skip", not to an external app taking over.
                    eyeDelta = reminderEngine.skipBreak(settings, current, nowMillis)
                        .eyeStatsDelta
                        .copy(skipCount = 0)
                }
                ActiveEngine.POMODORO.name ->
                    pomodoroDelta = pomodoroEngine.stop(current, nowMillis).pomodoroStatsDelta
            }
            // Copy instead of building a fresh row: sensor flags, warning debounce timestamps and
            // the user's pause/quiet-hours state live here too and must survive an external call.
            current.copy(
                activeEngine = ActiveEngine.POMODORO.name,
                reminderPhase = ReminderPhase.IDLE.name,
                nextPreAlertAt = 0L,
                nextReminderAt = 0L,
                pomodoroPhase = PomodoroPhase.FOCUS.name,
                pomodoroPhaseStartedAt = nowMillis,
                pomodoroPhaseEndAt = nowMillis + duration.coerceIn(MIN_FOCUS_DURATION_MS, MAX_FOCUS_DURATION_MS),
                pomodoroCycleIndex = current.pomodoroCycleIndex.coerceAtLeast(1),
                lastStatsTickAt = nowMillis,
                updatedAt = nowMillis,
            )
        }
        statisticsRepository.applyEyeDelta(settings.statsEnabled, nowMillis, eyeDelta)
        statisticsRepository.applyPomodoroDelta(settings.statsEnabled, nowMillis, pomodoroDelta)
        playAudioEvent(
            AudioEvent.ReminderTone(
                enabled = settings.soundEnabled && settings.pomodoroWorkStartSoundEnabled,
                volumePercent = settings.pomodoroWorkStartVolumePercent,
            ),
        )
        refreshActiveNotifications(settings, nextRuntime)
        uploadOpenApiTelemetry(sourceApp ?: tag)
    }

    suspend fun stopFocusSession(sourceApp: String?) = withContext(Dispatchers.IO) {
        val settings = settingsRepository.getOrDefault()
        val nowMillis = System.currentTimeMillis()
        // 引擎计算必须在仓库锁内基于锁内读到的行做：Open API 与前台服务并发写同一行，
        // 锁外先读再算出的整条快照会把并发写者的字段一起盖回去。
        var transition: RuntimeTransition? = null
        runtimeRepository.update { current ->
            if (current.activeEngine != ActiveEngine.POMODORO.name) return@update current
            pomodoroEngine.stop(current, nowMillis).also { transition = it }.nextRuntime
        }
        val applied = transition ?: return@withContext
        statisticsRepository.applyPomodoroDelta(settings.statsEnabled, nowMillis, applied.pomodoroStatsDelta)
        app.notifications.cancelAllScheduled()
        app.notifications.cancelOngoingStatus()
        app.stopTimerService()
        uploadOpenApiTelemetry(sourceApp)
    }

    suspend fun triggerEyeRelaxation(
        sourceApp: String?,
        requestedDurationSeconds: Int? = null,
    ): Int = withContext(Dispatchers.IO) {
        val settings = settingsRepository.getOrDefault()
        val nowMillis = System.currentTimeMillis()
        val durationSeconds = (requestedDurationSeconds ?: settings.restDurationSeconds)
            .coerceIn(MIN_REST_DURATION_SECONDS, MAX_REST_DURATION_SECONDS)
        val blockingDurationSeconds = if (settings.globalOverlayEnabled) {
            durationSeconds.coerceAtLeast(settings.overlayRestDurationSeconds)
        } else {
            durationSeconds
        }
        val restSettings = settings.copy(restDurationSeconds = durationSeconds)
        // 同 stopFocusSession：分支判定与引擎计算都要基于锁内读到的行，否则整条覆盖会丢并发写。
        var transition: RuntimeTransition? = null
        var pomodoroStop: RuntimeTransition? = null
        runtimeRepository.update { current ->
            val next = if (current.activeEngine == ActiveEngine.REMINDER.name &&
                current.reminderPhase in reminderWorkPhases
            ) {
                reminderEngine.startBreak(restSettings, current, nowMillis)
            } else {
                RuntimeTransition(nextRuntime = newExternalRestState(current, nowMillis, durationSeconds))
            }
            if (current.activeEngine == ActiveEngine.POMODORO.name) {
                pomodoroStop = pomodoroEngine.stop(current, nowMillis)
            }
            transition = next
            next.nextRuntime
        }
        val applied = transition ?: return@withContext blockingDurationSeconds

        statisticsRepository.applyEyeDelta(settings.statsEnabled, nowMillis, applied.eyeStatsDelta)
        pomodoroStop?.let {
            statisticsRepository.applyPomodoroDelta(settings.statsEnabled, nowMillis, it.pomodoroStatsDelta)
        }
        playAudioEvent(applied.audioEvent)
        showBlockingOverlayIfNeeded(settings, durationSeconds)
        refreshActiveNotifications(settings, applied.nextRuntime)
        uploadOpenApiTelemetry(sourceApp)
        blockingDurationSeconds
    }

    suspend fun recordOpenLaunch(
        sourceApp: String?,
    ) = withContext(Dispatchers.IO) {
        uploadOpenApiTelemetry(sourceApp)
    }

    private fun continuousScreenTimeMillis(
        runtime: RuntimeStateEntity,
        nowMillis: Long,
    ): Long {
        return when {
            runtime.activeEngine == ActiveEngine.REMINDER.name &&
                runtime.reminderPhase in reminderWorkPhases ->
                nowMillis.elapsedSince(runtime.reminderStartedAt)
            runtime.activeEngine == ActiveEngine.POMODORO.name &&
                runtime.pomodoroPhase == PomodoroPhase.FOCUS.name ->
                nowMillis.elapsedSince(runtime.pomodoroPhaseStartedAt)
            else -> 0L
        }
    }

    private fun newExternalRestState(
        current: RuntimeStateEntity,
        nowMillis: Long,
        durationSeconds: Int,
    ): RuntimeStateEntity {
        // Copy so sensor flags, warning debounce timestamps and pause state survive the takeover.
        return current.copy(
            activeEngine = ActiveEngine.REMINDER.name,
            reminderPhase = ReminderPhase.RESTING.name,
            reminderStartedAt = nowMillis,
            nextPreAlertAt = 0L,
            nextReminderAt = 0L,
            breakStartedAt = nowMillis,
            breakEndAt = nowMillis + durationSeconds * 1000L,
            pomodoroPhase = PomodoroPhase.IDLE.name,
            pomodoroPhaseStartedAt = 0L,
            pomodoroPhaseEndAt = 0L,
            lastStatsTickAt = nowMillis,
            updatedAt = nowMillis,
        )
    }

    private fun showBlockingOverlayIfNeeded(settings: AppSettingsEntity, durationSeconds: Int) {
        if (!settings.globalOverlayEnabled) return
        EyeProtectionOverlayService.show(
            context = app,
            title = app.getString(R.string.overlay_break_title),
            message = app.getString(R.string.overlay_break_message),
            durationSeconds = durationSeconds.coerceAtLeast(settings.overlayRestDurationSeconds),
        )
    }

    private fun refreshActiveNotifications(settings: AppSettingsEntity, state: RuntimeStateEntity) {
        app.notifications.syncRuntimeAlarms(settings, state)
        if (!settings.keepAliveEnabled) app.stopTimerService()
        if (!settings.notificationEnabled && !settings.keepAliveEnabled) {
            app.notifications.cancelOngoingStatus()
            return
        }
        if (state.activeEngine != ActiveEngine.IDLE.name && settings.keepAliveEnabled) {
            app.startTimerService()
        }
        if (state.activeEngine != ActiveEngine.IDLE.name && settings.notificationEnabled) {
            app.notifications.showOngoingStatus(state)
        } else if (state.activeEngine == ActiveEngine.IDLE.name || !settings.keepAliveEnabled) {
            app.notifications.cancelOngoingStatus()
            if (!settings.keepAliveEnabled) app.stopTimerService()
        }
    }

    private fun playAudioEvent(event: AudioEvent) {
        when (event) {
            AudioEvent.None -> Unit
            is AudioEvent.ReminderTone -> app.audio.playReminderTone(event)
        }
    }

    // Fire-and-forget: an external caller must not wait on a network upload, and it must not be
    // able to force one either, or any app could drive uploads at its own rate.
    private fun uploadOpenApiTelemetry(sourceApp: String?) {
        val resolved = sanitizeLumenOpenSourceApp(
            sourceApp,
            fallback = LumenOpenContracts.SOURCE_APP_EXTERNAL,
        )
        telemetryScope.launch {
            runCatching { app.telemetry.uploadCurrentSnapshot(force = false, sourceApp = resolved) }
        }
    }

    private fun Long.elapsedSince(startMillis: Long): Long {
        return if (startMillis > 0L) (this - startMillis).coerceAtLeast(0L) else 0L
    }

    private companion object {
        private const val MIN_FOCUS_DURATION_MS = 60_000L
        private const val MAX_FOCUS_DURATION_MS = 8 * 60 * 60_000L
        private const val MIN_REST_DURATION_SECONDS = 5
        private const val MAX_REST_DURATION_SECONDS = 30 * 60
        private val reminderWorkPhases = setOf(
            ReminderPhase.WORKING.name,
            ReminderPhase.PRE_ALERT.name,
            ReminderPhase.AWAITING_ACTION.name,
        )
    }
}
