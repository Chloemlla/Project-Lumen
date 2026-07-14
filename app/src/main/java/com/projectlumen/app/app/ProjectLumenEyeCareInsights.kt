package com.projectlumen.app.app

import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.projectlumen.app.R
import com.projectlumen.app.core.database.entities.DailyEyeStatsEntity
import com.projectlumen.app.core.enums.PlanTier
import com.projectlumen.app.core.enums.QuietMode
import kotlin.math.roundToInt

internal data class EyeCareInsightSummary(
    val riskScore: Int,
    @StringRes val riskLabelRes: Int,
    @StringRes val riskMessageRes: Int,
    val completionRate: Int,
    val skipRate: Int,
    val maxContinuousMinutes: Int,
    val averageContinuousMinutes: Int,
    val restMinutes: Int,
    val workMinutes: Int,
    val proximityWarnings: Int,
    val dryEyeWarnings: Int,
    val lowLightWarnings: Int,
    val configurationScore: Int,
    val missingSetupCount: Int,
    val activeProtectionCount: Int,
    val riskReasonRes: List<Int>,
    val actionRes: List<Int>,
)

private data class EyeCareGuideStep(
    val icon: ImageVector,
    @StringRes val titleRes: Int,
    @StringRes val detailRes: Int,
    val complete: Boolean,
)

@Composable
internal fun EyeCareGuidedSetupCard(
    uiState: ProjectLumenUiState,
    permissionRequirements: PermissionRequirements,
    shizukuReady: Boolean,
    canStartReminder: Boolean,
    onApplyRecommended: () -> Unit,
    onResolveNextPermission: () -> Unit,
    onCalibrateDistance: () -> Unit,
    onStartReminder: () -> Unit,
    onExportReport: () -> Unit,
) {
    val summary = rememberEyeCareInsightSummary(uiState, permissionRequirements, shizukuReady)
    val recommendedFeedback = rememberRecommendedEyeCareApplyFeedback(onApplyRecommended)
    val distanceCalibrated = uiState.settings.proximityBaselineEyeDistancePx > 0f ||
        uiState.settings.proximityBaselineFaceWidthPercent > 0
    val missingRuntimePermission = summary.missingSetupCount > 0
    val steps = listOf(
        EyeCareGuideStep(
            icon = Icons.Outlined.CheckCircle,
            titleRes = R.string.eye_care_guide_step_recommended,
            detailRes = R.string.eye_care_guide_step_recommended_detail,
            complete = uiState.settings.reminderEnabled &&
                uiState.settings.statsEnabled &&
                uiState.settings.proximityMonitoringEnabled &&
                uiState.settings.blinkMonitoringEnabled &&
                uiState.settings.ambientLightMonitoringEnabled,
        ),
        EyeCareGuideStep(
            icon = Icons.Outlined.Lock,
            titleRes = R.string.eye_care_guide_step_permissions,
            detailRes = R.string.eye_care_guide_step_permissions_detail,
            complete = !missingRuntimePermission,
        ),
        EyeCareGuideStep(
            icon = Icons.Outlined.PhotoCamera,
            titleRes = R.string.eye_care_guide_step_calibration,
            detailRes = R.string.eye_care_guide_step_calibration_detail,
            complete = !uiState.settings.proximityMonitoringEnabled || distanceCalibrated,
        ),
        EyeCareGuideStep(
            icon = Icons.Outlined.Schedule,
            titleRes = R.string.eye_care_guide_step_first_session,
            detailRes = R.string.eye_care_guide_step_first_session_detail,
            complete = uiState.eyeStats.any { it.workingSeconds > 0L || it.completedBreakCount > 0 },
        ),
        EyeCareGuideStep(
            icon = Icons.Outlined.BarChart,
            titleRes = R.string.eye_care_guide_step_report,
            detailRes = R.string.eye_care_guide_step_report_detail,
            complete = uiState.eyeStats.size >= 3,
        ),
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = 420f, dampingRatio = 0.82f)),
        shape = LumenCardShape,
        colors = lumenCardColors(),
        elevation = lumenCardElevation(),
        border = lumenCardBorder(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(Icons.Outlined.Spa, R.string.eye_care_guide_title)
            Text(
                stringResource(R.string.eye_care_guide_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            steps.forEach { step -> GuideStepLine(step) }
            LumenFlowRow {
                Button(onClick = recommendedFeedback.onApply) {
                    ButtonLabel(Icons.Outlined.CheckCircle, R.string.eye_care_apply_recommended)
                }
                OutlinedButton(onClick = onResolveNextPermission) {
                    ButtonLabel(Icons.Outlined.Lock, R.string.eye_care_guide_fix_next_permission)
                }
                OutlinedButton(onClick = onCalibrateDistance) {
                    ButtonLabel(Icons.Outlined.PhotoCamera, R.string.eye_care_guide_calibrate_now)
                }
                OutlinedButton(onClick = onStartReminder, enabled = canStartReminder) {
                    ButtonLabel(Icons.Outlined.Schedule, R.string.eye_care_guide_start_session)
                }
                OutlinedButton(onClick = onExportReport) {
                    ButtonLabel(Icons.Outlined.FileDownload, R.string.export_pdf_monthly)
                }
            }
            RecommendedEyeCareSetupFeedback(
                uiState = uiState,
                permissionRequirements = permissionRequirements,
                shizukuReady = shizukuReady,
                applyFeedbackCount = recommendedFeedback.applyCount,
            )
        }
    }
}

