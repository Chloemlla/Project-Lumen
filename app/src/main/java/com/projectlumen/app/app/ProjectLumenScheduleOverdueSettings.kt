package com.projectlumen.app.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.projectlumen.app.R
import com.projectlumen.app.core.database.entities.AppSettingsEntity
import com.projectlumen.app.core.schedule.ScheduleOverdueNag

@Composable
internal fun ScheduleOverdueNagCard(settings: AppSettingsEntity, viewModel: ProjectLumenViewModel) {
    SettingsSection(R.string.schedule_overdue_nag_section, Icons.Outlined.NotificationsActive) {
        SwitchRow(
            R.string.schedule_overdue_nag_enabled,
            Icons.Outlined.NotificationsActive,
            settings.scheduleOverdueNagEnabled,
        ) {
            viewModel.setScheduleOverdueNagEnabled(it)
        }
        // NumberSlider has no enabled parameter; hiding the controls while the switch is off
        // expresses "not active" without teaching the shared control a state every other settings
        // row would then have to honour.
        if (settings.scheduleOverdueNagEnabled) {
            NumberSlider(
                R.string.schedule_overdue_nag_interval,
                Icons.Outlined.Schedule,
                settings.scheduleOverdueNagIntervalMinutes,
                5f..360f,
                0,
                scheduleOverdueIntervalLabel(settings.scheduleOverdueNagIntervalMinutes),
            ) {
                viewModel.setScheduleOverdueNagIntervalMinutes(snapOverdueNagMinutes(it))
            }
            NumberSlider(
                R.string.schedule_overdue_nag_evening_time,
                Icons.Outlined.Schedule,
                settings.scheduleOverdueNagEveningMinute,
                0f..1435f,
                0,
                timeOfDayLabel(settings.scheduleOverdueNagEveningMinute),
            ) {
                viewModel.setScheduleOverdueNagEveningMinute(snapTimeMinute(it))
            }
        }
        Text(
            text = stringResource(R.string.schedule_overdue_nag_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun scheduleOverdueIntervalLabel(minutes: Int): String = when {
    minutes < 60 -> stringResource(R.string.schedule_overdue_nag_interval_minutes, minutes)
    minutes % 60 == 0 -> stringResource(R.string.schedule_overdue_nag_interval_hours, minutes / 60)
    else -> stringResource(
        R.string.schedule_overdue_nag_interval_hours_minutes,
        minutes / 60,
        minutes % 60,
    )
}

/** The slider runs continuously, so the committed value is pulled onto the 5-minute grid. */
private fun snapOverdueNagMinutes(value: Int): Int {
    val clamped = ScheduleOverdueNag.clampIntervalMinutes(value)
    return (clamped / ScheduleOverdueNag.MIN_INTERVAL_MINUTES) * ScheduleOverdueNag.MIN_INTERVAL_MINUTES
}
