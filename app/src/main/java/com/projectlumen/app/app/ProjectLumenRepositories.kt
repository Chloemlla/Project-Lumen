package com.projectlumen.app.app

import com.projectlumen.app.core.database.AppDatabase
import com.projectlumen.app.core.preferences.EyeCarePreferencesDataStore
import com.projectlumen.app.core.repositories.DailyGoalsRepository
import com.projectlumen.app.core.repositories.EntitlementRepository
import com.projectlumen.app.core.repositories.ReminderPlansRepository
import com.projectlumen.app.core.repositories.RuntimeRepository
import com.projectlumen.app.core.repositories.SettingsRepository
import com.projectlumen.app.core.repositories.StatisticsRepository
import com.projectlumen.app.core.repositories.TipTemplateRepository

internal class ProjectLumenRepositories(
    database: AppDatabase,
    eyeCarePreferences: EyeCarePreferencesDataStore,
) {
    val settings = SettingsRepository(database.appSettingsDao(), eyeCarePreferences)
    val runtime = RuntimeRepository(database.runtimeStateDao())
    val statistics = StatisticsRepository(
        database.dailyEyeStatsDao(),
        database.dailyPomodoroStatsDao(),
    )
    val tipTemplates = TipTemplateRepository(database.tipTemplatesDao())
    val dailyGoals = DailyGoalsRepository(database.dailyGoalsDao())
    val entitlements = EntitlementRepository(database.entitlementsDao())
    val reminderPlans = ReminderPlansRepository(database.reminderPlansDao())
}
