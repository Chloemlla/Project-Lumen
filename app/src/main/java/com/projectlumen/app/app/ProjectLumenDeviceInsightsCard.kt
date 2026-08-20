package com.projectlumen.app.app

import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Nightlight
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.projectlumen.app.R
import com.projectlumen.app.core.insights.AppUsageCategory
import com.projectlumen.app.core.insights.AppUsageSummary
import com.projectlumen.app.core.insights.BatteryChargeState
import com.projectlumen.app.core.insights.DeviceInsightRecommendation
import com.projectlumen.app.core.insights.DeviceInsightRecommendationKind
import com.projectlumen.app.core.insights.DeviceInsightsState
import com.projectlumen.app.core.insights.DeviceUsageAvailability
import com.projectlumen.app.core.insights.InsightPriority
import com.projectlumen.app.core.insights.UsageDataQuality

@Composable
internal fun DeviceUsageAndPowerInsightsCard(
    state: DeviceInsightsState,
    onRefresh: () -> Unit,
    onOpenUsageAccess: () -> Unit,
    onOpenBatteryUsage: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = 420f, dampingRatio = 0.82f)),
        shape = LumenCardShape,
        colors = lumenCardColors(LumenCardEmphasis.Quiet),
        elevation = lumenCardElevation(),
        border = lumenCardBorder(LumenCardEmphasis.Quiet),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // SectionHeader fills its width, so it needs a weighted slot of its own or the
                // progress indicator beside it is measured with no room left.
                Box(modifier = Modifier.weight(1f)) {
                    SectionHeader(Icons.Outlined.Apps, R.string.device_insights_title)
                }
                if (state.isRefreshing || state.availability == DeviceUsageAvailability.LOADING) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                }
            }
            Text(
                stringResource(R.string.device_insights_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            UsageAvailabilityContent(state, onOpenUsageAccess)
            DevicePowerContent(state)
            if (state.recommendations.isNotEmpty()) {
                SectionHeader(Icons.Outlined.CheckCircle, R.string.device_insights_recommendations)
                state.recommendations.forEach { recommendation ->
                    RecommendationLine(recommendation)
                }
            }
            LumenFlowRow {
                OutlinedButton(onClick = onRefresh, enabled = !state.isRefreshing) {
                    ButtonLabel(Icons.Outlined.Refresh, R.string.device_insights_refresh)
                }
                OutlinedButton(onClick = onOpenBatteryUsage) {
                    ButtonLabel(Icons.AutoMirrored.Outlined.OpenInNew, R.string.device_insights_open_battery_usage)
                }
            }
            Text(
                stringResource(R.string.device_insights_privacy_boundary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.device_insights_power_api_limit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UsageAvailabilityContent(
    state: DeviceInsightsState,
    onOpenUsageAccess: () -> Unit,
) {
    when (state.availability) {
        DeviceUsageAvailability.LOADING -> StatusLine(
            Icons.Outlined.Refresh,
            stringResource(R.string.device_insights_loading),
        )
        DeviceUsageAvailability.USAGE_ACCESS_REQUIRED -> {
            StatusLine(
                Icons.Outlined.Info,
                stringResource(R.string.device_insights_access_required),
            )
            Text(
                stringResource(R.string.device_insights_access_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(modifier = Modifier.fillMaxWidth(), onClick = onOpenUsageAccess) {
                ButtonLabel(Icons.AutoMirrored.Outlined.OpenInNew, R.string.device_insights_grant_access)
            }
        }
        DeviceUsageAvailability.EMPTY -> EmptyStateMessage(R.string.device_insights_empty)
        DeviceUsageAvailability.RESTRICTED -> StatusLine(
            Icons.Outlined.WarningAmber,
            stringResource(R.string.device_insights_restricted),
        )
        DeviceUsageAvailability.AVAILABLE -> state.usage?.let { usage ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallMetric(
                    R.string.device_insights_total_foreground,
                    durationLabel(usage.totalForegroundMillis),
                )
                SmallMetric(
                    R.string.device_insights_longest_session,
                    if (usage.quality == UsageDataQuality.EVENT_TIMELINE) {
                        durationLabel(usage.longestContinuousSessionMillis)
                    } else {
                        stringResource(R.string.device_insights_not_available_short)
                    },
                )
            }
            DeviceInsightsMetricRow(
                labelRes = R.string.device_insights_late_night,
                value = if (usage.quality == UsageDataQuality.EVENT_TIMELINE) {
                    durationLabel(usage.lateNightForegroundMillis)
                } else {
                    stringResource(R.string.device_insights_not_available_short)
                },
                valueState = if (usage.quality == UsageDataQuality.EVENT_TIMELINE &&
                    durationMinutes(usage.lateNightForegroundMillis) >= DEVICE_INSIGHTS_LATE_NIGHT_CAUTION_MINUTES
                ) {
                    DeviceInsightsValueState.Caution
                } else {
                    DeviceInsightsValueState.Neutral
                },
            )
            MetricRow(
                R.string.device_insights_app_switches,
                if (usage.quality == UsageDataQuality.EVENT_TIMELINE) {
                    usage.appSwitchCount.toString()
                } else {
                    stringResource(R.string.device_insights_not_available_short)
                },
            )
            if (usage.quality == UsageDataQuality.AGGREGATED_FALLBACK) {
                StatusLine(Icons.Outlined.Info, stringResource(R.string.device_insights_aggregate_fallback))
            }
            if (usage.topApps.isEmpty()) {
                EmptyStateMessage(R.string.device_insights_empty)
            } else {
                SectionHeader(Icons.Outlined.Schedule, R.string.device_insights_top_apps)
                val maximum = usage.topApps.maxOf(AppUsageSummary::foregroundMillis).coerceAtLeast(1L)
                usage.topApps.forEach { app -> UsageAppRow(app, maximum) }
            }
        }
    }
}

@Composable
private fun DevicePowerContent(state: DeviceInsightsState) {
    val power = state.power
    val levelPercent = power.levelPercent
    val temperatureCelsius = power.temperatureCelsius
    SectionHeader(Icons.Outlined.BatteryStd, R.string.device_insights_power_context)
    DeviceInsightsMetricRow(
        labelRes = R.string.device_insights_battery_level,
        value = levelPercent?.let { stringResource(R.string.percent_value, it) }
            ?: stringResource(R.string.device_insights_not_available_short),
        valueState = when {
            levelPercent == null -> DeviceInsightsValueState.Neutral
            levelPercent <= DEVICE_INSIGHTS_BATTERY_ALERT_PERCENT -> DeviceInsightsValueState.Alert
            levelPercent <= DEVICE_INSIGHTS_BATTERY_CAUTION_PERCENT -> DeviceInsightsValueState.Caution
            else -> DeviceInsightsValueState.Neutral
        },
    )
    MetricRow(R.string.device_insights_charge_state, chargeStateLabel(power.chargeState))
    DeviceInsightsMetricRow(
        labelRes = R.string.device_insights_temperature,
        value = temperatureCelsius?.let { stringResource(R.string.device_insights_temperature_value, it) }
            ?: stringResource(R.string.device_insights_not_available_short),
        valueState = when {
            temperatureCelsius == null -> DeviceInsightsValueState.Neutral
            temperatureCelsius >= DEVICE_INSIGHTS_TEMPERATURE_ALERT_CELSIUS -> DeviceInsightsValueState.Alert
            temperatureCelsius >= DEVICE_INSIGHTS_TEMPERATURE_CAUTION_CELSIUS -> DeviceInsightsValueState.Caution
            else -> DeviceInsightsValueState.Neutral
        },
    )
    DeviceInsightsMetricRow(
        labelRes = R.string.device_insights_power_saver,
        value = stringResource(if (power.powerSaveMode) R.string.device_insights_on else R.string.device_insights_off),
        valueState = if (power.powerSaveMode) DeviceInsightsValueState.Caution else DeviceInsightsValueState.Neutral,
    )
    DeviceInsightsMetricRow(
        labelRes = R.string.device_insights_background_policy,
        value = stringResource(
            when {
                power.appBackgroundRestricted -> R.string.device_insights_background_restricted
                power.batteryOptimizationExempt -> R.string.device_insights_background_unrestricted
                else -> R.string.device_insights_background_optimized
            },
        ),
        valueState = if (power.appBackgroundRestricted) {
            DeviceInsightsValueState.Alert
        } else {
            DeviceInsightsValueState.Neutral
        },
    )
}

/**
 * A [MetricRow] that can also carry the value's state. Reminder delivery depends on battery
 * and background policy, so a reader must be able to see "this one is out of range" without
 * decoding the label first.
 */
@Composable
private fun DeviceInsightsMetricRow(
    @StringRes labelRes: Int,
    value: String,
    valueState: DeviceInsightsValueState,
) {
    if (valueState == DeviceInsightsValueState.Neutral) {
        MetricRow(labelRes, value)
        return
    }
    val containerColor = if (valueState == DeviceInsightsValueState.Alert) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.tertiaryContainer
    }
    val contentColor = if (valueState == DeviceInsightsValueState.Alert) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onTertiaryContainer
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(LumenPreferenceShape)
            .background(containerColor)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            stringResource(labelRes),
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor,
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
        )
    }
}

private enum class DeviceInsightsValueState {
    Neutral,
    Caution,
    Alert,
}

private const val DEVICE_INSIGHTS_BATTERY_ALERT_PERCENT = 15
private const val DEVICE_INSIGHTS_BATTERY_CAUTION_PERCENT = 30
private const val DEVICE_INSIGHTS_TEMPERATURE_ALERT_CELSIUS = 43f
private const val DEVICE_INSIGHTS_TEMPERATURE_CAUTION_CELSIUS = 40f
private const val DEVICE_INSIGHTS_LATE_NIGHT_CAUTION_MINUTES = 30

@Composable
private fun UsageAppRow(app: AppUsageSummary, maximumMillis: Long) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(LumenPreferenceShape)
            .background(lumenNestedContainerColor)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = appCategoryLabel(app.category),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = durationLabel(app.foregroundMillis),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { (app.foregroundMillis.toFloat() / maximumMillis).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().clip(CircleShape),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
private fun RecommendationLine(recommendation: DeviceInsightRecommendation) {
    val icon: ImageVector = when (recommendation.kind) {
        DeviceInsightRecommendationKind.LATE_NIGHT_USE -> Icons.Outlined.Nightlight
        DeviceInsightRecommendationKind.STABLE_PATTERN -> Icons.Outlined.CheckCircle
        DeviceInsightRecommendationKind.LONG_CONTINUOUS_USE -> Icons.Outlined.Schedule
        DeviceInsightRecommendationKind.LOW_BATTERY -> Icons.Outlined.BatteryStd
        else -> if (recommendation.priority == InsightPriority.IMPORTANT) {
            Icons.Outlined.WarningAmber
        } else {
            Icons.Outlined.Info
        }
    }
    StatusLine(icon, recommendationText(recommendation))
}

@Composable
private fun recommendationText(recommendation: DeviceInsightRecommendation): String = when (recommendation.kind) {
    DeviceInsightRecommendationKind.HIGH_DAILY_EXPOSURE -> stringResource(
        R.string.device_insights_recommend_high_exposure,
        durationMinutes(recommendation.value),
    )
    DeviceInsightRecommendationKind.LONG_CONTINUOUS_USE -> stringResource(
        R.string.device_insights_recommend_long_session,
        durationMinutes(recommendation.value),
    )
    DeviceInsightRecommendationKind.LATE_NIGHT_USE -> stringResource(
        R.string.device_insights_recommend_late_night,
        durationMinutes(recommendation.value),
    )
    DeviceInsightRecommendationKind.FREQUENT_APP_SWITCHING -> stringResource(
        R.string.device_insights_recommend_switching,
        recommendation.value,
    )
    DeviceInsightRecommendationKind.DOMINANT_VISUAL_APP -> stringResource(
        R.string.device_insights_recommend_dominant_app,
        recommendation.subject,
        durationMinutes(recommendation.value),
    )
    DeviceInsightRecommendationKind.DEVICE_WARM -> stringResource(R.string.device_insights_recommend_warm)
    DeviceInsightRecommendationKind.LOW_BATTERY -> stringResource(
        R.string.device_insights_recommend_low_battery,
        recommendation.value,
    )
    DeviceInsightRecommendationKind.POWER_RESTRICTION -> stringResource(R.string.device_insights_recommend_power_restriction)
    DeviceInsightRecommendationKind.STABLE_PATTERN -> stringResource(R.string.device_insights_recommend_stable)
}

@Composable
private fun durationLabel(durationMillis: Long): String {
    val minutes = durationMinutes(durationMillis)
    return if (minutes >= 60) {
        stringResource(R.string.hours_short, minutes / 60f)
    } else {
        stringResource(R.string.minutes_value, minutes)
    }
}

private fun durationMinutes(durationMillis: Long): Int =
    (durationMillis.coerceAtLeast(0L) / 60_000L).toInt()

@Composable
private fun chargeStateLabel(state: BatteryChargeState): String = stringResource(
    when (state) {
        BatteryChargeState.CHARGING -> R.string.device_insights_charging
        BatteryChargeState.FULL -> R.string.device_insights_full
        BatteryChargeState.DISCHARGING -> R.string.device_insights_discharging
        BatteryChargeState.NOT_CHARGING -> R.string.device_insights_not_charging
        BatteryChargeState.UNKNOWN -> R.string.device_insights_unknown
    },
)

@Composable
private fun appCategoryLabel(category: AppUsageCategory): String = stringResource(
    when (category) {
        AppUsageCategory.PRODUCTIVITY -> R.string.device_insights_category_productivity
        AppUsageCategory.SOCIAL -> R.string.device_insights_category_social
        AppUsageCategory.VIDEO -> R.string.device_insights_category_video
        AppUsageCategory.GAME -> R.string.device_insights_category_game
        AppUsageCategory.READING -> R.string.device_insights_category_reading
        AppUsageCategory.COMMUNICATION -> R.string.device_insights_category_communication
        AppUsageCategory.NAVIGATION -> R.string.device_insights_category_navigation
        AppUsageCategory.AUDIO -> R.string.device_insights_category_audio
        AppUsageCategory.OTHER -> R.string.device_insights_category_other
    },
)
