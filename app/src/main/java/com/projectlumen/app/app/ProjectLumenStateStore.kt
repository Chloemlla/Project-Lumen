package com.projectlumen.app.app

import com.projectlumen.app.core.database.entities.AppSettingsEntity
import com.projectlumen.app.core.database.entities.DailyGoalEntity
import com.projectlumen.app.core.database.entities.RuntimeStateEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

internal class ProjectLumenStateStore(
    repositories: ProjectLumenRepositories,
    scope: CoroutineScope,
    now: StateFlow<Long>,
) {
    private val settingsPreview = MutableStateFlow<AppSettingsEntity?>(null)
    private val settingsState = combine(
        repositories.settings.observe(),
        settingsPreview,
    ) { persisted, preview ->
        SettingsSnapshot(
            settings = resolveSettings(persisted, preview),
            persistedReady = persisted != null,
        )
    }

    private val baseDataState = combine(
        settingsState,
        repositories.runtime.observe(),
        repositories.statistics.observeEyeStats(),
        repositories.statistics.observePomodoroStats(),
        repositories.tipTemplates.observeAll(),
    ) { settingsState, runtime, eyeStats, pomodoroStats, templates ->
        ProjectLumenUiState(
            settings = settingsState.settings ?: AppSettingsEntity(),
            runtime = runtime ?: RuntimeStateEntity(),
            eyeStats = eyeStats,
            pomodoroStats = pomodoroStats,
            templates = templates,
            isReady = settingsState.persistedReady && runtime != null,
        )
    }

    private val dataState = combine(
        baseDataState,
        repositories.dailyGoals.observe(),
        repositories.entitlements.observeAll(),
        repositories.reminderPlans.observeActive(),
    ) { state, dailyGoal, entitlements, reminderPlans ->
        state.copy(
            dailyGoal = dailyGoal ?: DailyGoalEntity(),
            entitlements = entitlements,
            reminderPlans = reminderPlans,
            isReady = state.isReady && dailyGoal != null,
        )
    }

    val uiState = combine(dataState, now) { state, nowMillis ->
        state.copy(nowMillis = nowMillis)
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProjectLumenUiState(),
    )

    fun previewSettings(settings: AppSettingsEntity) {
        settingsPreview.value = settings
    }

    private fun resolveSettings(
        persisted: AppSettingsEntity?,
        preview: AppSettingsEntity?,
    ): AppSettingsEntity? {
        if (preview == null) return persisted
        if (persisted == null) return preview
        return if (preview.updatedAt > persisted.updatedAt) preview else persisted
    }

    private data class SettingsSnapshot(
        val settings: AppSettingsEntity?,
        val persistedReady: Boolean,
    )
}
