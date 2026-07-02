package com.projectlumen.app.openapi

import com.projectlumen.app.ProjectLumenApplication
import com.projectlumen.app.R
import com.projectlumen.app.core.database.entities.AppSettingsEntity
import com.projectlumen.app.core.database.entities.RuntimeStateEntity
import com.projectlumen.app.core.enums.ActiveEngine
import com.projectlumen.app.core.enums.PomodoroPhase
import com.projectlumen.app.core.enums.ReminderPhase
import com.projectlumen.app.core.overlay.EyeProtectionOverlayService
import com.projectlumen.app.core.repositories.RuntimeRepository
import com.projectlumen.app.core.repositories.SettingsRepository
import com.projectlumen.app.core.repositories.StatisticsRepository
import com.projectlumen.app.core.runtime.AudioEvent
import com.projectlumen.app.core.runtime.PomodoroEngine
import com.projectlumen.app.core.runtime.ReminderEngine
import com.projectlumen.app.core.runtime.RuntimeTransition
import com.projectlumen.app.core.time.todayKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class LumenOpenRuntimeController(
    private val app: ProjectLumenApplication,
) {
    private val settingsRepository by lazy {
        SettingsRepository(app.database.appSettingsDao(), app.eyeCarePreferences)
    }
    private val runtimeRepository by lazy { RuntimeRepository(app.database.runtimeStateDao()) }
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
        val nextRuntime = RuntimeStateEntity(
            activeEngine = ActiveEngine.POMODORO.name,
            pomodoroPhase = PomodoroPhase.FOCUS.name,
            pomodoroPhaseStartedAt = nowMillis,
            pomodoroPhaseEndAt = nowMillis + duration.coerceIn(MIN_FOCUS_DURATION_MS, MAX_FOCUS_DURATION_MS),
            pomodoroCycleIndex = 1,
            updatedAt = nowMillis,
        )
        app.notifications.cancelAllScheduled()
        runtimeRepository.upsert(nextRuntime)
        playAudioEvent(
            AudioEvent.ReminderTone(
                enabled = settings.soundEnabled && settings.pomodoroWorkStartSoundEnabled,
                path = settings.pomodoroWorkStartSoundPath,
                volumePercent = settings.pomodoroWorkStartVolumePercent,
                vibrate = settings.vibrationEnabled,
            ),
        )
        refreshActiveNotifications(settings, nextRuntime)
        uploadOpenApiTelemetry(sourceApp ?: tag)
    }

    suspend fun stopFocusSession(sourceApp: String?) = withContext(Dispatchers.IO) {
        val settings = settingsRepository.getOrDefault()
        val runtime = runtimeRepository.getOrDefault()
        if (runtime.activeEngine != ActiveEngine.POMODORO.name) return@withContext
        val nowMillis = System.currentTimeMillis()
        val transition = pomodoroEngine.stop(runtime, nowMillis)
        statisticsRepository.applyPomodoroDelta(settings.statsEnabled, nowMillis, transition.pomodoroStatsDelta)
        runtimeRepository.upsert(transition.nextRuntime)
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
        val runtime = runtimeRepository.getOrDefault()
        val nowMillis = System.currentTimeMillis()
        val durationSeconds = (requestedDurationSeconds ?: settings.restDurationSeconds)
            .coerceIn(MIN_REST_DURATION_SECONDS, MAX_REST_DURATION_SECONDS)
        val blockingDurationSeconds = if (settings.globalOverlayEnabled) {
            durationSeconds.coerceAtLeast(settings.overlayRestDurationSeconds)
        } else {
            durationSeconds
        }
        val restSettings = settings.copy(restDurationSeconds = durationSeconds)
        val transition = if (runtime.activeEngine == ActiveEngine.REMINDER.name &&
            runtime.reminderPhase in reminderWorkPhases
        ) {
            reminderEngine.startBreak(restSettings, runtime, nowMillis)
        } else {
            RuntimeTransition(nextRuntime = newExternalRestState(nowMillis, durationSeconds))
        }

        statisticsRepository.applyEyeDelta(settings.statsEnabled, nowMillis, transition.eyeStatsDelta)
        if (runtime.activeEngine == ActiveEngine.POMODORO.name) {
            statisticsRepository.applyPomodoroDelta(
                settings.statsEnabled,
                nowMillis,
                pomodoroEngine.stop(runtime, nowMillis).pomodoroStatsDelta,
            )
        }
        runtimeRepository.upsert(transition.nextRuntime)
        playAudioEvent(transition.audioEvent)
        showBlockingOverlayIfNeeded(settings, durationSeconds)
        refreshActiveNotifications(settings, transition.nextRuntime)
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
        nowMillis: Long,
        durationSeconds: Int,
    ): RuntimeStateEntity {
        return RuntimeStateEntity(
            activeEngine = ActiveEngine.REMINDER.name,
            reminderPhase = ReminderPhase.RESTING.name,
            reminderStartedAt = nowMillis,
            breakStartedAt = nowMillis,
            breakEndAt = nowMillis + durationSeconds * 1000L,
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
            is AudioEvent.ReminderTone -> app.audio.playReminderTone(
                enabled = event.enabled,
                soundPath = event.path,
                volumePercent = event.volumePercent,
                vibrate = event.vibrate,
            )
        }
    }

    private suspend fun uploadOpenApiTelemetry(sourceApp: String?) {
        app.telemetry.uploadCurrentSnapshot(
            force = true,
            sourceApp = sanitizeLumenOpenSourceApp(
                sourceApp,
                fallback = LumenOpenContracts.SOURCE_APP_EXTERNAL,
            ),
        )
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
