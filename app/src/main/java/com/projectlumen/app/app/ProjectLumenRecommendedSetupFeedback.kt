package com.projectlumen.app.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    val profileApplied: Boolean,
)

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
    val snapshot = remember(uiState.settings, uiState.dailyGoal, permissionRequirements, shizukuReady) {
        recommendedEyeCareSetupSnapshot(
            settings = uiState.settings,
            dailyGoal = uiState.dailyGoal,
            permissionRequirements = permissionRequirements,
            shizukuReady = shizukuReady,
        )
    }
    // Every child here is a StatusLine, which already paints the nested surface. A wrapper
    // surface of the same role would only add an invisible box and a stray inset.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = 420f, dampingRatio = 0.82f)),
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
    settings: AppSettingsEntity,
    dailyGoal: DailyGoalEntity,
    permissionRequirements: PermissionRequirements,
    shizukuReady: Boolean,
): RecommendedEyeCareSetupSnapshot {
    val recommendedSettings = recommendedEyeCareSettings(settings)
    val recommendedGoal = recommendedEyeCareDailyGoal(dailyGoal)
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
        settingsMatchCount = RecommendedSettingFields.count { read -> read(settings) == read(recommendedSettings) },
        totalSettings = RecommendedSettingFields.size,
        goalMatchCount = RecommendedGoalFields.count { read -> read(dailyGoal) == read(recommendedGoal) },
        totalGoals = RecommendedGoalFields.size,
        activeProtectionCount = recommendedEyeCareActiveProtectionCount(settings),
        followUpCount = followUpCount,
        needsDistanceCalibration = needsDistanceCalibration,
        needsShizukuAuthorization = needsShizukuAuthorization,
        // Whole-entity equality, not the field counts: the recommended profile is a `copy` of the
        // current row, so any untouched field is equal by construction and a newly recommended field
        // cannot go unchecked just because the reader list below was not updated with it.
        profileApplied = settings == recommendedSettings && dailyGoal == recommendedGoal,
    )
}

/** Drives the user-visible "matched x/y" count only; [recommendedEyeCareSettings] owns the values. */
private val RecommendedSettingFields: List<(AppSettingsEntity) -> Any?> = listOf(
    AppSettingsEntity::reminderEnabled,
    AppSettingsEntity::warnIntervalMinutes,
    AppSettingsEntity::restDurationSeconds,
    AppSettingsEntity::statsEnabled,
    AppSettingsEntity::notificationEnabled,
    AppSettingsEntity::keepAliveEnabled,
    AppSettingsEntity::preAlertEnabled,
    AppSettingsEntity::preAlertSeconds,
    AppSettingsEntity::askBeforeBreak,
    AppSettingsEntity::disableSkip,
    AppSettingsEntity::quietHoursEnabled,
    AppSettingsEntity::proximityMonitoringEnabled,
    AppSettingsEntity::proximityCheckIntervalMinutes,
    AppSettingsEntity::proximityCaptureSeconds,
    AppSettingsEntity::proximityDistanceMultiplierPercent,
    AppSettingsEntity::proximityAlertCooldownSeconds,
    AppSettingsEntity::blinkMonitoringEnabled,
    AppSettingsEntity::blinkNoBlinkThresholdSeconds,
    AppSettingsEntity::blinkAlertCooldownSeconds,
    AppSettingsEntity::ambientLightMonitoringEnabled,
    AppSettingsEntity::ambientLightLowLuxThreshold,
    AppSettingsEntity::autoBrightnessEnabled,
    AppSettingsEntity::globalOverlayEnabled,
    AppSettingsEntity::overlayRestDurationSeconds,
    AppSettingsEntity::overlayStrictDistancePercent,
)

private val RecommendedGoalFields: List<(DailyGoalEntity) -> Any?> = listOf(
    DailyGoalEntity::restBreakGoal,
    DailyGoalEntity::maxContinuousWorkMinutes,
    DailyGoalEntity::pomodoroGoal,
    DailyGoalEntity::weeklyActiveDaysGoal,
)

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
