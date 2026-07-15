package com.projectlumen.app.app

import com.projectlumen.app.core.database.entities.AppSettingsEntity
import com.projectlumen.app.core.database.entities.DailyEyeStatsEntity
import com.projectlumen.app.core.database.entities.DailyGoalEntity
import com.projectlumen.app.core.database.entities.DailyPomodoroStatsEntity
import com.projectlumen.app.core.database.entities.EntitlementEntity
import com.projectlumen.app.core.database.entities.ReminderPlanEntity
import com.projectlumen.app.core.database.entities.RuntimeStateEntity
import com.projectlumen.app.core.database.entities.TipTemplateEntity
import com.chloemlla.lumen.crash.CrashReport

data class ProjectLumenUiState(
    val settings: AppSettingsEntity = AppSettingsEntity(),
    val runtime: RuntimeStateEntity = RuntimeStateEntity(),
    val eyeStats: List<DailyEyeStatsEntity> = emptyList(),
    val pomodoroStats: List<DailyPomodoroStatsEntity> = emptyList(),
    val templates: List<TipTemplateEntity> = emptyList(),
    val dailyGoal: DailyGoalEntity = DailyGoalEntity(),
    val entitlements: List<EntitlementEntity> = emptyList(),
    val reminderPlans: List<ReminderPlanEntity> = emptyList(),
    val nowMillis: Long = System.currentTimeMillis(),
    val isReady: Boolean = false,
    val crashReport: CrashReport? = null,
)
