package com.projectlumen.app.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.projectlumen.app.R
import com.projectlumen.app.core.quarkkeeper.QuarkKeeperCycleSummary
import com.projectlumen.app.core.quarkkeeper.QuarkKeeperDayCell
import com.projectlumen.app.core.quarkkeeper.QuarkKeeperDayState

/**
 * Cells per row. 30 does not divide by 7, so a week-shaped grid would end on a ragged two-cell row
 * and read as "the month is misaligned" rather than "the cycle is 30 days"; six columns tile the
 * cycle exactly into five even rows.
 */
private const val QUARK_KEEPER_GRID_COLUMNS = 6

private val QuarkKeeperGridGap = 4.dp

/**
 * The 30 squares of the current cycle.
 *
 * The cycle anchor is the first day the user ever checked in (see `QuarkKeeperCalendar`), which is
 * *not* derivable from today alone — so this composable takes the already-built
 * [QuarkKeeperCycleSummary] and never re-derives the cycle start. Every cell also carries its own
 * `dateKey`, `state` and `milestoneVipDays`; [todayKey] is used only to outline today, because a
 * square that is both "today" and "already checked in" reports `CHECKED_IN` and would otherwise lose
 * the one mark that says where the user is standing.
 */
@Composable
internal fun QuarkKeeperCycleGrid(
    cycle: QuarkKeeperCycleSummary,
    todayKey: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(QuarkKeeperGridGap)) {
        cycle.cells.chunked(QUARK_KEEPER_GRID_COLUMNS).forEach { rowCells ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(QuarkKeeperGridGap),
            ) {
                rowCells.forEach { cell ->
                    QuarkKeeperCycleCell(
                        cell = cell,
                        isToday = cell.dateKey == todayKey,
                        modifier = Modifier.weight(1f),
                    )
                }
                // The last row holds fewer cells than the others; equal-weight spacers keep the
                // remaining cells at full width instead of stretching them across the row.
                repeat(QUARK_KEEPER_GRID_COLUMNS - rowCells.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * One square. The four states are told apart by fill and outline rather than by text alone, since the
 * squares are ~40dp wide at phone width: filled-bright is done, filled-dim is missed, outlined-bright
 * is today, hollow is a day that has not started.
 */
@Composable
private fun QuarkKeeperCycleCell(cell: QuarkKeeperDayCell, isToday: Boolean, modifier: Modifier) {
    val milestone = cell.milestoneVipDays > 0
    val containerColor = when (cell.state) {
        QuarkKeeperDayState.CHECKED_IN -> MaterialTheme.colorScheme.primaryContainer
        QuarkKeeperDayState.MISSED -> MaterialTheme.colorScheme.surfaceContainerHighest
        QuarkKeeperDayState.PENDING -> MaterialTheme.colorScheme.surfaceContainerHigh
        QuarkKeeperDayState.UPCOMING -> Color.Transparent
    }
    val contentColor = when (cell.state) {
        QuarkKeeperDayState.CHECKED_IN -> MaterialTheme.colorScheme.onPrimaryContainer
        // Dimmed rather than error-red: the grid shows up to 30 of these at once, and a day missed
        // three weeks ago is information, not a warning the user can still act on.
        QuarkKeeperDayState.MISSED -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f)
        QuarkKeeperDayState.PENDING -> MaterialTheme.colorScheme.onSurface
        QuarkKeeperDayState.UPCOMING -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val border = when {
        // Today wins over the milestone ring: there is exactly one today and one border cannot say
        // two things, so the reward is carried by the badge instead of by a second outline.
        isToday -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        milestone -> BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)
        cell.state == QuarkKeeperDayState.UPCOMING -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        else -> null
    }
    val stateWord = stringResource(quarkKeeperDayStateLabelRes(cell.state))
    // The milestone sentence replaces the plain day sentence rather than joining it: read together
    // they would announce the day number twice on the one square that grants a reward.
    val description = if (milestone) {
        stringResource(
            R.string.quark_keeper_milestone_content_description,
            cell.cycleDay,
            cell.milestoneVipDays,
        ) + ", " + stateWord
    } else {
        stringResource(R.string.quark_keeper_day_content_description, cell.cycleDay, stateWord)
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(LumenPreferenceShape)
            .background(containerColor)
            .then(border?.let { stroke -> Modifier.border(stroke, LumenPreferenceShape) } ?: Modifier)
            // The squares repeat data that is also readable as text, so the screen-reader contract is
            // one description per day rather than a bare number.
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = cell.cycleDay.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
        )
        if (milestone) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary)
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            ) {
                Text(
                    text = stringResource(R.string.quark_keeper_milestone_badge, cell.milestoneVipDays),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

private fun quarkKeeperDayStateLabelRes(state: QuarkKeeperDayState): Int = when (state) {
    QuarkKeeperDayState.CHECKED_IN -> R.string.quark_keeper_day_checked_in
    QuarkKeeperDayState.MISSED -> R.string.quark_keeper_day_missed
    QuarkKeeperDayState.PENDING -> R.string.quark_keeper_day_pending
    QuarkKeeperDayState.UPCOMING -> R.string.quark_keeper_day_upcoming
}
