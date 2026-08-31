package com.projectlumen.app.app

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableLongStateOf
import com.projectlumen.app.core.database.entities.AppSettingsEntity
import com.projectlumen.app.core.database.entities.DailyEyeStatsEntity
import com.projectlumen.app.core.database.entities.DailyGoalEntity
import com.projectlumen.app.core.database.entities.DailyPomodoroStatsEntity
import com.projectlumen.app.core.database.entities.EntitlementEntity
import com.projectlumen.app.core.database.entities.ReminderPlanEntity
import com.projectlumen.app.core.database.entities.RuntimeStateEntity
import com.projectlumen.app.core.insights.DeviceInsightsState
import com.projectlumen.app.core.database.entities.TipTemplateEntity

/**
 * Snapshot-backed wall clock: the 1 Hz tick invalidates only the composables that actually read
 * [nowMillis], instead of producing a new [ProjectLumenUiState] every second for the whole tree.
 */
@Stable
class LumenUiClock(initialNowMillis: Long = System.currentTimeMillis()) {
    private val state = mutableLongStateOf(initialNowMillis)

    val nowMillis: Long
        get() = state.longValue

    fun update(nowMillis: Long) {
        state.longValue = nowMillis
    }
}

data class ProjectLumenUiState(
    val settings: AppSettingsEntity = AppSettingsEntity(),
    val runtime: RuntimeStateEntity = RuntimeStateEntity(),
    val eyeStats: List<DailyEyeStatsEntity> = emptyList(),
    val pomodoroStats: List<DailyPomodoroStatsEntity> = emptyList(),
    val templates: List<TipTemplateEntity> = emptyList(),
    val dailyGoal: DailyGoalEntity = DailyGoalEntity(),
    val entitlements: List<EntitlementEntity> = emptyList(),
    val reminderPlans: List<ReminderPlanEntity> = emptyList(),
    val deviceInsights: DeviceInsightsState = DeviceInsightsState(),
    val clock: LumenUiClock = LumenUiClock(),
    val isReady: Boolean = false,
) {
    val nowMillis: Long
        get() = clock.nowMillis
}
