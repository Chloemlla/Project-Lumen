package com.projectlumen.app.core.services

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.projectlumen.app.R
import com.projectlumen.app.core.database.AppDatabase
import com.projectlumen.app.core.database.entities.AppSettingsEntity
import com.projectlumen.app.core.database.entities.DailyEyeStatsEntity
import com.projectlumen.app.core.database.entities.DailyGoalEntity
import com.projectlumen.app.core.database.entities.DailyPomodoroStatsEntity
import com.projectlumen.app.core.database.entities.EntitlementEntity
import com.projectlumen.app.core.database.entities.FeatureFlagEntity
import com.projectlumen.app.core.database.entities.ReminderPlanEntity
import com.projectlumen.app.core.database.entities.TipTemplateEntity
import com.projectlumen.app.core.preferences.EyeCarePreferencesDataStore
import com.projectlumen.app.core.repositories.SettingsRepository
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

data class BackupImportSummary(
    val schemaVersion: Int,
    val exportedAt: Long,
    val templateCount: Int,
    val eyeStatDays: Int,
    val pomodoroStatDays: Int,
    val entitlementCount: Int,
    val reminderPlanCount: Int,
)

class DataBackupService(
    private val context: Context,
    private val database: AppDatabase,
    private val eyeCarePreferences: EyeCarePreferencesDataStore? = null,
) {
    suspend fun shareBackup() {
        val file = File(context.cacheDir, "project_lumen_backup.json")
        file.writeText(buildBackupJson().toString(2), Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.backup_export))
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.backup_export))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    suspend fun previewImport(uri: Uri): BackupImportSummary {
        val json = readBackupJson(uri)
        return BackupImportSummary(
            schemaVersion = json.optInt("schemaVersion", 1),
            exportedAt = json.optLong("exportedAt", 0L),
            templateCount = json.optJSONArray("templates")?.length() ?: 0,
            eyeStatDays = json.optJSONArray("dailyEyeStats")?.length() ?: 0,
            pomodoroStatDays = json.optJSONArray("dailyPomodoroStats")?.length() ?: 0,
            entitlementCount = json.optJSONArray("entitlements")?.length() ?: 0,
            reminderPlanCount = json.optJSONArray("reminderPlans")?.length() ?: 0,
        )
    }

    suspend fun importBackup(uri: Uri): BackupImportSummary {
        val json = readBackupJson(uri)
        val summary = previewImport(uri)
        importSettings(json.optJSONObject("settings"))
        importDailyGoal(json.optJSONObject("dailyGoal"))
        importTemplates(json.optJSONArray("templates"))
        importEyeStats(json.optJSONArray("dailyEyeStats"))
        importPomodoroStats(json.optJSONArray("dailyPomodoroStats"))
        importEntitlements(json.optJSONArray("entitlements"))
        importFeatureFlags(json.optJSONArray("featureFlags"))
        importReminderPlans(json.optJSONArray("reminderPlans"))
        return summary
    }

    private suspend fun buildBackupJson(): JSONObject {
        val settings = SettingsRepository(database.appSettingsDao(), eyeCarePreferences).getOrDefault()
        return JSONObject()
            .put("schemaVersion", 1)
            .put("exportedAt", System.currentTimeMillis())
            .put("settings", settings.toJson())
            .put("dailyGoal", (database.dailyGoalsDao().get() ?: DailyGoalEntity()).toJson())
            .put("templates", database.tipTemplatesDao().getAllIncludingDeleted().toJsonArray { it.toJson() })
            .put("dailyEyeStats", database.dailyEyeStatsDao().getAll().toJsonArray { it.toJson() })
            .put("dailyPomodoroStats", database.dailyPomodoroStatsDao().getAll().toJsonArray { it.toJson() })
            .put("entitlements", database.entitlementsDao().getAll().toJsonArray { it.toJson() })
            .put("featureFlags", database.featureFlagsDao().getAll().toJsonArray { it.toJson() })
            .put("reminderPlans", database.reminderPlansDao().getActive().toJsonArray { it.toJson() })
    }

    private fun readBackupJson(uri: Uri): JSONObject {
        val text = context.contentResolver.openInputStream(uri)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?: error("Unable to read backup file.")
        return JSONObject(text)
    }

    private suspend fun importSettings(json: JSONObject?) {
        if (json == null) return
        val current = SettingsRepository(database.appSettingsDao(), eyeCarePreferences).getOrDefault()
        val imported = current.copy(
            languageCode = json.optString("languageCode", current.languageCode),
            themeMode = json.optString("themeMode", current.themeMode),
            useDynamicColors = json.optBoolean("useDynamicColors", current.useDynamicColors),
            planTier = json.optString("planTier", current.planTier),
            entitlementExpiresAt = json.optLong("entitlementExpiresAt", current.entitlementExpiresAt),
            lastEntitlementSyncAt = json.optLong("lastEntitlementSyncAt", current.lastEntitlementSyncAt),
            reminderEnabled = json.optBoolean("reminderEnabled", current.reminderEnabled),
            warnIntervalMinutes = json.optInt("warnIntervalMinutes", current.warnIntervalMinutes),
            restDurationSeconds = json.optInt("restDurationSeconds", current.restDurationSeconds),
            statsEnabled = json.optBoolean("statsEnabled", current.statsEnabled),
            soundEnabled = json.optBoolean("soundEnabled", current.soundEnabled),
            vibrationEnabled = json.optBoolean("vibrationEnabled", current.vibrationEnabled),
            restSoundPath = json.optString("restSoundPath", current.restSoundPath),
            restStartSoundEnabled = json.optBoolean("restStartSoundEnabled", current.restStartSoundEnabled),
            restStartSoundPath = json.optString("restStartSoundPath", current.restStartSoundPath),
            preAlertVolumePercent = json.optInt("preAlertVolumePercent", current.preAlertVolumePercent),
            restStartVolumePercent = json.optInt("restStartVolumePercent", current.restStartVolumePercent),
            restEndVolumePercent = json.optInt("restEndVolumePercent", current.restEndVolumePercent),
            pomodoroWorkStartVolumePercent = json.optInt("pomodoroWorkStartVolumePercent", current.pomodoroWorkStartVolumePercent),
            pomodoroWorkEndVolumePercent = json.optInt("pomodoroWorkEndVolumePercent", current.pomodoroWorkEndVolumePercent),
            preAlertEnabled = json.optBoolean("preAlertEnabled", current.preAlertEnabled),
            preAlertSeconds = json.optInt("preAlertSeconds", current.preAlertSeconds),
            preAlertDefaultAction = json.optString("preAlertDefaultAction", current.preAlertDefaultAction),
            preAlertTitle = json.optString("preAlertTitle", current.preAlertTitle),
            preAlertSubtitle = json.optString("preAlertSubtitle", current.preAlertSubtitle),
            preAlertMessage = json.optString("preAlertMessage", current.preAlertMessage),
            preAlertIconPath = json.optString("preAlertIconPath", current.preAlertIconPath),
            preAlertSoundEnabled = json.optBoolean("preAlertSoundEnabled", current.preAlertSoundEnabled),
            askBeforeBreak = json.optBoolean("askBeforeBreak", current.askBeforeBreak),
            disableSkip = json.optBoolean("disableSkip", current.disableSkip),
            timeoutAutoBreak = json.optBoolean("timeoutAutoBreak", current.timeoutAutoBreak),
            pomodoroEnabled = json.optBoolean("pomodoroEnabled", current.pomodoroEnabled),
            pomodoroWorkMinutes = json.optInt("pomodoroWorkMinutes", current.pomodoroWorkMinutes),
            pomodoroShortBreakMinutes = json.optInt("pomodoroShortBreakMinutes", current.pomodoroShortBreakMinutes),
            pomodoroLongBreakMinutes = json.optInt("pomodoroLongBreakMinutes", current.pomodoroLongBreakMinutes),
            pomodoroInteractiveMode = json.optBoolean("pomodoroInteractiveMode", current.pomodoroInteractiveMode),
            pomodoroWorkStartSoundEnabled = json.optBoolean("pomodoroWorkStartSoundEnabled", current.pomodoroWorkStartSoundEnabled),
            pomodoroWorkEndSoundEnabled = json.optBoolean("pomodoroWorkEndSoundEnabled", current.pomodoroWorkEndSoundEnabled),
            pomodoroWorkStartSoundPath = json.optString("pomodoroWorkStartSoundPath", current.pomodoroWorkStartSoundPath),
            pomodoroWorkEndSoundPath = json.optString("pomodoroWorkEndSoundPath", current.pomodoroWorkEndSoundPath),
            activeTipTemplateId = json.optLong("activeTipTemplateId", current.activeTipTemplateId),
            statsWorkImagePath = json.optString("statsWorkImagePath", current.statsWorkImagePath),
            statsRestImagePath = json.optString("statsRestImagePath", current.statsRestImagePath),
            statsSkipImagePath = json.optString("statsSkipImagePath", current.statsSkipImagePath),
            useAutoDarkWindow = json.optBoolean("useAutoDarkWindow", current.useAutoDarkWindow),
            autoDarkStartMinute = json.optInt("autoDarkStartMinute", current.autoDarkStartMinute),
            autoDarkEndMinute = json.optInt("autoDarkEndMinute", current.autoDarkEndMinute),
            quietHoursEnabled = json.optBoolean("quietHoursEnabled", current.quietHoursEnabled),
            quietStartMinute = json.optInt("quietStartMinute", current.quietStartMinute),
            quietEndMinute = json.optInt("quietEndMinute", current.quietEndMinute),
            quietMode = json.optString("quietMode", current.quietMode),
            notificationEnabled = json.optBoolean("notificationEnabled", current.notificationEnabled),
            keepAliveEnabled = json.optBoolean("keepAliveEnabled", current.keepAliveEnabled),
            proximityMonitoringEnabled = json.optBoolean("proximityMonitoringEnabled", current.proximityMonitoringEnabled),
            proximityBaselineEyeDistancePx = json.optDouble("proximityBaselineEyeDistancePx", current.proximityBaselineEyeDistancePx.toDouble()).toFloat(),
            proximityBaselineFaceWidthPercent = json.optInt("proximityBaselineFaceWidthPercent", current.proximityBaselineFaceWidthPercent),
            proximityDistanceMultiplierPercent = json.optInt("proximityDistanceMultiplierPercent", current.proximityDistanceMultiplierPercent),
            proximityCheckIntervalMinutes = json.optInt("proximityCheckIntervalMinutes", current.proximityCheckIntervalMinutes),
            proximityCaptureSeconds = json.optInt("proximityCaptureSeconds", current.proximityCaptureSeconds),
            proximityFaceThresholdPercent = json.optInt("proximityFaceThresholdPercent", current.proximityFaceThresholdPercent),
            proximityAlertCooldownSeconds = json.optInt("proximityAlertCooldownSeconds", current.proximityAlertCooldownSeconds),
            blinkMonitoringEnabled = json.optBoolean("blinkMonitoringEnabled", current.blinkMonitoringEnabled),
            blinkNoBlinkThresholdSeconds = json.optInt("blinkNoBlinkThresholdSeconds", current.blinkNoBlinkThresholdSeconds),
            blinkAlertCooldownSeconds = json.optInt("blinkAlertCooldownSeconds", current.blinkAlertCooldownSeconds),
            ambientLightMonitoringEnabled = json.optBoolean("ambientLightMonitoringEnabled", current.ambientLightMonitoringEnabled),
            ambientLightLowLuxThreshold = json.optInt("ambientLightLowLuxThreshold", current.ambientLightLowLuxThreshold),
            autoBrightnessEnabled = json.optBoolean("autoBrightnessEnabled", current.autoBrightnessEnabled),
            autoBrightnessMinPercent = json.optInt("autoBrightnessMinPercent", current.autoBrightnessMinPercent),
            autoBrightnessMaxPercent = json.optInt("autoBrightnessMaxPercent", current.autoBrightnessMaxPercent),
            globalOverlayEnabled = json.optBoolean("globalOverlayEnabled", current.globalOverlayEnabled),
            overlayRestDurationSeconds = json.optInt("overlayRestDurationSeconds", current.overlayRestDurationSeconds),
            overlayStrictDistancePercent = json.optInt("overlayStrictDistancePercent", current.overlayStrictDistancePercent),
            shizukuAdvancedModeEnabled = json.optBoolean("shizukuAdvancedModeEnabled", current.shizukuAdvancedModeEnabled),
            shizukuContextAwareSamplingEnabled = json.optBoolean("shizukuContextAwareSamplingEnabled", current.shizukuContextAwareSamplingEnabled),
            shizukuServiceRecoveryEnabled = json.optBoolean("shizukuServiceRecoveryEnabled", current.shizukuServiceRecoveryEnabled),
            autoUpdateCheckEnabled = json.optBoolean("autoUpdateCheckEnabled", current.autoUpdateCheckEnabled),
            updatedAt = System.currentTimeMillis(),
        )
        database.appSettingsDao().upsert(imported)
        eyeCarePreferences?.saveFromSettings(imported)
    }

    private suspend fun importDailyGoal(json: JSONObject?) {
        if (json == null) return
        database.dailyGoalsDao().upsert(
            DailyGoalEntity(
                restBreakGoal = json.optInt("restBreakGoal", 8),
                maxContinuousWorkMinutes = json.optInt("maxContinuousWorkMinutes", 45),
                pomodoroGoal = json.optInt("pomodoroGoal", 4),
                weeklyActiveDaysGoal = json.optInt("weeklyActiveDaysGoal", 5),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun importTemplates(array: JSONArray?) {
        if (array == null) return
        for (index in 0 until array.length()) {
            val json = array.optJSONObject(index) ?: continue
            database.tipTemplatesDao().upsert(json.toTipTemplate())
        }
    }

    private suspend fun importEyeStats(array: JSONArray?) {
        if (array == null) return
        for (index in 0 until array.length()) {
            val imported = array.optJSONObject(index)?.toEyeStats() ?: continue
            val current = database.dailyEyeStatsDao().get(imported.statDate)
            database.dailyEyeStatsDao().upsert(
                if (current == null) {
                    imported
                } else {
                    current.copy(
                        workingSeconds = current.workingSeconds + imported.workingSeconds,
                        restSeconds = current.restSeconds + imported.restSeconds,
                        skipCount = current.skipCount + imported.skipCount,
                        completedBreakCount = current.completedBreakCount + imported.completedBreakCount,
                        preAlertCount = current.preAlertCount + imported.preAlertCount,
                        maxContinuousWorkSeconds = maxOf(current.maxContinuousWorkSeconds, imported.maxContinuousWorkSeconds),
                        proximityWarningCount = current.proximityWarningCount + imported.proximityWarningCount,
                        proximityCloseSeconds = current.proximityCloseSeconds + imported.proximityCloseSeconds,
                        eyeDryWarningCount = current.eyeDryWarningCount + imported.eyeDryWarningCount,
                        lowLightWarningCount = current.lowLightWarningCount + imported.lowLightWarningCount,
                        updatedAt = System.currentTimeMillis(),
                    )
                },
            )
        }
    }

    private suspend fun importPomodoroStats(array: JSONArray?) {
        if (array == null) return
        for (index in 0 until array.length()) {
            val imported = array.optJSONObject(index)?.toPomodoroStats() ?: continue
            val current = database.dailyPomodoroStatsDao().get(imported.statDate)
            database.dailyPomodoroStatsDao().upsert(
                if (current == null) {
                    imported
                } else {
                    current.copy(
                        completedTomatoCount = current.completedTomatoCount + imported.completedTomatoCount,
                        restartCount = current.restartCount + imported.restartCount,
                        completedFocusSessions = current.completedFocusSessions + imported.completedFocusSessions,
                        totalFocusSeconds = current.totalFocusSeconds + imported.totalFocusSeconds,
                        totalBreakSeconds = current.totalBreakSeconds + imported.totalBreakSeconds,
                        updatedAt = System.currentTimeMillis(),
                    )
                },
            )
        }
    }

    private suspend fun importEntitlements(array: JSONArray?) {
        if (array == null) return
        for (index in 0 until array.length()) {
            val entitlement = array.optJSONObject(index)?.toEntitlement() ?: continue
            database.entitlementsDao().upsert(entitlement)
        }
    }

    private suspend fun importFeatureFlags(array: JSONArray?) {
        if (array == null) return
        for (index in 0 until array.length()) {
            val flag = array.optJSONObject(index)?.toFeatureFlag() ?: continue
            database.featureFlagsDao().upsert(flag)
        }
    }

    private suspend fun importReminderPlans(array: JSONArray?) {
        if (array == null) return
        for (index in 0 until array.length()) {
            val plan = array.optJSONObject(index)?.toReminderPlan() ?: continue
            database.reminderPlansDao().upsert(plan)
        }
    }

    private fun AppSettingsEntity.toJson(): JSONObject = JSONObject()
        .put("languageCode", languageCode)
        .put("themeMode", themeMode)
        .put("useDynamicColors", useDynamicColors)
        .put("planTier", planTier)
        .put("entitlementExpiresAt", entitlementExpiresAt)
        .put("lastEntitlementSyncAt", lastEntitlementSyncAt)
        .put("reminderEnabled", reminderEnabled)
        .put("warnIntervalMinutes", warnIntervalMinutes)
        .put("restDurationSeconds", restDurationSeconds)
        .put("statsEnabled", statsEnabled)
        .put("soundEnabled", soundEnabled)
        .put("vibrationEnabled", vibrationEnabled)
        .put("restSoundPath", restSoundPath)
        .put("restStartSoundEnabled", restStartSoundEnabled)
        .put("restStartSoundPath", restStartSoundPath)
        .put("preAlertVolumePercent", preAlertVolumePercent)
        .put("restStartVolumePercent", restStartVolumePercent)
        .put("restEndVolumePercent", restEndVolumePercent)
        .put("pomodoroWorkStartVolumePercent", pomodoroWorkStartVolumePercent)
        .put("pomodoroWorkEndVolumePercent", pomodoroWorkEndVolumePercent)
        .put("preAlertEnabled", preAlertEnabled)
        .put("preAlertSeconds", preAlertSeconds)
        .put("preAlertDefaultAction", preAlertDefaultAction)
        .put("preAlertTitle", preAlertTitle)
        .put("preAlertSubtitle", preAlertSubtitle)
        .put("preAlertMessage", preAlertMessage)
        .put("preAlertIconPath", preAlertIconPath)
        .put("preAlertSoundEnabled", preAlertSoundEnabled)
        .put("askBeforeBreak", askBeforeBreak)
        .put("disableSkip", disableSkip)
        .put("timeoutAutoBreak", timeoutAutoBreak)
        .put("pomodoroEnabled", pomodoroEnabled)
        .put("pomodoroWorkMinutes", pomodoroWorkMinutes)
        .put("pomodoroShortBreakMinutes", pomodoroShortBreakMinutes)
        .put("pomodoroLongBreakMinutes", pomodoroLongBreakMinutes)
        .put("pomodoroInteractiveMode", pomodoroInteractiveMode)
        .put("pomodoroWorkStartSoundEnabled", pomodoroWorkStartSoundEnabled)
        .put("pomodoroWorkEndSoundEnabled", pomodoroWorkEndSoundEnabled)
        .put("pomodoroWorkStartSoundPath", pomodoroWorkStartSoundPath)
        .put("pomodoroWorkEndSoundPath", pomodoroWorkEndSoundPath)
        .put("activeTipTemplateId", activeTipTemplateId)
        .put("statsWorkImagePath", statsWorkImagePath)
        .put("statsRestImagePath", statsRestImagePath)
        .put("statsSkipImagePath", statsSkipImagePath)
        .put("useAutoDarkWindow", useAutoDarkWindow)
        .put("autoDarkStartMinute", autoDarkStartMinute)
        .put("autoDarkEndMinute", autoDarkEndMinute)
        .put("quietHoursEnabled", quietHoursEnabled)
        .put("quietStartMinute", quietStartMinute)
        .put("quietEndMinute", quietEndMinute)
        .put("quietMode", quietMode)
        .put("notificationEnabled", notificationEnabled)
        .put("keepAliveEnabled", keepAliveEnabled)
        .put("proximityMonitoringEnabled", proximityMonitoringEnabled)
        .put("proximityBaselineEyeDistancePx", proximityBaselineEyeDistancePx)
        .put("proximityBaselineFaceWidthPercent", proximityBaselineFaceWidthPercent)
        .put("proximityDistanceMultiplierPercent", proximityDistanceMultiplierPercent)
        .put("proximityCheckIntervalMinutes", proximityCheckIntervalMinutes)
        .put("proximityCaptureSeconds", proximityCaptureSeconds)
        .put("proximityFaceThresholdPercent", proximityFaceThresholdPercent)
        .put("proximityAlertCooldownSeconds", proximityAlertCooldownSeconds)
        .put("blinkMonitoringEnabled", blinkMonitoringEnabled)
        .put("blinkNoBlinkThresholdSeconds", blinkNoBlinkThresholdSeconds)
        .put("blinkAlertCooldownSeconds", blinkAlertCooldownSeconds)
        .put("ambientLightMonitoringEnabled", ambientLightMonitoringEnabled)
        .put("ambientLightLowLuxThreshold", ambientLightLowLuxThreshold)
        .put("autoBrightnessEnabled", autoBrightnessEnabled)
        .put("autoBrightnessMinPercent", autoBrightnessMinPercent)
        .put("autoBrightnessMaxPercent", autoBrightnessMaxPercent)
        .put("globalOverlayEnabled", globalOverlayEnabled)
        .put("overlayRestDurationSeconds", overlayRestDurationSeconds)
        .put("overlayStrictDistancePercent", overlayStrictDistancePercent)
        .put("shizukuAdvancedModeEnabled", shizukuAdvancedModeEnabled)
        .put("shizukuContextAwareSamplingEnabled", shizukuContextAwareSamplingEnabled)
        .put("shizukuServiceRecoveryEnabled", shizukuServiceRecoveryEnabled)
        .put("autoUpdateCheckEnabled", autoUpdateCheckEnabled)

    private fun DailyGoalEntity.toJson(): JSONObject = JSONObject()
        .put("restBreakGoal", restBreakGoal)
        .put("maxContinuousWorkMinutes", maxContinuousWorkMinutes)
        .put("pomodoroGoal", pomodoroGoal)
        .put("weeklyActiveDaysGoal", weeklyActiveDaysGoal)

    private fun TipTemplateEntity.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("isBuiltin", isBuiltin)
        .put("backgroundType", backgroundType)
        .put("backgroundValue", backgroundValue)
        .put("primaryColor", primaryColor)
        .put("titleText", titleText)
        .put("subtitleText", subtitleText)
        .put("imagePath", imagePath)
        .put("showSkipButton", showSkipButton)
        .put("layoutJson", layoutJson)
        .put("isPremium", isPremium)
        .put("remoteId", remoteId)
        .put("sortOrder", sortOrder)
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)
        .put("deletedAt", deletedAt)

    private fun DailyEyeStatsEntity.toJson(): JSONObject = JSONObject()
        .put("statDate", statDate)
        .put("workingSeconds", workingSeconds)
        .put("restSeconds", restSeconds)
        .put("skipCount", skipCount)
        .put("completedBreakCount", completedBreakCount)
        .put("preAlertCount", preAlertCount)
        .put("maxContinuousWorkSeconds", maxContinuousWorkSeconds)
        .put("proximityWarningCount", proximityWarningCount)
        .put("proximityCloseSeconds", proximityCloseSeconds)
        .put("eyeDryWarningCount", eyeDryWarningCount)
        .put("lowLightWarningCount", lowLightWarningCount)
        .put("updatedAt", updatedAt)

    private fun DailyPomodoroStatsEntity.toJson(): JSONObject = JSONObject()
        .put("statDate", statDate)
        .put("completedTomatoCount", completedTomatoCount)
        .put("restartCount", restartCount)
        .put("completedFocusSessions", completedFocusSessions)
        .put("totalFocusSeconds", totalFocusSeconds)
        .put("totalBreakSeconds", totalBreakSeconds)
        .put("updatedAt", updatedAt)

    private fun EntitlementEntity.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("source", source)
        .put("productId", productId)
        .put("purchaseToken", purchaseToken)
        .put("tier", tier)
        .put("status", status)
        .put("purchasedAt", purchasedAt)
        .put("expiresAt", expiresAt)
        .put("lastVerifiedAt", lastVerifiedAt)
        .put("rawPayloadJson", rawPayloadJson)

    private fun FeatureFlagEntity.toJson(): JSONObject = JSONObject()
        .put("key", key)
        .put("enabled", enabled)
        .put("payloadJson", payloadJson)
        .put("updatedAt", updatedAt)

    private fun ReminderPlanEntity.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("enabled", enabled)
        .put("warnIntervalMinutes", warnIntervalMinutes)
        .put("restDurationSeconds", restDurationSeconds)
        .put("quietHoursEnabled", quietHoursEnabled)
        .put("quietStartMinute", quietStartMinute)
        .put("quietEndMinute", quietEndMinute)
        .put("quietMode", quietMode)
        .put("sortOrder", sortOrder)
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)
        .put("deletedAt", deletedAt)

    private fun JSONObject.toTipTemplate(): TipTemplateEntity = TipTemplateEntity(
        id = optLong("id", 0L),
        name = optString("name", "Imported template"),
        isBuiltin = optBoolean("isBuiltin", false),
        backgroundType = optString("backgroundType", "SOLID"),
        backgroundValue = optString("backgroundValue", "#D4F2F0"),
        primaryColor = optString("primaryColor", "#246B73"),
        titleText = optString("titleText", "Time to rest"),
        subtitleText = optString("subtitleText", "Look away from the screen and relax your eyes."),
        imagePath = optString("imagePath", ""),
        showSkipButton = optBoolean("showSkipButton", true),
        layoutJson = optString("layoutJson", "{}"),
        isPremium = optBoolean("isPremium", false),
        remoteId = optString("remoteId", ""),
        sortOrder = optInt("sortOrder", 0),
        createdAt = optLong("createdAt", System.currentTimeMillis()),
        updatedAt = System.currentTimeMillis(),
        deletedAt = optLong("deletedAt", 0L),
    )

    private fun JSONObject.toEyeStats(): DailyEyeStatsEntity = DailyEyeStatsEntity(
        statDate = getString("statDate"),
        workingSeconds = optLong("workingSeconds", 0L),
        restSeconds = optLong("restSeconds", 0L),
        skipCount = optInt("skipCount", 0),
        completedBreakCount = optInt("completedBreakCount", 0),
        preAlertCount = optInt("preAlertCount", 0),
        maxContinuousWorkSeconds = optLong("maxContinuousWorkSeconds", 0L),
        proximityWarningCount = optInt("proximityWarningCount", 0),
        proximityCloseSeconds = optLong("proximityCloseSeconds", 0L),
        eyeDryWarningCount = optInt("eyeDryWarningCount", 0),
        lowLightWarningCount = optInt("lowLightWarningCount", 0),
        updatedAt = System.currentTimeMillis(),
    )

    private fun JSONObject.toPomodoroStats(): DailyPomodoroStatsEntity = DailyPomodoroStatsEntity(
        statDate = getString("statDate"),
        completedTomatoCount = optInt("completedTomatoCount", 0),
        restartCount = optInt("restartCount", 0),
        completedFocusSessions = optInt("completedFocusSessions", 0),
        totalFocusSeconds = optLong("totalFocusSeconds", 0L),
        totalBreakSeconds = optLong("totalBreakSeconds", 0L),
        updatedAt = System.currentTimeMillis(),
    )

    private fun JSONObject.toEntitlement(): EntitlementEntity = EntitlementEntity(
        id = optLong("id", 0L),
        source = optString("source", "manual_license"),
        productId = optString("productId", ""),
        purchaseToken = optString("purchaseToken", ""),
        tier = optString("tier", "FREE"),
        status = optString("status", "pending"),
        purchasedAt = optLong("purchasedAt", System.currentTimeMillis()),
        expiresAt = optLong("expiresAt", 0L),
        lastVerifiedAt = optLong("lastVerifiedAt", 0L),
        rawPayloadJson = optString("rawPayloadJson", ""),
    )

    private fun JSONObject.toFeatureFlag(): FeatureFlagEntity = FeatureFlagEntity(
        key = getString("key"),
        enabled = optBoolean("enabled", false),
        payloadJson = optString("payloadJson", ""),
        updatedAt = optLong("updatedAt", System.currentTimeMillis()),
    )

    private fun JSONObject.toReminderPlan(): ReminderPlanEntity = ReminderPlanEntity(
        id = optLong("id", 0L),
        name = optString("name", "Imported plan"),
        enabled = optBoolean("enabled", true),
        warnIntervalMinutes = optInt("warnIntervalMinutes", 20),
        restDurationSeconds = optInt("restDurationSeconds", 20),
        quietHoursEnabled = optBoolean("quietHoursEnabled", false),
        quietStartMinute = optInt("quietStartMinute", 1320),
        quietEndMinute = optInt("quietEndMinute", 420),
        quietMode = optString("quietMode", "PAUSE_TIMER"),
        sortOrder = optInt("sortOrder", 0),
        createdAt = optLong("createdAt", System.currentTimeMillis()),
        updatedAt = System.currentTimeMillis(),
        deletedAt = optLong("deletedAt", 0L),
    )

    private inline fun <T> List<T>.toJsonArray(transform: (T) -> JSONObject): JSONArray {
        val array = JSONArray()
        forEach { array.put(transform(it)) }
        return array
    }
}
