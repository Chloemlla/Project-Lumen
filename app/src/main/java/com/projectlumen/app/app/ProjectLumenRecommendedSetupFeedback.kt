package com.projectlumen.app.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.projectlumen.app.R
import com.projectlumen.app.core.database.entities.AppSettingsEntity
import com.projectlumen.app.core.database.entities.DailyGoalEntity

internal data class RecommendedEyeCareApplyFeedback(
    val applyCount: Int,
    val onApply: () -> Unit,
)

private data class RecommendedEyeCareSetupSnapshot(
    val settingsMatchCount: Int,
    val totalSettings: Int,
    val goalMatchCount: Int,
    val totalGoals: Int,
    val activeProtectionCount: Int,
    val followUpCount: Int,
    val needsDistanceCalibration: Boolean,
    val needsShizukuAuthorization: Boolean,
) {
    val profileApplied: Boolean
        get() = settingsMatchCount == totalSettings && goalMatchCount == totalGoals
}

@Composable
internal fun rememberRecommendedEyeCareApplyFeedback(
    onApplyRecommended: () -> Unit,
): RecommendedEyeCareApplyFeedback {
    var applyCount by rememberSaveable { mutableIntStateOf(0) }
    return RecommendedEyeCareApplyFeedback(
        applyCount = applyCount,
        onApply = {
            applyCount += 1
            onApplyRecommended()
        },
    )
}

@Composable
internal fun RecommendedEyeCareSetupFeedback(
    uiState: ProjectLumenUiState,
    permissionRequirements: PermissionRequirements,
    shizukuReady: Boolean,
    applyFeedbackCount: Int,
    modifier: Modifier = Modifier,
) {
    val snapshot = recommendedEyeCareSetupSnapshot(
        uiState = uiState,
        permissionRequirements = permissionRequirements,
        shizukuReady = shizukuReady,
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(LumenCardShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, LumenCardShape)
            .animateContentSize(animationSpec = spring(stiffness = 420f, dampingRatio = 0.82f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusLine(Icons.Outlined.Settings, recommendedSetupStatusText(snapshot))
        AnimatedVisibility(
            visible = applyFeedbackCount > 0,
            enter = fadeIn(tween(160)) + slideInVertically(tween(160)) { -it / 8 },
            exit = fadeOut(tween(120)) + slideOutVertically(tween(120)) { -it / 8 },
        ) {
            StatusLine(Icons.Outlined.CheckCircle, stringResource(R.string.recommended_setup_last_action))
        }
        StatusLine(Icons.Outlined.Info, stringResource(R.string.recommended_setup_button_action))
        RecommendedSetupFollowUpLine(snapshot)
    }
}

@Composable
internal fun RecommendedEyeCareSetupActionPreview() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusLine(Icons.Outlined.Info, stringResource(R.string.recommended_setup_onboarding_status))
        StatusLine(Icons.Outlined.Settings, stringResource(R.string.recommended_setup_button_action))
    }
}

internal fun recommendedEyeCareSettings(current: AppSettingsEntity): AppSettingsEntity {
    return current.copy(
        reminderEnabled = true,
        warnIntervalMinutes = 20,
        restDurationSeconds = 20,
        statsEnabled = true,
        notificationEnabled = true,
        keepAliveEnabled = true,
        preAlertEnabled = true,
        preAlertSeconds = 60,
        askBeforeBreak = true,
        disableSkip = false,
        quietHoursEnabled = false,
        proximityMonitoringEnabled = true,
        proximityCheckIntervalMinutes = 5,
        proximityCaptureSeconds = 2,
        proximityDistanceMultiplierPercent = 130,
        proximityAlertCooldownSeconds = 120,
        blinkMonitoringEnabled = true,
        blinkNoBlinkThresholdSeconds = 12,
        blinkAlertCooldownSeconds = 90,
        ambientLightMonitoringEnabled = true,
        ambientLightLowLuxThreshold = 10,
        autoBrightnessEnabled = false,
        globalOverlayEnabled = true,
        overlayRestDurationSeconds = 20,
        overlayStrictDistancePercent = 165,
    )
}

internal fun recommendedEyeCareDailyGoal(current: DailyGoalEntity): DailyGoalEntity {
    return current.copy(
        restBreakGoal = 8,
        maxContinuousWorkMinutes = 45,
        pomodoroGoal = 4,
        weeklyActiveDaysGoal = 5,
    )
}

@Composable
private fun recommendedSetupStatusText(snapshot: RecommendedEyeCareSetupSnapshot): String {
    val matchedCount = snapshot.settingsMatchCount + snapshot.goalMatchCount
    val totalCount = snapshot.totalSettings + snapshot.totalGoals
    return when {
        snapshot.profileApplied && snapshot.followUpCount == 0 -> {
            stringResource(
                R.string.recommended_setup_status_complete,
                snapshot.activeProtectionCount,
            )
        }
        snapshot.profileApplied -> {
            stringResource(
                R.string.recommended_setup_status_follow_up,
                snapshot.activeProtectionCount,
                snapshot.followUpCount,
            )
        }
        else -> {
            stringResource(
                R.string.recommended_setup_status_custom,
                matchedCount,
                totalCount,
                snapshot.activeProtectionCount,
            )
        }
    }
}

@Composable
private fun RecommendedSetupFollowUpLine(snapshot: RecommendedEyeCareSetupSnapshot) {
    when {
        snapshot.followUpCount == 0 -> {
            StatusLine(Icons.Outlined.CheckCircle, stringResource(R.string.recommended_setup_followup_none))
        }
        snapshot.needsDistanceCalibration -> {
            StatusLine(Icons.Outlined.WarningAmber, stringResource(R.string.recommended_setup_followup_calibration))
        }
        snapshot.needsShizukuAuthorization -> {
            StatusLine(Icons.Outlined.WarningAmber, stringResource(R.string.recommended_setup_followup_shizuku))
        }
        else -> {
            StatusLine(
                Icons.Outlined.WarningAmber,
                stringResource(R.string.recommended_setup_followup_items, snapshot.followUpCount),
            )
        }
    }
}

