package com.projectlumen.app.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.projectlumen.app.R
import com.projectlumen.app.core.database.entities.AppSettingsEntity
import com.projectlumen.app.core.database.entities.DailyGoalEntity
import com.projectlumen.app.core.enums.QuietMode

@Composable
internal fun SettingsReminderSection(settings: AppSettingsEntity, viewModel: ProjectLumenViewModel) {
    SettingsSection(R.string.section_reminder, Icons.Outlined.Spa) {
        SwitchRow(R.string.enable_reminder, Icons.Outlined.Spa, settings.reminderEnabled) {
            viewModel.setReminderEnabled(it)
        }
        NumberSlider(R.string.warn_interval, Icons.Outlined.Schedule, settings.warnIntervalMinutes, 5f..120f, 22, stringResource(R.string.minutes_value, settings.warnIntervalMinutes)) {
            viewModel.updateSettings { current -> current.copy(warnIntervalMinutes = it) }
        }
        NumberSlider(R.string.rest_duration, Icons.Outlined.Spa, settings.restDurationSeconds, 10f..300f, 28, stringResource(R.string.seconds_value, settings.restDurationSeconds)) {
            viewModel.updateSettings { current -> current.copy(restDurationSeconds = it) }
        }
        SwitchRow(R.string.ask_before_break, Icons.Outlined.NotificationsActive, settings.askBeforeBreak) {
            viewModel.updateSettings { current -> current.copy(askBeforeBreak = it) }
        }
        SwitchRow(R.string.disable_skip, Icons.Outlined.SkipNext, settings.disableSkip) {
            viewModel.updateSettings { current -> current.copy(disableSkip = it) }
        }
    }
}

@Composable
internal fun SettingsPreAlertSection(settings: AppSettingsEntity, viewModel: ProjectLumenViewModel) {
    SettingsSection(R.string.section_pre_alert, Icons.Outlined.Schedule, initiallyExpanded = false) {
        SwitchRow(R.string.enable_pre_alert, Icons.Outlined.Schedule, settings.preAlertEnabled) {
            viewModel.updateSettings { current -> current.copy(preAlertEnabled = it) }
        }
        NumberSlider(R.string.pre_alert_seconds, Icons.Outlined.Schedule, settings.preAlertSeconds, 10f..300f, 28, stringResource(R.string.seconds_value, settings.preAlertSeconds)) {
            viewModel.updateSettings { current -> current.copy(preAlertSeconds = it) }
        }
    }
}

@Composable
internal fun SettingsPomodoroSection(settings: AppSettingsEntity, viewModel: ProjectLumenViewModel) {
    SettingsSection(R.string.section_pomodoro, Icons.Outlined.LocalCafe) {
        SwitchRow(R.string.enable_pomodoro, Icons.Outlined.LocalCafe, settings.pomodoroEnabled) {
            viewModel.setPomodoroEnabled(it)
        }
        NumberSlider(R.string.pomodoro_work, Icons.Outlined.LocalCafe, settings.pomodoroWorkMinutes, 5f..60f, 10, minutesLabel(settings.pomodoroWorkMinutes)) {
            viewModel.updateSettings { current -> current.copy(pomodoroWorkMinutes = it) }
        }
        NumberSlider(R.string.pomodoro_short_break, Icons.Outlined.Spa, settings.pomodoroShortBreakMinutes, 3f..20f, 16, minutesLabel(settings.pomodoroShortBreakMinutes)) {
            viewModel.updateSettings { current -> current.copy(pomodoroShortBreakMinutes = it) }
        }
        NumberSlider(R.string.pomodoro_long_break, Icons.Outlined.Spa, settings.pomodoroLongBreakMinutes, 5f..45f, 39, minutesLabel(settings.pomodoroLongBreakMinutes)) {
            viewModel.updateSettings { current -> current.copy(pomodoroLongBreakMinutes = it) }
        }
    }
}

@Composable
internal fun SettingsQuietHoursSection(settings: AppSettingsEntity, viewModel: ProjectLumenViewModel) {
    SettingsSection(R.string.section_quiet_hours, Icons.Outlined.Schedule, initiallyExpanded = false) {
        SwitchRow(R.string.quiet_hours, Icons.Outlined.Schedule, settings.quietHoursEnabled) {
            viewModel.updateSettings { current -> current.copy(quietHoursEnabled = it) }
        }
        AnimatedVisibility(
            visible = settings.quietHoursEnabled,
            enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { -it / 4 },
            exit = fadeOut(tween(120)) + slideOutVertically(tween(120)) { -it / 4 },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(SettingsPreferenceItemGap)) {
                NumberSlider(R.string.quiet_start, Icons.Outlined.Schedule, settings.quietStartMinute, 0f..1435f, 0, timeOfDayLabel(settings.quietStartMinute)) {
                    viewModel.updateSettings { current -> current.copy(quietStartMinute = snapTimeMinute(it)) }
                }
                NumberSlider(R.string.quiet_end, Icons.Outlined.Schedule, settings.quietEndMinute, 0f..1435f, 0, timeOfDayLabel(settings.quietEndMinute)) {
                    viewModel.updateSettings { current -> current.copy(quietEndMinute = snapTimeMinute(it)) }
                }
                Text(stringResource(R.string.quiet_mode), style = MaterialTheme.typography.titleSmall)
                LumenFlowRow {
                    QuietModeChip(R.string.quiet_mode_pause_timer, QuietMode.PAUSE_TIMER, settings, viewModel)
                    QuietModeChip(R.string.quiet_mode_silent_notifications, QuietMode.SILENT_NOTIFICATIONS, settings, viewModel)
                    QuietModeChip(R.string.quiet_mode_record_only, QuietMode.RECORD_ONLY, settings, viewModel)
                }
            }
        }
    }
}

@Composable
internal fun SettingsGoalsSection(dailyGoal: DailyGoalEntity, viewModel: ProjectLumenViewModel) {
    SettingsSection(R.string.section_goals, Icons.Outlined.CheckCircle, initiallyExpanded = false) {
        NumberSlider(R.string.daily_rest_goal, Icons.Outlined.Spa, dailyGoal.restBreakGoal, 1f..20f, 18, "${dailyGoal.restBreakGoal}") {
            viewModel.updateDailyGoal { current -> current.copy(restBreakGoal = it) }
        }
        NumberSlider(R.string.max_continuous_work_goal, Icons.Outlined.Schedule, dailyGoal.maxContinuousWorkMinutes, 15f..120f, 20, stringResource(R.string.minutes_value, dailyGoal.maxContinuousWorkMinutes)) {
            viewModel.updateDailyGoal { current -> current.copy(maxContinuousWorkMinutes = it) }
        }
        NumberSlider(R.string.daily_pomodoro_goal, Icons.Outlined.LocalCafe, dailyGoal.pomodoroGoal, 1f..16f, 15, "${dailyGoal.pomodoroGoal}") {
            viewModel.updateDailyGoal { current -> current.copy(pomodoroGoal = it) }
        }
        NumberSlider(R.string.weekly_active_days_goal, Icons.Outlined.CheckCircle, dailyGoal.weeklyActiveDaysGoal, 1f..7f, 5, "${dailyGoal.weeklyActiveDaysGoal}") {
            viewModel.updateDailyGoal { current -> current.copy(weeklyActiveDaysGoal = it) }
        }
    }
}
