package com.projectlumen.app.app

import android.app.TimePickerDialog
import android.content.Context
import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.projectlumen.app.R
import com.projectlumen.app.core.quarkkeeper.QuarkKeeperReminder
import com.projectlumen.app.core.quarkkeeper.QuarkKeeperSettings
import kotlin.math.abs

/**
 * The guard's tunables. Pure presentation plus [onSettingsChange]: every edit hands back a whole
 * [QuarkKeeperSettings] so the persistence layer keeps writing the value as one atomic unit, and this
 * section never has to know which store is behind it.
 *
 * Every edit goes through `sanitized()` before it is handed out, and that is not belt-and-braces —
 * it is the only correct way to write these fields. The bounds are coupled: every reminder's ceiling
 * is `forced - MIN_NODE_GAP_MINUTES`, the deadline's floor is `MIN_MINUTE_OF_DAY +
 * MIN_NODE_GAP_MINUTES`, and the snooze cutoff's floor is the deadline. Computing those bounds here
 * and clamping with `coerceIn` would throw the moment a dependency moved the wrong way (`coerceIn`
 * rejects a minimum above its maximum), and clamping each field against the *other* field's stale
 * value would leave the pair inconsistent. `sanitized()` applies the whole chain in one pass, in the
 * one order that cannot produce an inverted range.
 *
 * The reminder list is written the same way, whole: `sanitized()` sorts, de-duplicates and caps it,
 * so a row's index is only ever valid for the list that row was rendered from, and nothing here may
 * predict where an edited entry will end up.
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
            settings.reminders.forEachIndexed { index, reminder ->
                QuarkKeeperReminderRow(
                    reminder = reminder,
                    onPickMinute = { pickedMinute ->
                        val updated = settings.reminders.withEdited(index) { it.copy(minuteOfDay = pickedMinute) }
                        onSettingsChange(settings.copy(reminders = updated).sanitized())
                    },
                    onEnabledChange = { enabled ->
                        val updated = settings.reminders.withEdited(index) { it.copy(enabled = enabled) }
                        onSettingsChange(settings.copy(reminders = updated).sanitized())
                    },
                    onDelete = {
                        val updated = settings.reminders.without(index)
                        onSettingsChange(settings.copy(reminders = updated).sanitized())
                    },
                )
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = settings.reminders.size < QuarkKeeperSettings.MAX_REMINDERS,
                onClick = {
                    val updated = settings.reminders + QuarkKeeperReminder(quarkKeeperNewReminderMinute(settings))
                    onSettingsChange(settings.copy(reminders = updated).sanitized())
                },
            ) {
                ButtonLabel(Icons.Outlined.Add, R.string.quark_keeper_reminder_add)
            }
            Text(
                text = stringResource(R.string.quark_keeper_reminders_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Only once the button is dead: a disabled control with nothing next to it explaining why
            // reads as a bug rather than as a limit.
            if (settings.reminders.size >= QuarkKeeperSettings.MAX_REMINDERS) {
                Text(
                    text = stringResource(
                        R.string.quark_keeper_reminders_full,
                        QuarkKeeperSettings.MAX_REMINDERS,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
 * One entry of the reminder list: the minute it fires at, its own switch, and the way to drop it.
 *
 * The time is the entry's identity, so it is the only thing labelled. A caption per line would bury
 * the one value that tells the entries apart, and a [SwitchRow] would put a second label in a row
 * that has no width left for it once the time, the switch and the delete button are all there.
 */
@Composable
private fun QuarkKeeperReminderRow(
    reminder: QuarkKeeperReminder,
    onPickMinute: (Int) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val switchDescription = stringResource(R.string.quark_keeper_reminder_enabled)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LumenMinTouchTargetHeight)
            .clip(LumenPreferenceShape)
            .background(lumenNestedContainerColor)
            .padding(
                horizontal = SettingsPreferenceHorizontalPadding,
                vertical = SettingsPreferenceVerticalPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SettingsPreferenceInnerGap),
    ) {
        OutlinedButton(
            onClick = {
                openQuarkKeeperTimePicker(context, reminder.minuteOfDay) { hour, minute ->
                    onPickMinute(hour * 60 + minute)
                }
            },
        ) {
            Text(quarkKeeperMinuteLabel(reminder.minuteOfDay))
        }
        Spacer(Modifier.weight(1f))
        Switch(
            checked = reminder.enabled,
            onCheckedChange = onEnabledChange,
            // `Switch` takes no content description of its own, and an unlabelled one announces as a
            // bare "switch" next to a time the screen reader has already read.
            modifier = Modifier.semantics { contentDescription = switchDescription },
        )
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.quark_keeper_reminder_delete),
            )
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

/**
 * The list with the entry at [index] replaced.
 *
 * The index is only ever the one the edited row was rendered with. `sanitized()` re-sorts what comes
 * back, so any index held across a recomposition would name a different entry than the one the user
 * touched.
 */
private fun List<QuarkKeeperReminder>.withEdited(
    index: Int,
    edit: (QuarkKeeperReminder) -> QuarkKeeperReminder,
): List<QuarkKeeperReminder> = mapIndexed { at, reminder -> if (at == index) edit(reminder) else reminder }

/** The same list without the entry at [index], and with the same condition on that index. */
private fun List<QuarkKeeperReminder>.without(index: Int): List<QuarkKeeperReminder> =
    filterIndexed { at, _ -> at != index }

/**
 * Where a freshly added entry lands: the first free slot on the [QuarkKeeperSettings.MIN_NODE_GAP_MINUTES]
 * grid after the last entry, wrapping past midnight when the evening has no room left.
 *
 * Free, rather than simply "the last time plus the gap": `sanitized()` collapses an entry whose minute
 * the list already holds, so a candidate that is taken here would leave the add button looking dead.
 * The scan stops at the deadline's floor for the same reason — anything later is pulled back onto a
 * minute that may already be occupied. The wrap is what keeps a slot findable at all: the list holds
 * at most [QuarkKeeperSettings.MAX_REMINDERS] entries and the grid has far more slots than that.
 */
private fun quarkKeeperNewReminderMinute(settings: QuarkKeeperSettings): Int {
    val gap = QuarkKeeperSettings.MIN_NODE_GAP_MINUTES
    val taken = settings.reminders.map { it.minuteOfDay }
    val ceiling = settings.forcedMinuteOfDay - gap
    // An emptied list restarts from where the default day begins rather than from midnight.
    val last = taken.maxOrNull() ?: QuarkKeeperSettings.DEFAULT_REMINDERS.first().minuteOfDay
    val grid = (QuarkKeeperSettings.MIN_MINUTE_OF_DAY..QuarkKeeperSettings.MAX_MINUTE_OF_DAY step gap).toList()
    return (grid.filter { it > last } + grid.filter { it <= last })
        .firstOrNull { candidate -> candidate <= ceiling && taken.none { abs(it - candidate) < gap } }
        ?: ceiling
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
