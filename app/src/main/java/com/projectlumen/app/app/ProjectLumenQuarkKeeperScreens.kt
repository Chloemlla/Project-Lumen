package com.projectlumen.app.app

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.projectlumen.app.R
import com.projectlumen.app.core.quarkkeeper.QuarkKeeperClock
import com.projectlumen.app.core.quarkkeeper.QuarkKeeperCycleSummary
import com.projectlumen.app.core.quarkkeeper.QuarkKeeperHistoryEntry
import com.projectlumen.app.core.quarkkeeper.QuarkKeeperRemaining
import com.projectlumen.app.core.quarkkeeper.QuarkKeeperSnapshot
import com.projectlumen.app.core.quarkkeeper.QuarkKeeperStats
import com.projectlumen.app.core.time.todayKey
import kotlinx.coroutines.delay

/**
 * The guard's dashboard: today's state, the permissions that decide whether a reminder can actually
 * arrive, the streak figures, the 30-day cycle and the check-in log.
 *
 * [cycle] and [stats] arrive already built. The composable deliberately does not call
 * `QuarkKeeperCalendar` itself: the cycle must be anchored on the same "today" the alarms were
 * reconciled against, and a second computation here could disagree with the caller's by a day around
 * midnight.
 *
 * The page is a [LazyColumn] rather than [LumenPage] because the log is unbounded — the store keeps up
 * to two years of days — and a `LazyColumn` nested inside `LumenPage`'s `verticalScroll` would be
 * measured against an infinite height and throw. The paddings below mirror the page tokens so the
 * dashboard lines up with every other screen, and the log is a lazy `items` segment after its header;
 * a card cannot span lazy items, so each log row carries its own nested-container background instead.
 */