@Composable
internal fun EyeCareInsightsHomeCard(
    uiState: ProjectLumenUiState,
    permissionRequirements: PermissionRequirements,
    shizukuReady: Boolean,
    onApplyRecommended: () -> Unit,
) {
    val summary = rememberEyeCareInsightSummary(uiState, permissionRequirements, shizukuReady)
    val recommendedFeedback = rememberRecommendedEyeCareApplyFeedback(onApplyRecommended)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = 420f, dampingRatio = 0.82f)),
        shape = LumenCardShape,
        colors = lumenCardColors(),
        elevation = lumenCardElevation(),
        border = lumenCardBorder(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(Icons.Outlined.Spa, R.string.eye_care_insights_title)
            RiskScoreHeader(summary)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallMetric(R.string.eye_care_config_score, stringResource(R.string.percent_value, summary.configurationScore))
                SmallMetric(R.string.eye_care_active_protections, summary.activeProtectionCount.toString())
            }
            InsightReasonList(summary.riskReasonRes.take(3))
            if (summary.missingSetupCount > 0) {
                StatusLine(
                    Icons.Outlined.WarningAmber,
                    stringResource(R.string.eye_care_setup_missing_count, summary.missingSetupCount),
                )
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = recommendedFeedback.onApply,
            ) {
                ButtonLabel(Icons.Outlined.CheckCircle, R.string.eye_care_apply_recommended)
            }
            RecommendedEyeCareSetupFeedback(
                uiState = uiState,
                permissionRequirements = permissionRequirements,
                shizukuReady = shizukuReady,
                applyFeedbackCount = recommendedFeedback.applyCount,
            )
        }
    }
}

@Composable
internal fun EyeCareHealthReportCard(
    uiState: ProjectLumenUiState,
    permissionRequirements: PermissionRequirements,
    shizukuReady: Boolean,
) {
    val summary = rememberEyeCareInsightSummary(uiState, permissionRequirements, shizukuReady)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = 420f, dampingRatio = 0.82f)),
        shape = LumenCardShape,
        colors = lumenCardColors(),
        elevation = lumenCardElevation(),
        border = lumenCardBorder(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(Icons.Outlined.BarChart, R.string.eye_care_health_report)
            RiskScoreHeader(summary)
            MetricRow(R.string.working_time, stringResource(R.string.minutes_value, summary.workMinutes))
            MetricRow(R.string.rest_time, stringResource(R.string.minutes_value, summary.restMinutes))
            MetricRow(R.string.eye_care_rest_completion, stringResource(R.string.percent_value, summary.completionRate))
            MetricRow(R.string.skip_rate, stringResource(R.string.percent_value, summary.skipRate))
            MetricRow(R.string.max_continuous_work_goal, stringResource(R.string.minutes_value, summary.maxContinuousMinutes))
            MetricRow(R.string.average_continuous_work, stringResource(R.string.minutes_value, summary.averageContinuousMinutes))
            MetricRow(R.string.proximity_warnings, summary.proximityWarnings.toString())
            MetricRow(R.string.eye_dry_warnings, summary.dryEyeWarnings.toString())
            MetricRow(R.string.low_light_warnings, summary.lowLightWarnings.toString())
            InsightReasonList(summary.riskReasonRes)
            InsightActionList(summary.actionRes)
        }
    }
}

