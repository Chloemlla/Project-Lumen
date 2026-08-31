package com.projectlumen.app.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.projectlumen.app.R
import com.projectlumen.app.core.database.entities.DailyEyeStatsEntity
import com.projectlumen.app.core.database.entities.TipTemplateEntity

@Composable
internal fun TodayStatsCard(stat: DailyEyeStatsEntity?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = 420f, dampingRatio = 0.82f)),
        shape = LumenCardShape,
        // Today's numbers are the headline of both Home and Statistics — the one Primary card
        // on each of those pages.
        colors = lumenCardColors(LumenCardEmphasis.Primary),
        elevation = lumenCardElevation(),
        border = lumenCardBorder(LumenCardEmphasis.Primary),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(Icons.Outlined.BarChart, R.string.today_summary)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallMetric(R.string.working_time, stringResource(R.string.hours_short, ((stat?.workingSeconds ?: 0L) / 3600.0)))
                SmallMetric(R.string.rest_time, minutesLabel(((stat?.restSeconds ?: 0L) / 60L).toInt()))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallMetric(R.string.skip_count, (stat?.skipCount ?: 0).toString())
                SmallMetric(R.string.completed_breaks, (stat?.completedBreakCount ?: 0).toString())
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallMetric(R.string.proximity_warnings, (stat?.proximityWarningCount ?: 0).toString())
                SmallMetric(R.string.proximity_close_time, minutesLabel(((stat?.proximityCloseSeconds ?: 0L) / 60L).toInt()))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallMetric(R.string.eye_dry_warnings, (stat?.eyeDryWarningCount ?: 0).toString())
                SmallMetric(R.string.low_light_warnings, (stat?.lowLightWarningCount ?: 0).toString())
            }
        }
    }
}

@Composable
internal fun GoalProgressCard(uiState: ProjectLumenUiState) {
    val goal = uiState.dailyGoal
    val eye = uiState.eyeStats.firstOrNull()
    val pomodoro = uiState.pomodoroStats.firstOrNull()
    val activeDays = remember(uiState.eyeStats, uiState.pomodoroStats) {
        val eyeActiveDates = uiState.eyeStats.take(7)
            .filter { it.workingSeconds > 0L || it.restSeconds > 0L }
            .map { it.statDate }
            .toSet()
        eyeActiveDates.size + uiState.pomodoroStats.take(7)
            .filter { it.completedFocusSessions > 0 && it.statDate !in eyeActiveDates }
            .size
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
            SectionHeader(Icons.Outlined.CheckCircle, R.string.today_goals)
            GoalLine(
                label = stringResource(R.string.daily_rest_goal),
                value = "${eye?.completedBreakCount ?: 0}/${goal.restBreakGoal}",
                progress = (eye?.completedBreakCount ?: 0).toFloat() / goal.restBreakGoal.coerceAtLeast(1).toFloat(),
            )
            val continuousMinutes = ((eye?.maxContinuousWorkSeconds ?: 0L) / 60L).toInt()
            GoalLine(
                label = stringResource(R.string.max_continuous_work_goal),
                value = stringResource(R.string.minutes_value, continuousMinutes),
                progress = 1f - (continuousMinutes.toFloat() / goal.maxContinuousWorkMinutes.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f),
            )
            GoalLine(
                label = stringResource(R.string.daily_pomodoro_goal),
                value = "${pomodoro?.completedFocusSessions ?: 0}/${goal.pomodoroGoal}",
                progress = (pomodoro?.completedFocusSessions ?: 0).toFloat() / goal.pomodoroGoal.coerceAtLeast(1).toFloat(),
            )
            GoalLine(
                label = stringResource(R.string.weekly_active_days_goal),
                value = "$activeDays/${goal.weeklyActiveDaysGoal}",
                progress = activeDays.toFloat() / goal.weeklyActiveDaysGoal.coerceAtLeast(1).toFloat(),
            )
        }
    }
}

@Composable
internal fun GoalLine(label: String, value: String, progress: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
internal fun TimerCard(
    label: String,
    seconds: Long,
    progress: Float,
    fallbackText: String,
    countdownStyle: String = COUNTDOWN_STYLE_CIRCLE,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 650),
        label = "timerProgress",
    )
    val timerText = if (seconds > 0) compactTime(seconds) else fallbackText
    val running = seconds > 0
    val transition = rememberInfiniteTransition(label = "timerPulse")
    val pulseScale = transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.035f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "timerPulseScale",
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = 420f, dampingRatio = 0.82f)),
        shape = LumenCardShape,
        // The running countdown is why the Break and Pomodoro pages exist — their one Primary card.
        colors = lumenCardColors(LumenCardEmphasis.Primary),
        elevation = lumenCardElevation(),
        border = lumenCardBorder(LumenCardEmphasis.Primary),
    ) {
        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                label,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.SemiBold,
            )
            when (countdownStyle) {
                COUNTDOWN_STYLE_BAR -> {
                    AnimatedTimerText(timerText, Modifier.padding(vertical = 40.dp))
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
                COUNTDOWN_STYLE_NUMBER -> {
                    AnimatedTimerText(
                        timerText = timerText,
                        modifier = Modifier
                            .padding(vertical = 56.dp)
                            .graphicsLayer {
                                val pulse = if (running) pulseScale.value else 1f
                                scaleX = pulse
                                scaleY = pulse
                            },
                    )
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .size(210.dp)
                            .graphicsLayer {
                                val pulse = if (running) pulseScale.value else 1f
                                scaleX = pulse
                                scaleY = pulse
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.fillMaxSize(),
                            strokeWidth = 12.dp,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                        AnimatedTimerText(timerText)
                    }
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
internal fun AnimatedTimerText(timerText: String, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = timerText,
        modifier = modifier,
        transitionSpec = {
            ContentTransform(
                targetContentEnter = fadeIn(tween(160)) + slideInVertically(tween(160)) { it / 2 },
                initialContentExit = fadeOut(tween(120)) + slideOutVertically(tween(120)) { -it / 2 },
                sizeTransform = SizeTransform(clip = false),
            )
        },
        label = "timerText",
    ) { text ->
        Text(
            text,
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun TemplatePreviewCard(template: TipTemplateEntity?) {
    val background = templateBackgroundColor(template)
    val primary = templatePrimaryColor(template)
    val animatedBackground by animateColorAsState(background, tween(220), label = "templateBackground")
    val animatedPrimary by animateColorAsState(primary, tween(220), label = "templatePrimary")
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = 420f, dampingRatio = 0.82f)),
        shape = LumenCardShape,
        colors = CardDefaults.cardColors(containerColor = animatedBackground),
        elevation = lumenCardElevation(),
        border = lumenCardBorder(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (template?.imagePath?.isNotBlank() == true) {
                UriImagePreview(template.imagePath)
            } else {
                ColorSwatch(animatedBackground)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(templateBreakTitle(template), style = MaterialTheme.typography.titleMedium, color = animatedPrimary)
                Text(
                    templateBreakSubtitle(template),
                    style = MaterialTheme.typography.bodyMedium,
                    color = animatedPrimary.copy(alpha = 0.76f),
                )
            }
        }
    }
}


