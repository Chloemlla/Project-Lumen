package com.projectlumen.app.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.projectlumen.app.R
import com.projectlumen.app.core.database.entities.ScheduleOccurrenceEntity
import com.projectlumen.app.core.enums.ScheduleRecurrence
import java.time.Instant
import java.time.ZoneId

private const val SCHEDULE_HOME_MAX_ITEMS = 8

/** Today plus the next seven days; matches the [scheduleHomeItems] window. */
private const val SCHEDULE_HOME_WINDOW_DAYS = 8L

@Composable
internal fun HomeScheduleCard(
    tasks: List<ScheduleOccurrenceEntity>,
    nowMillis: Long,
    onToggleCompleted: (Long, Boolean) -> Unit,
    onOpenSchedule: (Long) -> Unit,
    onCreateSchedule: () -> Unit,
) {
    val items = remember(tasks, nowMillis) { scheduleHomeItems(tasks, nowMillis) }
    ActionCard {
        SectionHeader(Icons.Outlined.EventNote, R.string.schedule_home_title)
        if (items.isEmpty()) {
            EmptyStateMessage(R.string.schedule_home_empty)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items.forEach { task ->
                    ScheduleHomeRow(
                        task = task,
                        nowMillis = nowMillis,
                        onToggleCompleted = onToggleCompleted,
                        onOpen = { onOpenSchedule(task.id) },
                    )
                }
            }
        }
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onCreateSchedule,
        ) {
            ButtonLabel(Icons.Outlined.Add, R.string.schedule_create)
        }
    }
}

@Composable
private fun ScheduleHomeRow(
    task: ScheduleOccurrenceEntity,
    nowMillis: Long,
    onToggleCompleted: (Long, Boolean) -> Unit,
    onOpen: () -> Unit,
) {
    val openLabel = stringResource(R.string.schedule_edit_title)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LumenMinTouchTargetHeight)
            .clip(LumenPreferenceShape)
            .background(lumenNestedContainerColor)
            .clickable(role = Role.Button, onClickLabel = openLabel, onClick = onOpen)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = task.completed,
            onCheckedChange = { onToggleCompleted(task.id, it) },
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = smartWrapDisplayText(task.title),
                style = MaterialTheme.typography.titleSmall,
                color = if (task.completed) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                textDecoration = if (task.completed) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = scheduleHomeSecondaryLabel(task, nowMillis),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun scheduleHomeSecondaryLabel(task: ScheduleOccurrenceEntity, nowMillis: Long): String {
    val whenLabel = scheduleDayLabel(task.startAt, nowMillis) + " " +
        scheduleTimeRangeLabel(task.startAt, task.endAt, task.allDay)
    val recurrence = scheduleRecurrenceOf(task.recurrence)
    if (recurrence == ScheduleRecurrence.NONE) return whenLabel
    return whenLabel + " · " +
        stringResource(R.string.schedule_summary_repeat, scheduleRecurrenceLabel(recurrence))
}

/**
 * The single home-list filter: undeleted entries that start before the end of the eighth day from
 * today, keeping unfinished work plus anything already checked off today. Sorted by start time,
 * capped at [SCHEDULE_HOME_MAX_ITEMS].
 */
internal fun scheduleHomeItems(
    tasks: List<ScheduleOccurrenceEntity>,
    nowMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<ScheduleOccurrenceEntity> {
    val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
    val todayStart = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
    val windowEnd = today.plusDays(SCHEDULE_HOME_WINDOW_DAYS)
        .atStartOfDay(zoneId)
        .toInstant()
        .toEpochMilli()
    return tasks
        .filter { it.deletedAt == 0L }
        .filter { it.startAt < windowEnd }
        .filter { !it.completed || it.startAt >= todayStart }
        .sortedBy { it.startAt }
        .take(SCHEDULE_HOME_MAX_ITEMS)
}