@Composable
internal fun EyeCareActionPlanCard(
    uiState: ProjectLumenUiState,
    permissionRequirements: PermissionRequirements,
    shizukuReady: Boolean,
    onApplyRecommended: () -> Unit,
    onExportReport: () -> Unit,
) {
    val summary = rememberEyeCareInsightSummary(uiState, permissionRequirements, shizukuReady)
    val recommendedFeedback = rememberRecommendedEyeCareApplyFeedback(onApplyRecommended)
    SettingsSection(
        titleRes = R.string.eye_care_action_plan,
        icon = Icons.Outlined.CheckCircle,
        headerAccessory = {
            Text(
                text = stringResource(summary.riskLabelRes),
                modifier = Modifier
                    .clip(CircleShape)
                    .background(riskBadgeContainerColor(summary.riskScore))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = riskBadgeContentColor(summary.riskScore),
            )
        },
    ) {
        InsightActionList(summary.actionRes)
        LumenFlowRow {
            Button(onClick = recommendedFeedback.onApply) {
                ButtonLabel(Icons.Outlined.CheckCircle, R.string.eye_care_apply_recommended)
            }
            OutlinedButton(onClick = onExportReport) {
                ButtonLabel(Icons.Outlined.FileDownload, R.string.export_pdf_monthly)
            }
        }
        RecommendedEyeCareSetupFeedback(
            uiState = uiState,
            permissionRequirements = permissionRequirements,
            shizukuReady = shizukuReady,
            applyFeedbackCount = recommendedFeedback.applyCount,
        )
    }
}

@Composable
private fun riskBadgeContainerColor(riskScore: Int): Color = when {
    riskScore >= 66 -> MaterialTheme.colorScheme.errorContainer
    riskScore >= 33 -> MaterialTheme.colorScheme.secondaryContainer
    else -> MaterialTheme.colorScheme.primaryContainer
}

@Composable
private fun riskBadgeContentColor(riskScore: Int): Color = when {
    riskScore >= 66 -> MaterialTheme.colorScheme.onErrorContainer
    riskScore >= 33 -> MaterialTheme.colorScheme.onSecondaryContainer
    else -> MaterialTheme.colorScheme.onPrimaryContainer
}

@Composable
internal fun EyeCareSetupAndPrivacyCard(
    uiState: ProjectLumenUiState,
    permissionRequirements: PermissionRequirements,
    shizukuReady: Boolean,
) {
    val summary = rememberEyeCareInsightSummary(
        uiState = uiState,
        permissionRequirements = permissionRequirements,
        shizukuReady = shizukuReady,
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = 420f, dampingRatio = 0.82f)),
        shape = LumenCardShape,
        colors = lumenCardColors(),
        elevation = lumenCardElevation(),
        border = lumenCardBorder(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(Icons.Outlined.Lock, R.string.eye_care_privacy_permissions)
            MetricRow(R.string.eye_care_config_score, stringResource(R.string.percent_value, summary.configurationScore))
            LinearProgressIndicator(
                progress = { summary.configurationScore / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            PermissionTransparencyLine(
                icon = Icons.Outlined.NotificationsActive,
                titleRes = R.string.eye_care_permission_notifications,
                detailRes = R.string.eye_care_permission_notifications_detail,
                satisfied = !permissionRequirements.notification,
            )
            PermissionTransparencyLine(
                icon = Icons.Outlined.Schedule,
                titleRes = R.string.eye_care_permission_exact_alarm,
                detailRes = R.string.eye_care_permission_exact_alarm_detail,
                satisfied = !permissionRequirements.exactAlarm,
            )
            PermissionTransparencyLine(
                icon = Icons.Outlined.NotificationsActive,
                titleRes = R.string.eye_care_permission_full_screen,
                detailRes = R.string.eye_care_permission_full_screen_detail,
                satisfied = !permissionRequirements.fullScreenIntent || !uiState.settings.notificationEnabled,
            )
            PermissionTransparencyLine(
                icon = Icons.Outlined.PhotoCamera,
                titleRes = R.string.eye_care_permission_camera,
                detailRes = R.string.eye_care_permission_camera_detail,
                satisfied = !permissionRequirements.camera || (!uiState.settings.proximityMonitoringEnabled && !uiState.settings.blinkMonitoringEnabled),
            )
            PermissionTransparencyLine(
                icon = Icons.Outlined.Style,
                titleRes = R.string.eye_care_permission_overlay,
                detailRes = R.string.eye_care_permission_overlay_detail,
                satisfied = !permissionRequirements.overlay || !uiState.settings.globalOverlayEnabled,
            )
            PermissionTransparencyLine(
                icon = Icons.Outlined.Style,
                titleRes = R.string.eye_care_permission_write_settings,
                detailRes = R.string.eye_care_permission_write_settings_detail,
                satisfied = !permissionRequirements.writeSettings || !uiState.settings.autoBrightnessEnabled ||
                    (uiState.settings.shizukuAdvancedModeEnabled && uiState.settings.shizukuNativeEyeProtectionEnabled),
            )
            PermissionTransparencyLine(
                icon = Icons.Outlined.Lock,
                titleRes = R.string.eye_care_permission_shizuku,
                detailRes = R.string.eye_care_permission_shizuku_detail,
                satisfied = !uiState.settings.shizukuAdvancedModeEnabled || shizukuReady,
            )
            StatusLine(Icons.Outlined.Info, stringResource(R.string.eye_care_privacy_boundary))
        }
    }
}

