package com.projectlumen.app.app

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.projectlumen.app.R
import com.projectlumen.app.core.quarkkeeper.QuarkKeeperSnapshot
import com.projectlumen.app.core.quarkkeeper.QuarkKeeperStats
import com.projectlumen.app.core.time.todayKey

/**
 * The home-screen summary of the guard: where today stands, plus the streak it is protecting.
 *
 * The whole card is one tap target that opens the dashboard. It is built from [Card] plus a
 * `clickable` rather than from [ActionCard] because that helper owns no click contract, and the clip
 * is applied before the `clickable` so the ripple follows the card's corners instead of spilling into
 * a rectangle behind them.
 */
@Composable
internal fun QuarkKeeperHomeCard(
    snapshot: QuarkKeeperSnapshot,
    stats: QuarkKeeperStats,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val nowMillis = rememberQuarkKeeperNow()
    val todayDateKey = remember(nowMillis) { todayKey(nowMillis) }
    // The store treats a row dated before today as absent — its midnight reset is derived on read
    // rather than driven by an alarm, because a device that was off at 00:00 never fires one. The
    // card applies the same rule so a snapshot captured before midnight cannot present yesterday's
    // check-in as today's.
    val checkedToday = snapshot.today.dateKey == todayDateKey && snapshot.today.checkedIn

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(LumenCardShape)
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.quark_keeper_settings_entry_title),
                onClick = onClick,
            )
            .animateContentSize(animationSpec = spring(stiffness = 420f, dampingRatio = 0.82f)),
        shape = LumenCardShape,
        colors = lumenCardColors(),
        elevation = lumenCardElevation(),
        border = lumenCardBorder(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader(Icons.Outlined.NotificationsActive, R.string.quark_keeper_title)
            StatusLine(
                icon = quarkKeeperTodayStatusIcon(snapshot, checkedToday),
                text = quarkKeeperTodayStatusText(
                    snapshot = snapshot,
                    checkedToday = checkedToday,
                    nowMillis = nowMillis,
                ),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SmallMetric(
                    R.string.quark_keeper_stats_streak,
                    quarkKeeperDaysLabel(stats.currentStreakDays),
                )
                SmallMetric(
                    R.string.quark_keeper_stats_vip,
                    quarkKeeperDaysLabel(stats.estimatedVipDays),
                )
            }
        }
    }
}
