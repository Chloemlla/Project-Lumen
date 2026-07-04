package com.projectlumen.app.app

import com.projectlumen.app.core.database.AppDatabase
import com.projectlumen.app.core.preferences.EyeCarePreferencesDataStore
import com.projectlumen.app.core.repositories.AppNetworkControlRepository
import com.projectlumen.app.core.repositories.DailyGoalsRepository
import com.projectlumen.app.core.repositories.EntitlementRepository
import com.projectlumen.app.core.repositories.FeatureFlagRepository
import com.projectlumen.app.core.repositories.ReminderPlansRepository
import com.projectlumen.app.core.repositories.RuntimeRepository
import com.projectlumen.app.core.repositories.SettingsRepository
import com.projectlumen.app.core.repositories.StatisticsRepository
import com.projectlumen.app.core.repositories.TipTemplateRepository
import com.projectlumen.app.core.security.SecureCredentialStore

internal class ProjectLumenRepositories(
    database: AppDatabase,
    eyeCarePreferences: EyeCarePreferencesDataStore,
    secureCredentials: SecureCredentialStore,
) {
    val settings = SettingsRepository(
        database.appSettingsDao(),
        eyeCarePreferences,
        { secureCredentials.deviceInstallationId() },
    )
    val appNetworkControls = AppNetworkControlRepository(database.appNetworkControlsDao())
    val runtime = RuntimeRepository(database.runtimeStateDao())
    val statistics = StatisticsRepository(
        database.dailyEyeStatsDao(),
        database.dailyPomodoroStatsDao(),
    )
    val tipTemplates = TipTemplateRepository(database.tipTemplatesDao())
    val dailyGoals = DailyGoalsRepository(database.dailyGoalsDao())
    val entitlements = EntitlementRepository(database.entitlementsDao())
    val featureFlags = FeatureFlagRepository(database.featureFlagsDao())
    val reminderPlans = ReminderPlansRepository(database.reminderPlansDao())
}
