package com.projectlumen.app.app

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.projectlumen.app.R
import com.projectlumen.app.core.database.entities.DailyEyeStatsEntity
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
internal fun TrendCard(uiState: ProjectLumenUiState) {
    val recent = remember(uiState.eyeStats) { uiState.eyeStats.take(7).reversed() }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = 420f, dampingRatio = 0.82f)),
        shape = LumenCardShape,
        // Historical reference below the headline numbers — quiet, so the 7-day bars read as
        // supporting detail rather than a second focal point.
        colors = lumenCardColors(LumenCardEmphasis.Quiet),
        elevation = lumenCardElevation(),
        border = lumenCardBorder(LumenCardEmphasis.Quiet),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(Icons.Outlined.BarChart, R.string.weekly_trend)
            if (recent.isEmpty()) {
                EmptyStateMessage(
                    messageRes = R.string.statistics_no_trend_data,
                    illustration = EmptyStateIllustration.VideoFiles,
                )
            } else {
                val maxSeconds = recent.maxOf { max(it.workingSeconds, 1L) }
                recent.forEach { stat ->
                    val targetWidth = (stat.workingSeconds.toFloat() / maxSeconds.toFloat()).coerceIn(0.05f, 1f)
                    val animatedWidth by animateFloatAsState(
                        targetValue = targetWidth,
                        animationSpec = tween(durationMillis = 520),
                        label = "trendBarWidth",
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stat.statDate.takeLast(5), style = MaterialTheme.typography.labelMedium)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(10.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(animatedWidth)
                                    .height(10.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                        }
                        Text(minutesLabel((stat.workingSeconds / 60L).toInt()), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
internal fun HabitSuggestionCard(uiState: ProjectLumenUiState) {
    val suggestionIds = remember(uiState.eyeStats, uiState.dailyGoal.maxContinuousWorkMinutes) {
        val aggregate = eyeCareStatsAggregate(uiState.eyeStats)
        buildList {
            if (aggregate.skipRate >= EYE_CARE_SKIP_RATE_HIGH) add(R.string.habit_suggestion_shorter_break)
            if (aggregate.maxContinuousMinutes > uiState.dailyGoal.maxContinuousWorkMinutes) {
                add(R.string.habit_suggestion_strict_overlay)
            }
            if (aggregate.lowLightWarnings >= EYE_CARE_WARNING_ALERT_COUNT) add(R.string.habit_suggestion_room_light)
            if (aggregate.dryEyeWarnings >= EYE_CARE_WARNING_ALERT_COUNT) add(R.string.habit_suggestion_blink_pause)
            if (isEmpty()) add(R.string.habit_suggestion_keep_rhythm)
        }.distinct()
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = 420f, dampingRatio = 0.82f)),
        shape = LumenCardShape,
        colors = lumenCardColors(),
        elevation = lumenCardElevation(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(Icons.Outlined.WarningAmber, R.string.habit_suggestions)
            suggestionIds.forEach { suggestionRes ->
                StatusLine(Icons.Outlined.CheckCircle, stringResource(suggestionRes))
            }
        }
    }
}

@Composable
internal fun AdvancedStatsCard(
    eyeStats: List<DailyEyeStatsEntity>,
    pomodoroStats: List<com.projectlumen.app.core.database.entities.DailyPomodoroStatsEntity>,
) {
    val workSeconds = remember(eyeStats) { eyeStats.sumOf { it.workingSeconds } }
    val restSeconds = remember(eyeStats) { eyeStats.sumOf { it.restSeconds } }
    val completedBreaks = remember(eyeStats) { eyeStats.sumOf { it.completedBreakCount } }
    val skips = remember(eyeStats) { eyeStats.sumOf { it.skipCount } }
    val totalBreakDecisions = (completedBreaks + skips).coerceAtLeast(1)
    val averageContinuousMinutes = remember(eyeStats) {
        eyeStats
            .filter { it.maxContinuousWorkSeconds > 0L }
            .map { it.maxContinuousWorkSeconds / 60L }
            .average()
            .takeIf { !it.isNaN() }
            ?: 0.0
    }
    val completedTomatoes = remember(pomodoroStats) { pomodoroStats.sumOf { it.completedTomatoCount } }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = 420f, dampingRatio = 0.82f)),
        shape = LumenCardShape,
        colors = lumenCardColors(),
        elevation = lumenCardElevation(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(Icons.Outlined.BarChart, R.string.advanced_statistics)
            MetricRow(R.string.working_time, stringResource(R.string.hours_short, workSeconds / 3600.0))
            MetricRow(R.string.rest_time, minutesLabel((restSeconds / 60L).toInt()))
            MetricRow(R.string.rest_completion_rate, stringResource(R.string.percent_value, ((completedBreaks * 100) / totalBreakDecisions).coerceIn(0, 100)))
            MetricRow(R.string.skip_rate, stringResource(R.string.percent_value, ((skips * 100) / totalBreakDecisions).coerceIn(0, 100)))
            MetricRow(R.string.average_continuous_work, stringResource(R.string.minutes_value, averageContinuousMinutes.roundToInt()))
            MetricRow(R.string.completed_tomatoes, completedTomatoes.toString())
        }
    }
}


