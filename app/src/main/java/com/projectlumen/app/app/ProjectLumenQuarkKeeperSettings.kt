package com.projectlumen.app.app

import android.app.TimePickerDialog
import android.content.Context
import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.projectlumen.app.R
import com.projectlumen.app.core.quarkkeeper.QuarkKeeperSettings

/**
 * The guard's tunables. Pure presentation plus [onSettingsChange]: every edit hands back a whole
 * [QuarkKeeperSettings] so the persistence layer keeps writing the value as one atomic unit, and this
 * section never has to know which store is behind it.
 *
 * Every edit goes through `sanitized()` before it is handed out, and that is not belt-and-braces —
 * it is the only correct way to write these fields. The bounds are coupled: the daily reminder's
 * ceiling is `MAX_MINUTE_OF_DAY - MIN_NODE_GAP_MINUTES`, the deadline's floor is
 * `regular + MIN_NODE_GAP_MINUTES`, and the snooze cutoff's floor is the deadline. Computing those
 * bounds here and clamping with `coerceIn` would throw the moment a dependency moved the wrong way
 * (`coerceIn` rejects a minimum above its maximum), and clamping each field against the *other*
 * field's stale value would leave the pair inconsistent. `sanitized()` applies the whole chain in one
 * pass, in the one order that cannot produce an inverted range.
 */
@Composable
internal fun QuarkKeeperSettingsSection(
    settings: QuarkKeeperSettings,
    onSettingsChange: (QuarkKeeperSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    // SettingsSection owns the card and the collapse state but takes no modifier parameter, so the
    // caller's modifier goes on a thin wrapper around it.
    Column(modifier = modifier.fillMaxWidth()) {
        SettingsSection(R.string.quark_keeper_section_nodes, Icons.Outlined.Schedule) {
            QuarkKeeperTimeRow(
                labelRes = R.string.quark_keeper_regular_time,
                icon = Icons.Outlined.Schedule,
                minuteOfDay = settings.regularMinuteOfDay,
            ) { pickedMinute ->
                onSettingsChange(settings.copy(regularMinuteOfDay = pickedMinute).sanitized())
            }
            QuarkKeeperTimeRow(
                labelRes = R.string.quark_keeper_forced_time,
                icon = Icons.Outlined.NotificationsActive,
                minuteOfDay = settings.forcedMinuteOfDay,
            ) { pickedMinute ->
                onSettingsChange(settings.copy(forcedMinuteOfDay = pickedMinute).sanitized())
            }
            Text(
                text = stringResource(
                    R.string.quark_keeper_nodes_hint,
                    QuarkKeeperSettings.MIN_NODE_GAP_MINUTES,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NumberSlider(
                R.string.quark_keeper_snooze_minutes,
                Icons.Outlined.Refresh,
                settings.snoozeMinutes,
                QuarkKeeperSnoozeRange,
                quarkKeeperSnoozeSliderSteps(),
                quarkKeeperSnoozeLabel(settings.snoozeMinutes),
            ) { minutes ->
                onSettingsChange(settings.copy(snoozeMinutes = minutes).sanitized())
            }
            // Read-only on purpose, and rendered as a plain metric rather than as a row with a
            // button: the cutoff is derived from the deadline (it may never be earlier than it), so
            // offering a picker here would only invite a value the model would immediately move.
            MetricRow(
                R.string.quark_keeper_snooze_cutoff,
                quarkKeeperMinuteLabel(settings.snoozeCutoffMinuteOfDay),
            )
            Text(
                text = stringResource(
                    R.string.quark_keeper_snooze_blocked,
                    quarkKeeperMinuteLabel(settings.snoozeCutoffMinuteOfDay),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SwitchRow(
                R.string.quark_keeper_sound,
                Icons.AutoMirrored.Outlined.VolumeUp,
                settings.soundEnabled,
            ) { enabled ->
                onSettingsChange(settings.copy(soundEnabled = enabled).sanitized())
            }
        }
    }
}

/**
 * A labelled row whose only control is the current time, displayed to the minute.
 *
 * [TimePickerDialog] rather than a Compose time picker: the module stores a minute of the local day,
 * which that dialog edits directly with no conversion, and the app's schedule screen already opens
 * the same platform dialog for the same kind of input. Two differently-styled pickers for
 * "pick a time of day" would be the one inconsistency a user would actually notice.
 */
@Composable
private fun QuarkKeeperTimeRow(
    @StringRes labelRes: Int,
    icon: ImageVector,
    minuteOfDay: Int,
    onPickMinute: (Int) -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(LumenPreferenceShape)
            .background(lumenNestedContainerColor)
            .padding(
                horizontal = SettingsPreferenceHorizontalPadding,
                vertical = SettingsPreferenceVerticalPadding,
            ),
        verticalArrangement = Arrangement.spacedBy(SettingsPreferenceInnerGap),
    ) {
        LabelWithIcon(icon, labelRes, Modifier.fillMaxWidth())
        OutlinedButton(
            onClick = {
                openQuarkKeeperTimePicker(context, minuteOfDay) { hour, minute ->
                    onPickMinute(hour * 60 + minute)
                }
            },
        ) {
            Text(quarkKeeperMinuteLabel(minuteOfDay))
        }
    }
}

private fun openQuarkKeeperTimePicker(
    context: Context,
    minuteOfDay: Int,
    onTimeSet: (hour: Int, minute: Int) -> Unit,
) {
    val initial = minuteOfDay.coerceIn(
        QuarkKeeperSettings.MIN_MINUTE_OF_DAY,
        QuarkKeeperSettings.MAX_MINUTE_OF_DAY,
    )
    TimePickerDialog(
        context,
        { _, hour, minute -> onTimeSet(hour, minute) },
        initial / 60,
        initial % 60,
        DateFormat.is24HourFormat(context),
    ).show()
}

/** "15 min" for the slider's live value, in the module's own wording. */
@Composable
private fun quarkKeeperSnoozeLabel(minutes: Int): String =
    stringResource(R.string.quark_keeper_remaining_minutes, minutes)

/**
 * The number of stops between the endpoints of the snooze slider. Derived from the model's constants
 * rather than hard-coded, so widening the legal range moves the slider's grid with it instead of
 * leaving stop positions that no longer divide evenly.
 */
private fun quarkKeeperSnoozeSliderSteps(): Int {
    val span = QuarkKeeperSettings.MAX_SNOOZE_MINUTES - QuarkKeeperSettings.MIN_SNOOZE_MINUTES
    // steps counts the stops *between* the endpoints, so an evenly divided span needs one fewer.
    return (span / QUARK_KEEPER_SNOOZE_STEP_MINUTES - 1).coerceAtLeast(0)
}

/** The granularity users think in for a "remind me again in..." delay. */
private const val QUARK_KEEPER_SNOOZE_STEP_MINUTES = 5

private val QuarkKeeperSnoozeRange: ClosedFloatingPointRange<Float> =
    QuarkKeeperSettings.MIN_SNOOZE_MINUTES.toFloat()..QuarkKeeperSettings.MAX_SNOOZE_MINUTES.toFloat()
