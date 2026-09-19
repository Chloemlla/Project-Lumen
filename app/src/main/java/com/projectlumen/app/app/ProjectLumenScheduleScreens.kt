@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.projectlumen.app.app

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.projectlumen.app.R
import com.projectlumen.app.core.enums.ScheduleCategory
import com.projectlumen.app.core.enums.ScheduleEditScope
import com.projectlumen.app.core.enums.ScheduleRecurrence
import com.projectlumen.app.core.enums.ScheduleReminderMethod
import com.projectlumen.app.core.schedule.ScheduleDraft
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Sentinel stored in `reminderMinutesBefore` for "do not remind me". */
internal const val SCHEDULE_REMINDER_NONE = -1

/** The reminder offsets offered as chips, in minutes before the start time. */
private val ScheduleReminderOptions = listOf(-1, 0, 5, 10, 15, 30, 60, 24 * 60)

private const val SCHEDULE_MIN_DURATION_MILLIS = 5L * 60L * 1000L
private const val SCHEDULE_DEFAULT_START_HOUR = 9
private const val SCHEDULE_DEFAULT_REPEAT_DAYS = 30L

private val ScheduleTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private enum class ScheduleTimeField { START, END, REPEAT_UNTIL }

@Composable
internal fun ScheduleDetailScreen(
    uiState: ProjectLumenUiState,
    viewModel: ProjectLumenViewModel,
    onBack: () -> Unit,
) {
    val state = viewModel.scheduleDetailState.collectAsStateWithLifecycle().value
    var everOpened by rememberSaveable { mutableStateOf(false) }
    val draftMissing = state == null
    LaunchedEffect(draftMissing) {
        // Saving and deleting both clear the draft, which is the page's signal to leave. The flag
        // keeps the first frame (draft still loading) from bouncing straight back.
        if (!draftMissing) {
            everOpened = true
        } else if (everOpened) {
            onBack()
        }
    }
    val draftState = state ?: return
    val draft = draftState.draft
    val zone = remember { ZoneId.systemDefault() }
    val context = LocalContext.current
    var dateField by remember { mutableStateOf<ScheduleTimeField?>(null) }
    var timeField by remember { mutableStateOf<ScheduleTimeField?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    fun applyStart(startAt: Long) {
        viewModel.updateScheduleDraft { current ->
            val duration = scheduleDuration(current.startAt, current.endAt)
            current.copy(startAt = startAt, endAt = startAt + duration)
        }
    }

    fun applyEnd(endAt: Long) {
        viewModel.updateScheduleDraft { current ->
            val duration = scheduleDuration(current.startAt, current.endAt)
            current.copy(endAt = if (endAt > current.startAt) endAt else current.startAt + duration)
        }
    }

    LumenPage {
        ActionCard {
            SectionHeader(
                Icons.Outlined.EventNote,
                if (draftState.editingId == null) R.string.schedule_new_title else R.string.schedule_edit_title,
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = draft.title,
                onValueChange = { value -> viewModel.updateScheduleDraft { it.copy(title = value) } },
                label = { Text(stringResource(R.string.schedule_name_label)) },
                placeholder = { Text(stringResource(R.string.schedule_name_placeholder)) },
                singleLine = true,
            )
            ScheduleSummaryText(draft, uiState.nowMillis)
            SwitchRow(R.string.schedule_all_day, Icons.Outlined.Schedule, draft.allDay) { allDay ->
                viewModel.updateScheduleDraft { current ->
                    if (allDay) {
                        val startAt = scheduleStartOfDay(current.startAt, zone)
                        current.copy(
                            allDay = true,
                            startAt = startAt,
                            endAt = scheduleStartOfNextDay(startAt, zone),
                        )
                    } else {
                        val startAt = scheduleAtLocalTime(
                            current.startAt,
                            SCHEDULE_DEFAULT_START_HOUR,
                            0,
                            zone,
                        )
                        current.copy(
                            allDay = false,
                            startAt = startAt,
                            endAt = startAt + SCHEDULE_DEFAULT_DURATION_MILLIS,
                        )
                    }
                }
            }
            ScheduleMomentRow(
                labelRes = R.string.schedule_start,
                icon = Icons.Outlined.Schedule,
                millis = draft.startAt,
                allDay = draft.allDay,
                onPickDate = { dateField = ScheduleTimeField.START },
                onPickTime = { timeField = ScheduleTimeField.START },
            )
            ScheduleMomentRow(
                labelRes = R.string.schedule_end,
                icon = Icons.Outlined.Schedule,
                millis = draft.endAt,
                allDay = draft.allDay,
                onPickDate = { dateField = ScheduleTimeField.END },
                onPickTime = { timeField = ScheduleTimeField.END },
            )
        }

        ActionCard {
            SectionHeader(Icons.Outlined.Category, R.string.schedule_category_label)
            LumenFlowRow {
                ScheduleCategory.entries.forEach { category ->
                    FilterChip(
                        selected = draft.category == category,
                        onClick = { viewModel.updateScheduleDraft { it.copy(category = category) } },
                        label = { Text(scheduleCategoryLabel(category)) },
                    )
                }
            }
        }

        ActionCard {
            SectionHeader(Icons.Outlined.NotificationsActive, R.string.schedule_reminder_label)
            LumenFlowRow {
                ScheduleReminderOptions.forEach { minutes ->
                    FilterChip(
                        selected = draft.reminderMinutesBefore == minutes,
                        onClick = {
                            viewModel.updateScheduleDraft { it.copy(reminderMinutesBefore = minutes) }
                        },
                        label = { Text(scheduleReminderLabel(minutes)) },
                    )
                }
            }
            SectionHeader(Icons.Outlined.NotificationsActive, R.string.schedule_reminder_method_label)
            LumenFlowRow {
                ScheduleReminderMethod.entries.forEach { method ->
                    FilterChip(
                        // Without a reminder time there is nothing to deliver, so the delivery
                        // channel is meaningless until one is chosen.
                        enabled = draft.reminderMinutesBefore != SCHEDULE_REMINDER_NONE,
                        selected = draft.reminderMethod == method,
                        onClick = { viewModel.updateScheduleDraft { it.copy(reminderMethod = method) } },
                        label = { Text(scheduleReminderMethodLabel(method)) },
                    )
                }
            }
        }

        ActionCard {
            SectionHeader(Icons.Outlined.Repeat, R.string.schedule_recurrence_label)
            LumenFlowRow {
                ScheduleRecurrence.entries.forEach { recurrence ->
                    FilterChip(
                        selected = draft.recurrence == recurrence,
                        onClick = {
                            viewModel.updateScheduleDraft { current ->
                                val until = when {
                                    recurrence == ScheduleRecurrence.NONE -> 0L
                                    current.recurrenceUntil != 0L -> current.recurrenceUntil
                                    else -> scheduleDefaultRepeatUntil(current.startAt, zone)
                                }
                                current.copy(recurrence = recurrence, recurrenceUntil = until)
                            }
                        },
                        label = { Text(scheduleRecurrenceLabel(recurrence)) },
                    )
                }
            }
            SwitchRow(
                labelRes = R.string.schedule_recurrence_never_ends,
                icon = Icons.Outlined.EventRepeat,
                checked = draft.recurrenceUntil == 0L,
                enabled = draft.recurrence != ScheduleRecurrence.NONE,
            ) { neverEnds ->
                viewModel.updateScheduleDraft { current ->
                    current.copy(
                        recurrenceUntil = if (neverEnds) {
                            0L
                        } else {
                            scheduleDefaultRepeatUntil(current.startAt, zone)
                        },
                    )
                }
            }
            if (draft.recurrence != ScheduleRecurrence.NONE && draft.recurrenceUntil != 0L) {
                ScheduleMomentRow(
                    labelRes = R.string.schedule_recurrence_until,
                    icon = Icons.Outlined.EventRepeat,
                    millis = draft.recurrenceUntil,
                    allDay = true,
                    onPickDate = { dateField = ScheduleTimeField.REPEAT_UNTIL },
                    onPickTime = null,
                )
            }
        }

        ActionCard {
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = viewModel::requestSaveSchedule,
            ) {
                ButtonLabel(Icons.Outlined.Save, R.string.schedule_save)
            }
            if (draftState.editingId != null) {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (draftState.hasSeries) {
                            viewModel.requestDeleteSchedule()
                        } else {
                            showDeleteConfirm = true
                        }
                    },
                ) {
                    ButtonLabel(Icons.Outlined.Delete, R.string.schedule_delete)
                }
            }
        }
    }

    dateField?.let { field ->
        val initialMillis = when (field) {
            ScheduleTimeField.START -> scheduleUtcMidnightOf(draft.startAt, zone)
            ScheduleTimeField.END -> scheduleUtcMidnightOf(draft.endAt, zone)
            ScheduleTimeField.REPEAT_UNTIL -> scheduleUtcMidnightOf(draft.recurrenceUntil, zone)
        }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { dateField = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        val picked = datePickerState.selectedDateMillis
                        dateField = null
                        if (picked != null) {
                            when (field) {
                                ScheduleTimeField.START -> applyStart(scheduleWithDate(draft.startAt, picked, zone))
                                ScheduleTimeField.END -> applyEnd(scheduleWithDate(draft.endAt, picked, zone))
                                ScheduleTimeField.REPEAT_UNTIL -> viewModel.updateScheduleDraft { current ->
                                    current.copy(recurrenceUntil = scheduleEndOfDay(picked, zone))
                                }
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.schedule_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { dateField = null }) {
                    Text(stringResource(R.string.schedule_cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    timeField?.let { field ->
        val fieldMillis = if (field == ScheduleTimeField.START) draft.startAt else draft.endAt
        val zoned = Instant.ofEpochMilli(fieldMillis).atZone(zone)
        val timePickerState = rememberTimePickerState(
            initialHour = zoned.hour,
            initialMinute = zoned.minute,
            is24Hour = DateFormat.is24HourFormat(context),
        )
        AlertDialog(
            onDismissRequest = { timeField = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        val picked = scheduleAtLocalTime(fieldMillis, timePickerState.hour, timePickerState.minute, zone)
                        timeField = null
                        if (field == ScheduleTimeField.START) applyStart(picked) else applyEnd(picked)
                    },
                ) {
                    Text(stringResource(R.string.schedule_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { timeField = null }) {
                    Text(stringResource(R.string.schedule_cancel))
                }
            },
            text = {
                TimePicker(state = timePickerState)
            },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.schedule_delete_confirm_title)) },
            text = { Text(scheduleDateTimeLabel(draft.startAt)) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.requestDeleteSchedule()
                    },
                ) {
                    Text(stringResource(R.string.schedule_delete))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.schedule_cancel))
                }
            },
        )
    }

    if (draftState.pendingAction != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissScheduleScopePrompt,
            title = { Text(stringResource(R.string.schedule_scope_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ScheduleScopeOption(
                        label = stringResource(R.string.schedule_scope_this_only),
                        onSelect = { viewModel.confirmScheduleScope(ScheduleEditScope.THIS_ONLY) },
                    )
                    ScheduleScopeOption(
                        label = stringResource(R.string.schedule_scope_this_and_future),
                        onSelect = { viewModel.confirmScheduleScope(ScheduleEditScope.THIS_AND_FUTURE) },
                    )
                    ScheduleScopeOption(
                        label = stringResource(R.string.schedule_scope_all),
                        onSelect = { viewModel.confirmScheduleScope(ScheduleEditScope.ALL) },
                    )
                    // "This schedule only" keeps the series rule untouched, so the repeat edits on
                    // this page are dropped for that choice.
                    Text(
                        text = stringResource(R.string.schedule_scope_this_only_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                OutlinedButton(onClick = viewModel::dismissScheduleScopePrompt) {
                    Text(stringResource(R.string.schedule_cancel))
                }
            },
        )
    }
}

@Composable
private fun ScheduleScopeOption(label: String, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LumenMinTouchTargetHeight)
            .clip(LumenPreferenceShape)
            .clickable(role = Role.Button, onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ScheduleSummaryText(draft: ScheduleDraft, nowMillis: Long) {
    val summary = listOf(
        scheduleDayLabel(draft.startAt, nowMillis),
        scheduleTimeRangeLabel(draft.startAt, draft.endAt, draft.allDay),
        stringResource(R.string.schedule_summary_repeat, scheduleRecurrenceLabel(draft.recurrence)),
        stringResource(R.string.schedule_summary_category, scheduleCategoryLabel(draft.category)),
        stringResource(R.string.schedule_summary_reminder, scheduleReminderLabel(draft.reminderMinutesBefore)),
    ).joinToString(" · ")
    Text(
        text = summary,
        modifier = Modifier
            .fillMaxWidth()
            .clip(LumenPreferenceShape)
            .background(lumenNestedContainerColor)
            .padding(
                horizontal = SettingsPreferenceHorizontalPadding,
                vertical = SettingsPreferenceVerticalPadding,
            ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ScheduleMomentRow(
    labelRes: Int,
    icon: ImageVector,
    millis: Long,
    allDay: Boolean,
    onPickDate: () -> Unit,
    onPickTime: (() -> Unit)?,
) {
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
        LumenFlowRow {
            OutlinedButton(onClick = onPickDate) {
                Text(scheduleDateLabel(millis))
            }
            onPickTime?.let { pickTime ->
                OutlinedButton(
                    // An all-day entry is pinned to 00:00-00:00, so a time has nothing to edit.
                    enabled = !allDay,
                    onClick = pickTime,
                ) {
                    Text(scheduleTimeOfDayLabel(millis))
                }
            }
        }
    }
}

@Composable
internal fun scheduleDayLabel(startAt: Long, nowMillis: Long): String {
    val zone = remember { ZoneId.systemDefault() }
    val startDate = remember(startAt, zone) {
        Instant.ofEpochMilli(startAt).atZone(zone).toLocalDate()
    }
    val today = remember(nowMillis, zone) {
        Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    }
    val weekday = scheduleWeekdayLabel(startDate.dayOfWeek)
    return when (startDate) {
        today -> stringResource(R.string.schedule_day_today, weekday)
        today.plusDays(1) -> stringResource(R.string.schedule_day_tomorrow, weekday)
        today.minusDays(1) -> stringResource(R.string.schedule_day_yesterday, weekday)
        else -> scheduleDateLabel(startAt) + " " + weekday
    }
}

@Composable
internal fun scheduleTimeRangeLabel(startAt: Long, endAt: Long, allDay: Boolean): String {
    if (allDay) return stringResource(R.string.schedule_all_day)
    return stringResource(
        R.string.schedule_time_range,
        scheduleTimeOfDayLabel(startAt),
        scheduleTimeOfDayLabel(endAt),
    )
}

/** `2026年9月13日06:30` — the value shown when a full moment identifies the entry. */
@Composable
internal fun scheduleDateTimeLabel(millis: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    val zone = remember { ZoneId.systemDefault() }
    return remember(millis, locale, zone) {
        runCatching {
            Instant.ofEpochMilli(millis).atZone(zone).format(scheduleDateTimeFormatter(locale))
        }.getOrElse { millis.toString() }
    }
}

@Composable
internal fun scheduleDateLabel(millis: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    val zone = remember { ZoneId.systemDefault() }
    return remember(millis, locale, zone) {
        runCatching {
            Instant.ofEpochMilli(millis).atZone(zone).format(scheduleDateFormatter(locale))
        }.getOrElse { millis.toString() }
    }
}

@Composable
private fun scheduleTimeOfDayLabel(millis: Long): String {
    val zone = remember { ZoneId.systemDefault() }
    return remember(millis, zone) {
        Instant.ofEpochMilli(millis).atZone(zone).format(ScheduleTimeFormatter)
    }
}

@Composable
internal fun scheduleCategoryLabel(category: ScheduleCategory): String = stringResource(
    when (category) {
        ScheduleCategory.PERSONAL -> R.string.schedule_category_personal
        ScheduleCategory.WORK -> R.string.schedule_category_work
        ScheduleCategory.STUDY -> R.string.schedule_category_study
        ScheduleCategory.OTHER -> R.string.schedule_category_other
    },
)

@Composable
internal fun scheduleRecurrenceLabel(recurrence: ScheduleRecurrence): String = stringResource(
    when (recurrence) {
        ScheduleRecurrence.NONE -> R.string.schedule_recurrence_none
        ScheduleRecurrence.DAILY -> R.string.schedule_recurrence_daily
        ScheduleRecurrence.WEEKDAYS -> R.string.schedule_recurrence_weekdays
        ScheduleRecurrence.WEEKLY -> R.string.schedule_recurrence_weekly
        ScheduleRecurrence.MONTHLY -> R.string.schedule_recurrence_monthly
        ScheduleRecurrence.YEARLY -> R.string.schedule_recurrence_yearly
    },
)

@Composable
internal fun scheduleReminderLabel(minutesBefore: Int): String = when {
    minutesBefore == SCHEDULE_REMINDER_NONE -> stringResource(R.string.schedule_reminder_none)
    minutesBefore == 0 -> stringResource(R.string.schedule_reminder_on_time)
    minutesBefore == 60 -> stringResource(R.string.schedule_reminder_hour_before)
    minutesBefore == 24 * 60 -> stringResource(R.string.schedule_reminder_day_before)
    else -> stringResource(R.string.schedule_reminder_minutes_before, minutesBefore)
}

@Composable
internal fun scheduleReminderMethodLabel(method: ScheduleReminderMethod): String = stringResource(
    when (method) {
        ScheduleReminderMethod.NOTIFICATION -> R.string.schedule_reminder_method_notification
        ScheduleReminderMethod.ALARM -> R.string.schedule_reminder_method_alarm
    },
)

@Composable
private fun scheduleWeekdayLabel(dayOfWeek: DayOfWeek): String = stringResource(
    when (dayOfWeek) {
        DayOfWeek.MONDAY -> R.string.schedule_weekday_mon
        DayOfWeek.TUESDAY -> R.string.schedule_weekday_tue
        DayOfWeek.WEDNESDAY -> R.string.schedule_weekday_wed
        DayOfWeek.THURSDAY -> R.string.schedule_weekday_thu
        DayOfWeek.FRIDAY -> R.string.schedule_weekday_fri
        DayOfWeek.SATURDAY -> R.string.schedule_weekday_sat
        DayOfWeek.SUNDAY -> R.string.schedule_weekday_sun
    },
)

private fun scheduleDateFormatter(locale: Locale): DateTimeFormatter =
    if (locale.language == Locale.CHINESE.language) {
        DateTimeFormatter.ofPattern("yyyy年M月d日", locale)
    } else {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    }

private fun scheduleDateTimeFormatter(locale: Locale): DateTimeFormatter =
    if (locale.language == Locale.CHINESE.language) {
        DateTimeFormatter.ofPattern("yyyy年M月d日HH:mm", locale)
    } else {
        DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm", locale)
    }

private fun scheduleDuration(startAt: Long, endAt: Long): Long =
    (endAt - startAt).coerceAtLeast(SCHEDULE_MIN_DURATION_MILLIS)

private fun scheduleStartOfDay(millis: Long, zone: ZoneId): Long =
    Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        .atStartOfDay(zone)
        .toInstant()
        .toEpochMilli()

private fun scheduleStartOfNextDay(millis: Long, zone: ZoneId): Long =
    Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        .plusDays(1)
        .atStartOfDay(zone)
        .toInstant()
        .toEpochMilli()

/** Repeat rules end on the last millisecond of the chosen day, matching the stored convention. */
private fun scheduleEndOfDay(utcMidnightMillis: Long, zone: ZoneId): Long =
    Instant.ofEpochMilli(utcMidnightMillis).atZone(ZoneOffset.UTC).toLocalDate()
        .atTime(LocalTime.MAX)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()

private fun scheduleDefaultRepeatUntil(startAt: Long, zone: ZoneId): Long =
    Instant.ofEpochMilli(startAt).atZone(zone).toLocalDate()
        .plusDays(SCHEDULE_DEFAULT_REPEAT_DAYS)
        .atTime(LocalTime.MAX)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()

private fun scheduleAtLocalTime(millis: Long, hour: Int, minute: Int, zone: ZoneId): Long =
    Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        .atTime(hour, minute)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()

/** Re-dates `millis` to the picked day while keeping its local time of day. */
private fun scheduleWithDate(millis: Long, pickedUtcMidnightMillis: Long, zone: ZoneId): Long {
    val pickedDate = Instant.ofEpochMilli(pickedUtcMidnightMillis).atZone(ZoneOffset.UTC).toLocalDate()
    val timeOfDay = Instant.ofEpochMilli(millis).atZone(zone).toLocalTime()
    return pickedDate.atTime(timeOfDay).atZone(zone).toInstant().toEpochMilli()
}

/** Material's date picker speaks UTC midnight, so the initial value has to be built the same way. */
private fun scheduleUtcMidnightOf(millis: Long, zone: ZoneId): Long =
    Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        .atStartOfDay(ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()