@Composable
internal fun EyeCareGrowthCapabilityCard(
    uiState: ProjectLumenUiState,
    remoteState: ProjectLumenRemoteUiState,
    onOpenTemplates: () -> Unit,
    onConfigureReports: () -> Unit,
    onConfigureCloud: () -> Unit,
    onConfigureFamilyMode: () -> Unit,
    onConfigureGuidance: () -> Unit,
    onSyncCloud: () -> Unit,
    onApplyFamilyMode: () -> Unit,
    onApplyGuidance: () -> Unit,
    onExportReport: () -> Unit,
) {
    val proEnabled = planTier(uiState.settings) >= PlanTier.PRO
    val hasPremiumTemplates = uiState.templates.any { it.isPremium }
    val advancedReportsReady = uiState.settings.statsEnabled
    val cloudSyncReady = remoteState.signedIn
    val familyModeReady = isFamilyEyeCareModeActive(uiState)
    val aiGuidanceReady = uiState.settings.statsEnabled && uiState.settings.reminderEnabled
    val activeCapabilities = listOf(
        proEnabled && hasPremiumTemplates,
        advancedReportsReady,
        cloudSyncReady,
        familyModeReady,
        aiGuidanceReady,
    ).count { it }
    SettingsSection(
        titleRes = R.string.eye_care_growth_capabilities,
        icon = Icons.Outlined.Sync,
        initiallyExpanded = false,
        headerAccessory = {
            Text(
                text = "$activeCapabilities/5",
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (activeCapabilities > 0) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (activeCapabilities > 0) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        },
    ) {
        MetricRow(R.string.plan_tier, uiState.settings.planTier.lowercase())
        CapabilityLine(
                icon = Icons.Outlined.Style,
                titleRes = R.string.eye_care_growth_pro_templates,
                active = proEnabled && hasPremiumTemplates,
                inactiveActionRes = R.string.eye_care_capability_configure,
                onConfigure = onOpenTemplates,
            )
            CapabilityLine(
                icon = Icons.Outlined.BarChart,
                titleRes = R.string.eye_care_growth_advanced_reports,
                active = advancedReportsReady,
                inactiveActionRes = R.string.eye_care_capability_enable,
                onConfigure = onConfigureReports,
            )
            CapabilityLine(
                icon = Icons.Outlined.Sync,
                titleRes = R.string.eye_care_growth_cloud_sync,
                active = cloudSyncReady,
                inactiveActionRes = R.string.eye_care_capability_sign_in,
                onConfigure = onConfigureCloud,
            )
            CapabilityLine(
                icon = Icons.Outlined.Lock,
                titleRes = R.string.eye_care_growth_family_mode,
                active = familyModeReady,
                inactiveActionRes = R.string.eye_care_capability_review,
                onConfigure = onConfigureFamilyMode,
            )
            CapabilityLine(
                icon = Icons.Outlined.Info,
                titleRes = R.string.eye_care_growth_ai_guidance,
                active = aiGuidanceReady,
                inactiveActionRes = R.string.eye_care_capability_review,
                onConfigure = onConfigureGuidance,
            )
            LumenFlowRow {
                OutlinedButton(onClick = onOpenTemplates) {
                    ButtonLabel(Icons.Outlined.Style, R.string.nav_templates)
                }
                OutlinedButton(onClick = if (advancedReportsReady) onExportReport else onConfigureReports) {
                    ButtonLabel(
                        Icons.Outlined.BarChart,
                        if (advancedReportsReady) R.string.eye_care_growth_advanced_reports else R.string.eye_care_capability_enable,
                    )
                }
                OutlinedButton(onClick = if (cloudSyncReady) onSyncCloud else onConfigureCloud) {
                    ButtonLabel(
                        Icons.Outlined.Sync,
                        if (cloudSyncReady) R.string.remote_cloud_sync_now else R.string.eye_care_capability_sign_in,
                    )
                }
                Button(onClick = onApplyFamilyMode) {
                    ButtonLabel(Icons.Outlined.Lock, R.string.eye_care_apply_family_profile)
                }
                Button(onClick = onApplyGuidance) {
                    ButtonLabel(Icons.Outlined.Info, R.string.eye_care_apply_local_guidance)
                }
            }
    }
}

internal fun applyRecommendedEyeCareSettings(viewModel: ProjectLumenViewModel) {
    viewModel.updateSettings(::recommendedEyeCareSettings)
    viewModel.updateDailyGoal(::recommendedEyeCareDailyGoal)
}

internal fun applyFamilyEyeCareMode(viewModel: ProjectLumenViewModel) {
    viewModel.updateSettings { current ->
        current.copy(
            reminderEnabled = true,
            warnIntervalMinutes = 15,
            restDurationSeconds = 30,
            statsEnabled = true,
            notificationEnabled = true,
            keepAliveEnabled = true,
            preAlertEnabled = true,
            preAlertSeconds = 90,
            askBeforeBreak = true,
            disableSkip = true,
            timeoutAutoBreak = true,
            quietHoursEnabled = true,
            quietStartMinute = 1260,
            quietEndMinute = 420,
            quietMode = QuietMode.PAUSE_TIMER.name,
            proximityMonitoringEnabled = true,
            proximityCheckIntervalMinutes = 3,
            proximityCaptureSeconds = 2,
            proximityDistanceMultiplierPercent = 145,
            proximityAlertCooldownSeconds = 90,
            blinkMonitoringEnabled = true,
            blinkNoBlinkThresholdSeconds = 8,
            blinkAlertCooldownSeconds = 60,
            ambientLightMonitoringEnabled = true,
            ambientLightLowLuxThreshold = 20,
            autoBrightnessEnabled = false,
            globalOverlayEnabled = true,
            overlayRestDurationSeconds = 30,
            overlayStrictDistancePercent = 175,
        )
    }
    viewModel.updateDailyGoal { current ->
        current.copy(
            restBreakGoal = 10,
            maxContinuousWorkMinutes = 30,
            pomodoroGoal = 3,
            weeklyActiveDaysGoal = 5,
        )
    }
}

internal fun applyPersonalizedEyeCareGuidance(
    viewModel: ProjectLumenViewModel,
    uiState: ProjectLumenUiState,
) {
    val recentEyeStats = uiState.eyeStats.take(14)
    val completedBreaks = recentEyeStats.sumOf { it.completedBreakCount }
    val skips = recentEyeStats.sumOf { it.skipCount }
    val totalBreakDecisions = completedBreaks + skips
    val skipRate = if (totalBreakDecisions > 0) {
        (skips * 100) / totalBreakDecisions
    } else {
        0
    }.coerceIn(0, 100)
    val maxContinuousMinutes = ((recentEyeStats.maxOfOrNull { it.maxContinuousWorkSeconds } ?: 0L) / 60L).toInt()
    val proximityWarnings = recentEyeStats.sumOf { it.proximityWarningCount }
    val dryEyeWarnings = recentEyeStats.sumOf { it.eyeDryWarningCount }
    val lowLightWarnings = recentEyeStats.sumOf { it.lowLightWarningCount }
    val goalContinuousMinutes = uiState.dailyGoal.maxContinuousWorkMinutes.coerceIn(15, 120)

    viewModel.updateSettings { current ->
        val suggestedInterval = when {
            maxContinuousMinutes > goalContinuousMinutes + 15 -> (goalContinuousMinutes - 10).coerceIn(10, 45)
            maxContinuousMinutes > goalContinuousMinutes -> (goalContinuousMinutes - 5).coerceIn(10, 60)
            else -> current.warnIntervalMinutes.coerceIn(15, 45)
        }
        val suggestedRestSeconds = if (skipRate >= 40) {
            current.restDurationSeconds.coerceIn(15, 20)
        } else {
            current.restDurationSeconds.coerceAtLeast(20)
        }
        current.copy(
            reminderEnabled = true,
            warnIntervalMinutes = suggestedInterval,
            restDurationSeconds = suggestedRestSeconds,
            statsEnabled = true,
            notificationEnabled = true,
            keepAliveEnabled = true,
            preAlertEnabled = true,
            preAlertSeconds = if (skipRate >= 40) 45 else current.preAlertSeconds.coerceAtLeast(60),
            askBeforeBreak = skipRate < 40,
            disableSkip = false,
            proximityMonitoringEnabled = current.proximityMonitoringEnabled || proximityWarnings > 0,
            proximityCheckIntervalMinutes = if (proximityWarnings > 0) {
                current.proximityCheckIntervalMinutes.coerceAtMost(3)
            } else {
                current.proximityCheckIntervalMinutes
            },
            proximityDistanceMultiplierPercent = if (proximityWarnings > 0) {
                current.proximityDistanceMultiplierPercent.coerceAtLeast(140)
            } else {
                current.proximityDistanceMultiplierPercent
            },
            blinkMonitoringEnabled = current.blinkMonitoringEnabled || dryEyeWarnings > 0,
            blinkNoBlinkThresholdSeconds = if (dryEyeWarnings > 0) {
                current.blinkNoBlinkThresholdSeconds.coerceAtMost(8)
            } else {
                current.blinkNoBlinkThresholdSeconds
            },
            ambientLightMonitoringEnabled = current.ambientLightMonitoringEnabled || lowLightWarnings > 0,
            ambientLightLowLuxThreshold = if (lowLightWarnings > 0) {
                current.ambientLightLowLuxThreshold.coerceAtLeast(20)
            } else {
                current.ambientLightLowLuxThreshold
            },
            globalOverlayEnabled = current.globalOverlayEnabled || skipRate < 25,
        )
    }
    if (maxContinuousMinutes > goalContinuousMinutes) {
        viewModel.updateDailyGoal { current ->
            current.copy(maxContinuousWorkMinutes = (goalContinuousMinutes - 5).coerceIn(15, 120))
        }
    }
}

@Composable
private fun rememberEyeCareInsightSummary(
    uiState: ProjectLumenUiState,
    permissionRequirements: PermissionRequirements,
    shizukuReady: Boolean = false,
): EyeCareInsightSummary {
    val recentEyeStats = uiState.eyeStats.take(14)
    val completedBreaks = recentEyeStats.sumOf { it.completedBreakCount }
    val skips = recentEyeStats.sumOf { it.skipCount }
    val totalBreakDecisions = completedBreaks + skips
    val completionRate = if (totalBreakDecisions > 0) {
        (completedBreaks * 100) / totalBreakDecisions
    } else {
        100
    }.coerceIn(0, 100)
    val skipRate = if (totalBreakDecisions > 0) {
        (skips * 100) / totalBreakDecisions
    } else {
        0
    }.coerceIn(0, 100)
    val maxContinuousMinutes = ((recentEyeStats.maxOfOrNull { it.maxContinuousWorkSeconds } ?: 0L) / 60L).toInt()
    val averageContinuousMinutes = recentEyeStats
        .filter { it.maxContinuousWorkSeconds > 0L }
        .map { it.maxContinuousWorkSeconds / 60L }
        .average()
        .takeIf { !it.isNaN() }
        ?.roundToInt()
        ?: 0
    val workMinutes = (recentEyeStats.sumOf { it.workingSeconds } / 60L).toInt()
    val restMinutes = (recentEyeStats.sumOf { it.restSeconds } / 60L).toInt()
    val proximityWarnings = recentEyeStats.sumOf { it.proximityWarningCount }
    val dryEyeWarnings = recentEyeStats.sumOf { it.eyeDryWarningCount }
    val lowLightWarnings = recentEyeStats.sumOf { it.lowLightWarningCount }
    val riskScore = calculateRiskScore(
        stats = recentEyeStats,
        completionRate = completionRate,
        skipRate = skipRate,
        maxContinuousMinutes = maxContinuousMinutes,
        targetContinuousMinutes = uiState.dailyGoal.maxContinuousWorkMinutes,
        proximityWarnings = proximityWarnings,
        dryEyeWarnings = dryEyeWarnings,
        lowLightWarnings = lowLightWarnings,
        permissionRequirements = permissionRequirements,
    )
    val activeProtectionCount = listOf(
        uiState.settings.reminderEnabled,
        uiState.settings.notificationEnabled,
        uiState.settings.keepAliveEnabled,
        uiState.settings.preAlertEnabled,
        uiState.settings.proximityMonitoringEnabled,
        uiState.settings.blinkMonitoringEnabled,
        uiState.settings.ambientLightMonitoringEnabled,
        uiState.settings.globalOverlayEnabled,
        uiState.settings.statsEnabled,
    ).count { it }
    val missingSetupCount = listOf(
        permissionRequirements.notification && uiState.settings.notificationEnabled,
        permissionRequirements.exactAlarm && uiState.settings.notificationEnabled,
        permissionRequirements.fullScreenIntent && uiState.settings.notificationEnabled,
        permissionRequirements.camera && (uiState.settings.proximityMonitoringEnabled || uiState.settings.blinkMonitoringEnabled),
        permissionRequirements.overlay && uiState.settings.globalOverlayEnabled,
        permissionRequirements.writeSettings && uiState.settings.autoBrightnessEnabled &&
            !(uiState.settings.shizukuAdvancedModeEnabled && uiState.settings.shizukuNativeEyeProtectionEnabled),
        uiState.settings.shizukuAdvancedModeEnabled && !shizukuReady,
        uiState.settings.proximityMonitoringEnabled &&
            uiState.settings.proximityBaselineEyeDistancePx <= 0f &&
            uiState.settings.proximityBaselineFaceWidthPercent <= 0,
    ).count { it }
    val configurationScore = (100 - missingSetupCount * 12 + activeProtectionCount * 3)
        .coerceIn(0, 100)
    val riskReasons = buildList {
        if (recentEyeStats.isEmpty() || workMinutes == 0) add(R.string.eye_care_reason_no_stats)
        if (skipRate >= 40) add(R.string.eye_care_reason_skipped_breaks)
        if (maxContinuousMinutes > uiState.dailyGoal.maxContinuousWorkMinutes) add(R.string.eye_care_reason_long_work)
        if (proximityWarnings >= 2) add(R.string.eye_care_reason_distance)
        if (dryEyeWarnings >= 2) add(R.string.eye_care_reason_dry_eye)
        if (lowLightWarnings >= 2) add(R.string.eye_care_reason_low_light)
        if (missingSetupCount > 0) add(R.string.eye_care_reason_setup_missing)
        if (isEmpty()) add(R.string.eye_care_reason_stable)
    }
    val actions = buildList {
        if (!uiState.settings.reminderEnabled || !uiState.settings.statsEnabled) add(R.string.eye_care_action_enable_core)
        if (missingSetupCount > 0) add(R.string.eye_care_action_finish_permissions)
        if (uiState.settings.proximityMonitoringEnabled && uiState.settings.proximityBaselineEyeDistancePx <= 0f &&
            uiState.settings.proximityBaselineFaceWidthPercent <= 0
        ) {
            add(R.string.eye_care_action_calibrate_distance)
        }
        if (skipRate >= 40) add(R.string.eye_care_action_reduce_friction)
        if (maxContinuousMinutes > uiState.dailyGoal.maxContinuousWorkMinutes) add(R.string.eye_care_action_tighten_interval)
        if (proximityWarnings >= 2) add(R.string.eye_care_action_increase_distance)
        if (dryEyeWarnings >= 2) add(R.string.eye_care_action_blink_pause)
        if (lowLightWarnings >= 2) add(R.string.eye_care_action_room_light)
        if (isEmpty()) add(R.string.eye_care_action_keep_current)
    }
    val (labelRes, messageRes) = when {
        riskScore >= 75 -> R.string.eye_care_risk_high to R.string.eye_care_risk_high_message
        riskScore >= 45 -> R.string.eye_care_risk_medium to R.string.eye_care_risk_medium_message
        else -> R.string.eye_care_risk_low to R.string.eye_care_risk_low_message
    }
    return EyeCareInsightSummary(
        riskScore = riskScore,
        riskLabelRes = labelRes,
        riskMessageRes = messageRes,
        completionRate = completionRate,
        skipRate = skipRate,
        maxContinuousMinutes = maxContinuousMinutes,
        averageContinuousMinutes = averageContinuousMinutes,
        restMinutes = restMinutes,
        workMinutes = workMinutes,
        proximityWarnings = proximityWarnings,
        dryEyeWarnings = dryEyeWarnings,
        lowLightWarnings = lowLightWarnings,
        configurationScore = configurationScore,
        missingSetupCount = missingSetupCount,
        activeProtectionCount = activeProtectionCount,
        riskReasonRes = riskReasons,
        actionRes = actions,
    )
}

private fun calculateRiskScore(
    stats: List<DailyEyeStatsEntity>,
    completionRate: Int,
    skipRate: Int,
    maxContinuousMinutes: Int,
    targetContinuousMinutes: Int,
    proximityWarnings: Int,
    dryEyeWarnings: Int,
    lowLightWarnings: Int,
    permissionRequirements: PermissionRequirements,
): Int {
    if (stats.isEmpty() || stats.sumOf { it.workingSeconds } == 0L) {
        return 35
    }
    val continuousPenalty = ((maxContinuousMinutes - targetContinuousMinutes).coerceAtLeast(0) * 2).coerceAtMost(25)
    val completionPenalty = ((100 - completionRate) / 4).coerceIn(0, 25)
    val skipPenalty = (skipRate / 4).coerceIn(0, 20)
    val environmentPenalty = (proximityWarnings * 5 + dryEyeWarnings * 4 + lowLightWarnings * 3).coerceAtMost(25)
    val setupPenalty = listOf(
        permissionRequirements.notification,
        permissionRequirements.exactAlarm,
        permissionRequirements.fullScreenIntent,
        permissionRequirements.camera,
        permissionRequirements.overlay,
        permissionRequirements.writeSettings,
    ).count { it } * 3
    return (10 + continuousPenalty + completionPenalty + skipPenalty + environmentPenalty + setupPenalty)
        .coerceIn(0, 100)
}

@Composable
private fun RiskScoreHeader(summary: EyeCareInsightSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(riskColor(summary.riskScore).copy(alpha = 0.18f))
                    .border(1.dp, riskColor(summary.riskScore).copy(alpha = 0.62f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    summary.riskScore.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = riskColor(summary.riskScore),
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    stringResource(summary.riskLabelRes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(summary.riskMessageRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        LinearProgressIndicator(
            progress = { summary.riskScore / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape),
            color = riskColor(summary.riskScore),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
private fun InsightReasonList(reasons: List<Int>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.eye_care_key_findings),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        reasons.distinct().forEach { reasonRes ->
            StatusLine(Icons.Outlined.Info, stringResource(reasonRes))
        }
    }
}

@Composable
private fun InsightActionList(actions: List<Int>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.eye_care_today_actions),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        actions.distinct().forEach { actionRes ->
            StatusLine(Icons.Outlined.CheckCircle, stringResource(actionRes))
        }
    }
}

@Composable
private fun GuideStepLine(step: EyeCareGuideStep) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(LumenCardShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (step.complete) 0.22f else 0.34f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, LumenCardShape)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (step.complete) Icons.Outlined.CheckCircle else step.icon,
            contentDescription = null,
            tint = if (step.complete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(step.titleRes),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(step.detailRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StatusPill(
            if (step.complete) Icons.Outlined.CheckCircle else Icons.Outlined.Schedule,
            if (step.complete) R.string.eye_care_guide_done else R.string.eye_care_guide_pending,
        )
    }
}

@Composable
private fun PermissionTransparencyLine(
    icon: ImageVector,
    @StringRes titleRes: Int,
    @StringRes detailRes: Int,
    satisfied: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(LumenCardShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, LumenCardShape)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (satisfied) Icons.Outlined.CheckCircle else icon,
            contentDescription = null,
            tint = if (satisfied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(titleRes),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(detailRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            stringResource(if (satisfied) R.string.eye_care_permission_ready else R.string.eye_care_permission_needs_action),
            style = MaterialTheme.typography.labelLarge,
            color = if (satisfied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CapabilityLine(
    icon: ImageVector,
    @StringRes titleRes: Int,
    active: Boolean,
    @StringRes inactiveActionRes: Int,
    onConfigure: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(LumenCardShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, LumenCardShape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(
            stringResource(titleRes),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (active) {
            StatusPill(Icons.Outlined.CheckCircle, R.string.eye_care_capability_active)
        } else {
            OutlinedButton(onClick = onConfigure) {
                ButtonLabel(Icons.Outlined.Schedule, inactiveActionRes)
            }
        }
    }
}

internal fun isFamilyEyeCareModeActive(uiState: ProjectLumenUiState): Boolean {
    val settings = uiState.settings
    return settings.reminderEnabled &&
        settings.warnIntervalMinutes <= 15 &&
        settings.restDurationSeconds >= 30 &&
        settings.disableSkip &&
        settings.timeoutAutoBreak &&
        settings.proximityMonitoringEnabled &&
        settings.blinkMonitoringEnabled &&
        settings.ambientLightMonitoringEnabled &&
        settings.globalOverlayEnabled &&
        uiState.dailyGoal.maxContinuousWorkMinutes <= 30
}

@Composable
private fun riskColor(score: Int): Color {
    return when {
        score >= 75 -> MaterialTheme.colorScheme.error
        score >= 45 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
}