@Composable
internal fun QuarkKeeperDashboardScreen(
    snapshot: QuarkKeeperSnapshot,
    cycle: QuarkKeeperCycleSummary,
    stats: QuarkKeeperStats,
    permissions: PermissionRequirements,
    developerModeEnabled: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onMarkDone: () -> Unit,
    onUndo: () -> Unit,
    onOpenQuark: () -> Unit,
    onTriggerReminderNow: () -> Unit,
    onTriggerDeadlineNow: () -> Unit,
    onRequestExactAlarm: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    sessionMessage: String?,
) {
    val nowMillis = rememberQuarkKeeperNow()
    val todayDateKey = remember(nowMillis) { todayKey(nowMillis) }
    // The store treats a row dated before today as absent — its midnight reset is derived on read
    // rather than driven by an alarm, because a device that was off at 00:00 never fires one. The
    // dashboard applies the same rule so a snapshot captured before midnight cannot present
    // yesterday's check-in as today's.
    val checkedToday = snapshot.today.dateKey == todayDateKey && snapshot.today.checkedIn
    val snoozedUntilMillis = snapshot.today.snoozedUntilMillis.takeIf { it > nowMillis } ?: 0L
    val pageTokens = rememberLumenUiTokens(LocalContext.current).page
    // Same breakpoints as LumenPage: Android 16+ large screens ignore forced orientation, so wide
    // pages widen their gutters instead of stretching the content.
    val widthDp = LocalConfiguration.current.screenWidthDp
    val horizontalPadding = when {
        widthDp >= 840 -> maxOf(pageTokens.contentPaddingStartDp, 24f)
        widthDp >= 600 -> maxOf(pageTokens.contentPaddingStartDp, 16f)
        else -> pageTokens.contentPaddingStartDp
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = pageTokens.maxContentWidthDp.dp),
            contentPadding = PaddingValues(
                start = horizontalPadding.dp,
                top = pageTokens.contentPaddingTopDp.dp,
                end = horizontalPadding.dp,
                bottom = pageTokens.contentPaddingBottomDp.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(pageTokens.sectionGapDp.dp),
        ) {
            // First, not last: it confirms the tap the user just made, and it is cleared by the
            // caller on the next state change, so it never becomes part of the page's furniture.
            sessionMessage?.let { message ->
                item(key = "session-message") {
                    QuarkKeeperConfirmation(message)
                }
            }

            item(key = "intro") {
                PageIntro(
                    icon = Icons.Outlined.NotificationsActive,
                    titleRes = R.string.quark_keeper_title,
                    message = stringResource(R.string.quark_keeper_settings_entry_summary),
                )
            }

            item(key = "enabled") {
                ActionCard {
                    SwitchRow(
                        R.string.quark_keeper_enabled,
                        Icons.Outlined.NotificationsActive,
                        snapshot.settings.enabled,
                    ) { onToggleEnabled(it) }
                    Text(
                        text = stringResource(R.string.quark_keeper_enabled_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item(key = "today") {
                ActionCard {
                    SectionHeader(Icons.Outlined.CheckCircle, R.string.quark_keeper_section_status)
                    Text(
                        text = quarkKeeperTodayStatusText(
                            snapshot = snapshot,
                            checkedToday = checkedToday,
                            nowMillis = nowMillis,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            !snapshot.settings.enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                            checkedToday -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                    )
                    if (snoozedUntilMillis > 0L && !checkedToday) {
                        StatusLine(
                            Icons.Outlined.Schedule,
                            stringResource(
                                R.string.quark_keeper_today_snoozed,
                                quarkKeeperClockLabel(snoozedUntilMillis),
                            ),
                        )
                    }
                    if (checkedToday) {
                        OutlinedButton(onClick = onUndo) {
                            Text(stringResource(R.string.quark_keeper_action_undo))
                        }
                    } else {
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onMarkDone,
                        ) {
                            ButtonLabel(Icons.Outlined.CheckCircle, R.string.quark_keeper_action_check_in)
                        }
                        // Offered next to the check-in button rather than only from the alert: the
                        // user who is already in the app is the one most likely to go and do it now.
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onOpenQuark,
                        ) {
                            ButtonLabel(Icons.AutoMirrored.Outlined.OpenInNew, R.string.quark_keeper_action_go)
                        }
                    }
                }
            }

            item(key = "permissions") {
                ActionCard {
                    SectionHeader(Icons.Outlined.Lock, R.string.quark_keeper_section_permissions)
                    Text(
                        text = stringResource(R.string.quark_keeper_permission_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // The requirement flags are "needed", not "granted", so they invert here once
                    // rather than in four call sites.
                    QuarkKeeperPermissionRow(
                        labelRes = R.string.quark_keeper_permission_notifications,
                        icon = Icons.Outlined.NotificationsActive,
                        granted = !permissions.notification,
                        onGrant = onOpenNotificationSettings,
                    )
                    QuarkKeeperPermissionRow(
                        labelRes = R.string.quark_keeper_permission_exact_alarm,
                        icon = Icons.Outlined.Schedule,
                        granted = !permissions.exactAlarm,
                        onGrant = onRequestExactAlarm,
                    )
                    QuarkKeeperPermissionRow(
                        labelRes = R.string.quark_keeper_permission_overlay,
                        icon = Icons.Outlined.Lock,
                        granted = !permissions.overlay,
                        onGrant = onRequestOverlay,
                    )
                    // Last, and not because it is least important: the exemption is what keeps the two
                    // alarms above from being deferred by Doze, so without it the other three rows can
                    // all read "granted" while the reminder still arrives late. It is listed beside
                    // them because it is the same kind of thing — a trip to a system screen the guard
                    // cannot make on the user's behalf.
                    QuarkKeeperPermissionRow(
                        labelRes = R.string.quark_keeper_permission_battery,
                        icon = Icons.Outlined.BatterySaver,
                        granted = !permissions.batteryExemptionNeeded,
                        onGrant = onRequestBatteryOptimization,
                    )
                }
            }

            item(key = "stats") {
                ActionCard {
                    SectionHeader(Icons.Outlined.BarChart, R.string.quark_keeper_section_stats)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SmallMetric(
                            R.string.quark_keeper_stats_streak,
                            quarkKeeperDaysLabel(stats.currentStreakDays),
                        )
                        SmallMetric(
                            R.string.quark_keeper_stats_longest,
                            quarkKeeperDaysLabel(stats.longestStreakDays),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SmallMetric(
                            R.string.quark_keeper_stats_total,
                            quarkKeeperDaysLabel(stats.totalCheckinDays),
                        )
                        SmallMetric(
                            R.string.quark_keeper_stats_vip,
                            quarkKeeperDaysLabel(stats.estimatedVipDays),
                        )
                    }
                    Text(
                        text = stringResource(R.string.quark_keeper_estimate_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item(key = "cycle") {
                ActionCard {
                    SectionHeader(Icons.Outlined.EventRepeat, R.string.quark_keeper_section_cycle)
                    if (cycle.cells.isEmpty()) {
                        EmptyStateMessage(R.string.quark_keeper_cycle_empty)
                    } else {
                        // The cell for today is what carries the day number, and it is the only
                        // honest source for it: `completedDays` counts check-ins, which is a
                        // different number as soon as a day is missed. A cycle built for another day
                        // has no cell for today at all, so the caption is dropped rather than filled
                        // with a guess.
                        val currentCycleDay = cycle.cells
                            .firstOrNull { cell -> cell.dateKey == todayDateKey }
                            ?.cycleDay
                        if (currentCycleDay != null) {
                            Text(
                                text = stringResource(
                                    R.string.quark_keeper_cycle_progress,
                                    // cycleIndex counts from zero in the model; a caption that says
                                    // "Cycle 0" to a first-time user reads as a bug.
                                    cycle.cycleIndex + 1,
                                    currentCycleDay,
                                    cycle.cells.size,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        QuarkKeeperCycleGrid(cycle = cycle, todayKey = todayDateKey)
                    }
                }
            }

            // Developer-only, and placed here rather than at the end of the page on purpose: the
            // check-in log below is an unbounded lazy list — up to two years of days — so a block after
            // it would be reachable only by scrolling past every entry.
            //
            // The buttons are deliberately left tappable with no `enabled` condition. They run the same
            // chain a scheduled reminder runs, and that chain is what declines when the guard is off or
            // the day is already checked in; disabling them here would hide exactly the behaviour a
            // developer is trying to observe.
            if (developerModeEnabled) {
                item(key = "developer-triggers") {
                    ActionCard {
                        SectionHeader(Icons.Outlined.Code, R.string.quark_keeper_dev_section)
                        Text(
                            text = stringResource(R.string.quark_keeper_dev_trigger_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onTriggerReminderNow,
                        ) {
                            ButtonLabel(
                                Icons.Outlined.NotificationsActive,
                                R.string.quark_keeper_dev_trigger_reminder,
                            )
                        }
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onTriggerDeadlineNow,
                        ) {
                            ButtonLabel(
                                Icons.Outlined.WarningAmber,
                                R.string.quark_keeper_dev_trigger_deadline,
                            )
                        }
                    }
                }
            }

            item(key = "history-header") {
                SectionHeader(Icons.Outlined.EventNote, R.string.quark_keeper_section_history)
            }
            if (stats.history.isEmpty()) {
                item(key = "history-empty") {
                    EmptyStateMessage(R.string.quark_keeper_history_empty)
                }
            } else {
                // Newest first, straight from `buildStats`. Keyed by date so a check-in or an undo
                // reuses the rows it did not change instead of rebuilding the visible window.
                items(items = stats.history, key = { entry -> entry.dateKey }) { entry ->
                    QuarkKeeperHistoryRow(entry = entry, nowMillis = nowMillis)
                }
            }
        }
    }
}

/**
 * The confirmation of the tap the user just made, animated in.
 *
 * The button that raises it sits at the end of the page and the line lands at the top of a scrolling
 * list, so a static row reads as something that was always there rather than as an answer — the tap
 * appears to have done nothing. It settles rather than bounces: the tap is a routine confirmation, not
 * a reward.
 *
 * Keyed on the message so the second tap animates too. A check-in undone straight after being recorded
 * swaps the text, and reusing the previous state object would leave that swap unanimated — exactly the
 * case where the user is most likely to be looking for the acknowledgement.
 */
@Composable
private fun QuarkKeeperConfirmation(message: String) {
    var shown by remember(message) { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = CONFIRMATION_ENTER_MILLIS),
        label = "quark-keeper-confirmation",
    )
    LaunchedEffect(message) { shown = true }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = progress
                // Read inside graphicsLayer so the animation invalidates drawing only, as the guard's
                // pulse does, rather than recomposing the row on every frame.
                val scale = CONFIRMATION_ENTER_SCALE + (1f - CONFIRMATION_ENTER_SCALE) * progress
                scaleX = scale
                scaleY = scale
            },
    ) {
        StatusLine(Icons.Outlined.CheckCircle, message)
    }
}

/**
 * One permission with its state and, when it is missing, the way to fix it.
 *
 * [LabelWithIcon] and the nested-container row are the same pieces [SwitchRow] is built from, so a
 * permission reads like every other preference row in the app rather than like a bespoke list.
 */
@Composable
private fun QuarkKeeperPermissionRow(
    @StringRes labelRes: Int,
    icon: ImageVector,
    granted: Boolean,
    onGrant: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(LumenPreferenceShape)
            .background(lumenNestedContainerColor)
            .padding(
                horizontal = SettingsPreferenceHorizontalPadding,
                vertical = SettingsPreferenceVerticalPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LabelWithIcon(icon, labelRes, Modifier.weight(1f))
        if (granted) {
            Text(
                text = stringResource(R.string.quark_keeper_permission_granted),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            OutlinedButton(onClick = onGrant) {
                Text(stringResource(R.string.quark_keeper_permission_grant))
            }
        }
    }
}

/**
 * One log line. The day label comes from the schedule screen's formatter so "Today"/"Yesterday" read
 * the same here as they do on an event row — two date styles for the same day in one app is the kind
 * of inconsistency users read as a bug.
 */
@Composable
private fun QuarkKeeperHistoryRow(entry: QuarkKeeperHistoryEntry, nowMillis: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(LumenPreferenceShape)
            .background(lumenNestedContainerColor)
            .padding(
                horizontal = SettingsPreferenceHorizontalPadding,
                vertical = SettingsPreferenceVerticalPadding,
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = scheduleDayLabel(entry.checkedInAtMillis, nowMillis),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = quarkKeeperClockLabel(entry.checkedInAtMillis),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** How often the dashboard re-reads the wall clock; see [rememberQuarkKeeperNow]. */
private const val QUARK_KEEPER_CLOCK_TICK_MILLIS = 60_000L

/**
 * The wall clock, refreshed once a minute.
 *
 * The dashboard's contract carries no clock and the app's shared 1 Hz clock is not reachable from
 * here, but "today" and the remaining-time figure both have to move: a value read once at composition
 * would still say "3 h 12 min left" at 23:50. One tick a minute matches the module's own resolution —
 * the alarms, the deadline node and the check-in log are all whole minutes — so a faster tick would
 * buy nothing but recompositions.
 */
@Composable
internal fun rememberQuarkKeeperNow(): Long {
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(QUARK_KEEPER_CLOCK_TICK_MILLIS)
            nowMillis = System.currentTimeMillis()
        }
    }
    return nowMillis
}

/** `21:04` for a stored instant, at the minute precision the whole module is measured in. */
@Composable
internal fun quarkKeeperClockLabel(millis: Long): String {
    val (hour, minute) = QuarkKeeperClock.minutePrecisionLabel(millis)
    return stringResource(R.string.quark_keeper_history_time, hour, minute)
}

/**
 * Where today stands, in one sentence.
 *
 * Defined once for the dashboard and the home card: the two screens summarise the same three states,
 * and a second copy of this `when` is exactly how they would end up disagreeing about a day the user
 * checked in from the alert overlay.
 *
 * The order matters — a disabled guard schedules nothing, so its countdown would be a promise the app
 * is not keeping, while an already-answered day is worth reporting even with the guard off.
 */
@Composable
internal fun quarkKeeperTodayStatusText(
    snapshot: QuarkKeeperSnapshot,
    checkedToday: Boolean,
    nowMillis: Long,
): String = when {
    !snapshot.settings.enabled -> stringResource(R.string.quark_keeper_today_disabled)
    checkedToday -> stringResource(
        R.string.quark_keeper_today_checked_in,
        quarkKeeperClockLabel(snapshot.today.checkedInAtMillis),
    )
    else -> stringResource(
        R.string.quark_keeper_today_pending,
        quarkKeeperRemainingLabel(QuarkKeeperClock.remainingUntilEndOfDay(nowMillis)),
    )
}

internal fun quarkKeeperTodayStatusIcon(snapshot: QuarkKeeperSnapshot, checkedToday: Boolean): ImageVector = when {
    !snapshot.settings.enabled -> Icons.Outlined.WarningAmber
    checkedToday -> Icons.Outlined.CheckCircle
    else -> Icons.Outlined.Schedule
}

/**
 * `21:04` for a minute of the day.
 *
 * Coerced to a clock range rather than to the model's `MAX_MINUTE_OF_DAY` window: this renders stored
 * values, and silently redisplaying 23:59 as 23:55 would hide the very thing the settings screen is
 * supposed to make visible.
 */
@Composable
internal fun quarkKeeperMinuteLabel(minuteOfDay: Int): String {
    val safeMinute = minuteOfDay.coerceIn(0, MAX_MINUTE_OF_CLOCK)
    return stringResource(R.string.quark_keeper_history_time, safeMinute / 60, safeMinute % 60)
}

/** "3 h 12 min" / "45 min" / "less than a minute" for a countdown. */
@Composable
internal fun quarkKeeperRemainingLabel(remaining: QuarkKeeperRemaining): String = when {
    remaining.totalMinutes <= 0 -> stringResource(R.string.quark_keeper_remaining_less_than_minute)
    remaining.hours <= 0 -> stringResource(R.string.quark_keeper_remaining_minutes, remaining.minutes)
    else -> stringResource(
        R.string.quark_keeper_remaining_hours_minutes,
        remaining.hours,
        remaining.minutes,
    )
}

/** "1 day" / "5 days" — the plural resource is the only place the unit is spelled out. */
@Composable
internal fun quarkKeeperDaysLabel(days: Int): String =
    pluralStringResource(R.plurals.quark_keeper_days_count, days, days)

private const val MAX_MINUTE_OF_CLOCK = 23 * 60 + 59

/** Short enough to feel like a response to the tap, long enough to be read as movement. */
private const val CONFIRMATION_ENTER_MILLIS = 280

/** The row grows into place from just under its own size; 1f would leave the motion to opacity alone. */
private const val CONFIRMATION_ENTER_SCALE = 0.92f