private fun recommendedEyeCareSetupSnapshot(
    uiState: ProjectLumenUiState,
    permissionRequirements: PermissionRequirements,
    shizukuReady: Boolean,
): RecommendedEyeCareSetupSnapshot {
    val settings = uiState.settings
    val dailyGoal = uiState.dailyGoal
    val settingMatches = recommendedEyeCareSettingMatches(settings)
    val goalMatches = recommendedEyeCareGoalMatches(dailyGoal)
    val needsDistanceCalibration = settings.proximityMonitoringEnabled &&
        settings.proximityBaselineEyeDistancePx <= 0f &&
        settings.proximityBaselineFaceWidthPercent <= 0
    val needsShizukuAuthorization = settings.shizukuAdvancedModeEnabled && !shizukuReady
    val followUpCount = listOf(
        permissionRequirements.notification && settings.notificationEnabled,
        permissionRequirements.exactAlarm && settings.notificationEnabled,
        permissionRequirements.fullScreenIntent && settings.notificationEnabled,
        permissionRequirements.camera && (settings.proximityMonitoringEnabled || settings.blinkMonitoringEnabled),
        permissionRequirements.overlay && settings.globalOverlayEnabled,
        permissionRequirements.writeSettings && settings.autoBrightnessEnabled &&
            !(settings.shizukuAdvancedModeEnabled && settings.shizukuNativeEyeProtectionEnabled),
        needsShizukuAuthorization,
        needsDistanceCalibration,
    ).count { it }

    return RecommendedEyeCareSetupSnapshot(
        settingsMatchCount = settingMatches.count { it },
        totalSettings = settingMatches.size,
        goalMatchCount = goalMatches.count { it },
        totalGoals = goalMatches.size,
        activeProtectionCount = recommendedEyeCareActiveProtectionCount(settings),
        followUpCount = followUpCount,
        needsDistanceCalibration = needsDistanceCalibration,
        needsShizukuAuthorization = needsShizukuAuthorization,
    )
}

private fun recommendedEyeCareSettingMatches(settings: AppSettingsEntity): List<Boolean> {
    val recommended = recommendedEyeCareSettings(settings)
    return listOf(
        settings.reminderEnabled == recommended.reminderEnabled,
        settings.warnIntervalMinutes == recommended.warnIntervalMinutes,
        settings.restDurationSeconds == recommended.restDurationSeconds,
        settings.statsEnabled == recommended.statsEnabled,
        settings.notificationEnabled == recommended.notificationEnabled,
        settings.keepAliveEnabled == recommended.keepAliveEnabled,
        settings.preAlertEnabled == recommended.preAlertEnabled,
        settings.preAlertSeconds == recommended.preAlertSeconds,
        settings.askBeforeBreak == recommended.askBeforeBreak,
        settings.disableSkip == recommended.disableSkip,
        settings.quietHoursEnabled == recommended.quietHoursEnabled,
        settings.proximityMonitoringEnabled == recommended.proximityMonitoringEnabled,
        settings.proximityCheckIntervalMinutes == recommended.proximityCheckIntervalMinutes,
        settings.proximityCaptureSeconds == recommended.proximityCaptureSeconds,
        settings.proximityDistanceMultiplierPercent == recommended.proximityDistanceMultiplierPercent,
        settings.proximityAlertCooldownSeconds == recommended.proximityAlertCooldownSeconds,
        settings.blinkMonitoringEnabled == recommended.blinkMonitoringEnabled,
        settings.blinkNoBlinkThresholdSeconds == recommended.blinkNoBlinkThresholdSeconds,
        settings.blinkAlertCooldownSeconds == recommended.blinkAlertCooldownSeconds,
        settings.ambientLightMonitoringEnabled == recommended.ambientLightMonitoringEnabled,
        settings.ambientLightLowLuxThreshold == recommended.ambientLightLowLuxThreshold,
        settings.autoBrightnessEnabled == recommended.autoBrightnessEnabled,
        settings.globalOverlayEnabled == recommended.globalOverlayEnabled,
        settings.overlayRestDurationSeconds == recommended.overlayRestDurationSeconds,
        settings.overlayStrictDistancePercent == recommended.overlayStrictDistancePercent,
    )
}

private fun recommendedEyeCareGoalMatches(dailyGoal: DailyGoalEntity): List<Boolean> {
    val recommended = recommendedEyeCareDailyGoal(dailyGoal)
    return listOf(
        dailyGoal.restBreakGoal == recommended.restBreakGoal,
        dailyGoal.maxContinuousWorkMinutes == recommended.maxContinuousWorkMinutes,
        dailyGoal.pomodoroGoal == recommended.pomodoroGoal,
        dailyGoal.weeklyActiveDaysGoal == recommended.weeklyActiveDaysGoal,
    )
}

private fun recommendedEyeCareActiveProtectionCount(settings: AppSettingsEntity): Int {
    return listOf(
        settings.reminderEnabled,
        settings.notificationEnabled,
        settings.keepAliveEnabled,
        settings.preAlertEnabled,
        settings.proximityMonitoringEnabled,
        settings.blinkMonitoringEnabled,
        settings.ambientLightMonitoringEnabled,
        settings.globalOverlayEnabled,
        settings.statsEnabled,
    ).count { it }
}
