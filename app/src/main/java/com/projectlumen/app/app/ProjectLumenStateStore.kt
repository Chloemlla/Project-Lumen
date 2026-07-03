package com.projectlumen.app.app

import com.projectlumen.app.core.database.entities.AppSettingsEntity
import com.projectlumen.app.core.database.entities.DailyGoalEntity
import com.projectlumen.app.core.database.entities.RuntimeStateEntity
import com.projectlumen.app.core.crash.CrashReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

internal class ProjectLumenStateStore(
    repositories: ProjectLumenRepositories,
    scope: CoroutineScope,
    now: StateFlow<Long>,
    private val recordCrashReport: (Throwable) -> CrashReport,
) {
    private val settingsPreview = MutableStateFlow<AppSettingsEntity?>(null)
    private val crashReport = MutableStateFlow<CrashReport?>(null)
    private val settingsState = combine(
        repositories.settings.observe().catch { throwable ->
            recordCrash(throwable)
            emit(null)
        },
        settingsPreview,
    ) { persisted, preview ->
        SettingsSnapshot(
            settings = resolveSettings(persisted, preview),
            persistedReady = persisted != null,
        )
    }

    private val baseDataState = combine(
        settingsState,
        repositories.runtime.observe().catch { throwable ->
            recordCrash(throwable)
            emit(null)
        },
        repositories.statistics.observeEyeStats().catch { throwable ->
            recordCrash(throwable)
            emit(emptyList())
        },
        repositories.statistics.observePomodoroStats().catch { throwable ->
            recordCrash(throwable)
            emit(emptyList())
        },
        repositories.tipTemplates.observeAll().catch { throwable ->
            recordCrash(throwable)
            emit(emptyList())
        },
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
        repositories.dailyGoals.observe().catch { throwable ->
            recordCrash(throwable)
            emit(null)
        },
        repositories.entitlements.observeAll().catch { throwable ->
            recordCrash(throwable)
            emit(emptyList())
        },
        repositories.reminderPlans.observeActive().catch { throwable ->
            recordCrash(throwable)
            emit(emptyList())
        },
    ) { state, dailyGoal, entitlements, reminderPlans ->
        state.copy(
            dailyGoal = dailyGoal ?: DailyGoalEntity(),
            entitlements = entitlements,
            reminderPlans = reminderPlans,
            isReady = state.isReady && dailyGoal != null,
        )
    }

    val uiState = combine(dataState, now, crashReport) { state, nowMillis, report ->
        state.copy(nowMillis = nowMillis, crashReport = report)
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProjectLumenUiState(),
    )

    fun previewSettings(settings: AppSettingsEntity) {
        settingsPreview.value = settings
    }

    fun recordCrash(throwable: Throwable) {
        crashReport.value = recordCrashReport(throwable)
    }

    fun clearCrashReport() {
        crashReport.value = null
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
