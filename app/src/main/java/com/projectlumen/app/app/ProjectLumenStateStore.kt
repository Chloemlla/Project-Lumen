package com.projectlumen.app.app

import com.projectlumen.app.core.database.entities.AppSettingsEntity
import com.projectlumen.app.core.database.entities.DailyGoalEntity
import com.projectlumen.app.core.database.entities.RuntimeStateEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

internal class ProjectLumenStateStore(
    repositories: ProjectLumenRepositories,
    scope: CoroutineScope,
    now: StateFlow<Long>,
) {
    private val baseDataState = combine(
        repositories.settings.observe(),
        repositories.runtime.observe(),
        repositories.statistics.observeEyeStats(),
        repositories.statistics.observePomodoroStats(),
        repositories.tipTemplates.observeAll(),
    ) { settings, runtime, eyeStats, pomodoroStats, templates ->
        ProjectLumenUiState(
            settings = settings ?: AppSettingsEntity(),
            runtime = runtime ?: RuntimeStateEntity(),
            eyeStats = eyeStats,
            pomodoroStats = pomodoroStats,
            templates = templates,
            isReady = settings != null && runtime != null,
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
}
