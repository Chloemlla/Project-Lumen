package com.projectlumen.app.app

import com.projectlumen.app.core.database.entities.AppSettingsEntity
import com.projectlumen.app.core.database.entities.DailyGoalEntity
import com.projectlumen.app.core.database.entities.RuntimeStateEntity
import com.projectlumen.app.core.database.entities.ScheduleOccurrenceEntity
import com.projectlumen.app.core.insights.DeviceInsightsState
import com.projectlumen.app.core.quarkkeeper.QuarkKeeperStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

internal class ProjectLumenStateStore(
    repositories: ProjectLumenRepositories,
    scope: CoroutineScope,
    private val clock: LumenUiClock,
    private val recordHandledFailure: (Throwable) -> Unit,
) {
    private val settingsPreview = MutableStateFlow<AppSettingsEntity?>(null)
    private val settingsState = combine(
        repositories.settings.observe().catch { throwable ->
            recordHandledFailure(throwable)
            emit(null)
        }.distinctUntilChanged(),
        settingsPreview,
    ) { persisted, preview ->
        SettingsSnapshot(
            settings = resolveSettings(persisted, preview),
            persistedReady = persisted != null,
        )
    }.distinctUntilChanged()

    private val baseDataState = combine(
        settingsState,
        repositories.runtime.observe().catch { throwable ->
            recordHandledFailure(throwable)
            emit(null)
        }.distinctUntilChanged(),
        repositories.statistics.observeEyeStats().catch { throwable ->
            recordHandledFailure(throwable)
            emit(emptyList())
        }.distinctUntilChanged(),
        repositories.statistics.observePomodoroStats().catch { throwable ->
            recordHandledFailure(throwable)
            emit(emptyList())
        }.distinctUntilChanged(),
        repositories.tipTemplates.observeAll().catch { throwable ->
            recordHandledFailure(throwable)
            emit(emptyList())
        }.distinctUntilChanged(),
    ) { settingsState, runtime, eyeStats, pomodoroStats, templates ->
        ProjectLumenUiState(
            settings = settingsState.settings ?: AppSettingsEntity(),
            runtime = runtime ?: RuntimeStateEntity(),
            eyeStats = eyeStats,
            pomodoroStats = pomodoroStats,
            templates = templates,
            clock = clock,
            isReady = settingsState.persistedReady && runtime != null,
        )
    }

    private val deviceAndScheduleState = combine(
        repositories.deviceInsights.observe(),
        repositories.schedule.observeAll().catch { throwable ->
            recordHandledFailure(throwable)
            emit(emptyList())
        }.distinctUntilChanged(),
    ) { deviceInsights, scheduleTasks ->
        DeviceAndScheduleSnapshot(
            deviceInsights = deviceInsights,
            scheduleTasks = scheduleTasks,
        )
    }

    private val dataState = combine(
        baseDataState,
        repositories.dailyGoals.observe().catch { throwable ->
            recordHandledFailure(throwable)
            emit(null)
        }.distinctUntilChanged(),
        repositories.entitlements.observeAll().catch { throwable ->
            recordHandledFailure(throwable)
            emit(emptyList())
        }.distinctUntilChanged(),
        repositories.reminderPlans.observeActive().catch { throwable ->
            recordHandledFailure(throwable)
            emit(emptyList())
        }.distinctUntilChanged(),
        deviceAndScheduleState,
    ) { state, dailyGoal, entitlements, reminderPlans, deviceAndSchedule ->
        state.copy(
            dailyGoal = dailyGoal ?: DailyGoalEntity(),
            entitlements = entitlements,
            reminderPlans = reminderPlans,
            deviceInsights = deviceAndSchedule.deviceInsights,
            scheduleTasks = deviceAndSchedule.scheduleTasks,
            isReady = state.isReady && dailyGoal != null,
        )
    }.combine(QuarkKeeperStore.state) { state, quarkKeeper ->
        // Chained rather than added to the combine above: that one is already at the largest typed
        // overload, and the guard's flow has no failure mode to catch — the store answers with its
        // last good snapshot, or with defaults when the file cannot be read at all.
        state.copy(quarkKeeper = quarkKeeper)
    }

    val uiState = dataState.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProjectLumenUiState(clock = clock),
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

    private data class DeviceAndScheduleSnapshot(
        val deviceInsights: DeviceInsightsState,
        val scheduleTasks: List<ScheduleOccurrenceEntity>,
    )
}
