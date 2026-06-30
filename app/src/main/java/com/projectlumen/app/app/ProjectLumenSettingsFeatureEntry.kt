package com.projectlumen.app.app

import com.projectlumen.app.core.database.entities.AppSettingsEntity
import com.projectlumen.app.core.database.entities.DailyGoalEntity
import com.projectlumen.app.core.enums.ActiveEngine
import com.projectlumen.app.core.enums.AppThemeMode
import com.projectlumen.app.core.i18n.LocaleController
import com.projectlumen.app.core.repositories.DailyGoalsRepository
import com.projectlumen.app.core.repositories.RuntimeRepository
import com.projectlumen.app.core.repositories.SettingsRepository
import com.projectlumen.app.core.services.NotificationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class ProjectLumenSettingsFeatureEntry(
    private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val runtimeRepository: RuntimeRepository,
    private val dailyGoalsRepository: DailyGoalsRepository,
    private val runtimeEntry: ProjectLumenRuntimeFeatureEntry,
    private val notifications: NotificationService,
    private val stopTimerService: () -> Unit,
    private val scheduleProximityMonitoring: () -> Unit,
    private val cancelProximityMonitoring: () -> Unit,
    private val calibrateProximityMonitoring: () -> Unit,
    private val startLightMonitoring: () -> Unit,
    private val stopLightMonitoring: () -> Unit,
) {
    fun applyStartupMonitoring(settings: AppSettingsEntity) {
        if (settings.proximityMonitoringEnabled || settings.blinkMonitoringEnabled) scheduleProximityMonitoring()
        applyLightMonitoringSettings(settings)
    }

    fun updateSettings(transform: (AppSettingsEntity) -> AppSettingsEntity) {
        scope.launch {
            val current = settingsRepository.getOrDefault()
            val nowMillis = System.currentTimeMillis()
            val updated = settingsRepository.update(nowMillis, transform)
            val shouldRescheduleProximity = (updated.proximityMonitoringEnabled || updated.blinkMonitoringEnabled) && (
                current.proximityCheckIntervalMinutes != updated.proximityCheckIntervalMinutes ||
                    current.proximityCaptureSeconds != updated.proximityCaptureSeconds ||
                    current.proximityDistanceMultiplierPercent != updated.proximityDistanceMultiplierPercent ||
                    current.proximityFaceThresholdPercent != updated.proximityFaceThresholdPercent ||
                    current.proximityAlertCooldownSeconds != updated.proximityAlertCooldownSeconds ||
                    current.blinkMonitoringEnabled != updated.blinkMonitoringEnabled ||
                    current.blinkNoBlinkThresholdSeconds != updated.blinkNoBlinkThresholdSeconds ||
                    current.blinkAlertCooldownSeconds != updated.blinkAlertCooldownSeconds
                )
            runtimeEntry.applySettingsToActiveRuntime(updated, nowMillis)
            if (shouldRescheduleProximity) scheduleProximityMonitoring()
            applyLightMonitoringSettings(updated)
        }
    }

    fun setReminderEnabled(enabled: Boolean) {
        scope.launch {
            val nowMillis = System.currentTimeMillis()
            settingsRepository.update(nowMillis) { it.copy(reminderEnabled = enabled) }
            val state = runtimeRepository.getOrDefault()
            if (!enabled && state.activeEngine == ActiveEngine.REMINDER.name) {
                runtimeEntry.stopReminderRuntime()
            }
        }
    }

    fun setPomodoroEnabled(enabled: Boolean) {
        scope.launch {
            val nowMillis = System.currentTimeMillis()
            settingsRepository.update(nowMillis) { it.copy(pomodoroEnabled = enabled) }
            val state = runtimeRepository.getOrDefault()
            if (!enabled && state.activeEngine == ActiveEngine.POMODORO.name) {
                runtimeEntry.stopPomodoroRuntime()
            }
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        scope.launch {
            val settings = settingsRepository.update { it.copy(notificationEnabled = enabled) }
            runtimeEntry.refreshActiveNotifications(settings, runtimeRepository.getOrDefault())
        }
    }

    fun setKeepAliveEnabled(enabled: Boolean) {
        scope.launch {
            val settings = settingsRepository.update { it.copy(keepAliveEnabled = enabled) }
            runtimeEntry.refreshActiveNotifications(settings, runtimeRepository.getOrDefault())
        }
    }

    fun setProximityMonitoringEnabled(enabled: Boolean) {
        scope.launch {
            settingsRepository.update { it.copy(proximityMonitoringEnabled = enabled) }
            if (enabled) {
                scheduleProximityMonitoring()
            } else {
                val settings = settingsRepository.getOrDefault()
                if (settings.blinkMonitoringEnabled) {
                    scheduleProximityMonitoring()
                } else {
                    cancelProximityMonitoring()
                }
                val state = runtimeRepository.getOrDefault()
                runtimeRepository.upsert(
                    state.copy(
                        proximityMonitoringActive = false,
                        proximityTooClose = false,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    fun setBlinkMonitoringEnabled(enabled: Boolean) {
        scope.launch {
            val settings = settingsRepository.update { it.copy(blinkMonitoringEnabled = enabled) }
            if (settings.proximityMonitoringEnabled || settings.blinkMonitoringEnabled) {
                scheduleProximityMonitoring()
            } else {
                cancelProximityMonitoring()
            }
        }
    }

    fun setAmbientLightMonitoringEnabled(enabled: Boolean) {
        scope.launch {
            val settings = settingsRepository.update { it.copy(ambientLightMonitoringEnabled = enabled) }
            applyLightMonitoringSettings(settings)
        }
    }

    fun setAutoBrightnessEnabled(enabled: Boolean) {
        scope.launch {
            val settings = settingsRepository.update { it.copy(autoBrightnessEnabled = enabled) }
            applyLightMonitoringSettings(settings)
        }
    }

    fun calibrateProximity() {
        calibrateProximityMonitoring()
    }

    fun setLanguageCode(languageCode: String) {
        scope.launch {
            val normalized = LocaleController.normalize(languageCode)
            settingsRepository.update { it.copy(languageCode = normalized) }
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        scope.launch {
            settingsRepository.update { it.copy(themeMode = mode.name) }
        }
    }

    fun setAutoUpdateCheckEnabled(enabled: Boolean) {
        scope.launch {
            settingsRepository.update { it.copy(autoUpdateCheckEnabled = enabled) }
        }
    }

    fun updateDailyGoal(transform: (DailyGoalEntity) -> DailyGoalEntity) {
        scope.launch {
            dailyGoalsRepository.update(transform = transform)
        }
    }

    private fun applyLightMonitoringSettings(settings: AppSettingsEntity) {
        if (settings.ambientLightMonitoringEnabled || settings.autoBrightnessEnabled) {
            startLightMonitoring()
        } else {
            stopLightMonitoring()
        }
    }
}
