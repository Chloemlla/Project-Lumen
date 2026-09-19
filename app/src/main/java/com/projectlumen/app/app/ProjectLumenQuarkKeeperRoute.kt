package com.projectlumen.app.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.projectlumen.app.R
import com.projectlumen.app.core.quarkkeeper.QuarkKeeperCalendar
import com.projectlumen.app.core.time.todayKey

/**
 * The nav-host entry point for the guard's dashboard.
 *
 * `QuarkKeeperDashboardScreen` is a pure function of its arguments; this is the only place that knows
 * where they come from — the app's state store for the snapshot, the live permission sweep, and the
 * ViewModel for the actions. The cycle and the statistics are built here because the dashboard takes
 * them prebuilt: both are anchored on "today", and one caller-side computation is what keeps the grid,
 * the figures and the check-in buttons describing the same day.
 */
@Composable
internal fun QuarkKeeperRoute(
    uiState: ProjectLumenUiState,
    viewModel: ProjectLumenViewModel,
) {
    val context = LocalContext.current
    val snapshot = uiState.quarkKeeper
    val permissions = rememberPermissionRequirements()
    val quarkUnavailable by viewModel.quarkKeeperQuarkUnavailable.collectAsStateWithLifecycle()
    // Raised by the feature entry on the foreground that follows a hand-off to Quark, not derived here:
    // whether the user came back inside the window is a question about an instant, and the dashboard's
    // minute-resolution clock could not answer it — nor should the screen be the one to consume the
    // stamp, since it is not on screen at the moment the Activity resumes.
    val returnConfirmation by viewModel.quarkKeeperReturnConfirmation.collectAsStateWithLifecycle()
    // Ticks on its own minute, like the dashboard's clock and for the same reason: `nowMillis` from the
    // app's shared 1 Hz clock would rebuild the statistics below once a second, and the module has no
    // resolution finer than a minute to justify it.
    val nowMillis = rememberQuarkKeeperNow()
    val todayDateKey = remember(nowMillis) { todayKey(nowMillis) }
    val anchorDateKey = remember(snapshot.records) { QuarkKeeperCalendar.anchorDateKey(snapshot.records) }
    val cycle = remember(snapshot.records, todayDateKey, anchorDateKey) {
        QuarkKeeperCalendar.buildCycle(snapshot.records, todayDateKey, anchorDateKey)
    }
    val stats = remember(snapshot.records, todayDateKey, anchorDateKey) {
        QuarkKeeperCalendar.buildStats(snapshot.records, todayDateKey, anchorDateKey)
    }
    // Confirmation of the tap the user just made. It is deliberately not derived from the stored state:
    // a dashboard re-entered after a check-in made from the alert overlay has nothing to confirm, and a
    // message that outlived its tap would read as part of the page rather than as an answer to it. So it
    // lives and dies with this screen, and any other action clears it first.
    var sessionMessage by remember { mutableStateOf<String?>(null) }
    val checkedInMessage = stringResource(R.string.quark_keeper_success_message)
    val undoneMessage = stringResource(R.string.quark_keeper_undo_message)

    QuarkKeeperDashboardScreen(
        snapshot = snapshot,
        cycle = cycle,
        stats = stats,
        permissions = permissions,
        onToggleEnabled = { enabled ->
            sessionMessage = null
            viewModel.setQuarkKeeperEnabled(enabled)
        },
        onMarkDone = {
            sessionMessage = checkedInMessage
            viewModel.markQuarkKeeperCheckedIn()
        },
        onUndo = {
            sessionMessage = undoneMessage
            viewModel.undoQuarkKeeperCheckIn()
        },
        onOpenQuark = viewModel::openQuarkKeeperCheckIn,
        onRequestExactAlarm = { openExactAlarmSettings(context) },
        onRequestOverlay = { openOverlaySettings(context) },
        onRequestBatteryOptimization = { openBatteryOptimizationSettings(context) },
        onOpenNotificationSettings = { openAppNotificationSettings(context) },
        sessionMessage = sessionMessage,
    )

    if (quarkUnavailable) {
        QuarkKeeperQuarkMissingDialog(
            onInstall = viewModel::openQuarkKeeperStoreListing,
            onOpenWeb = viewModel::openQuarkKeeperWebCheckIn,
            onDismiss = viewModel::dismissQuarkKeeperQuarkUnavailable,
        )
    }

    if (returnConfirmation) {
        QuarkKeeperReturnConfirmationDialog(
            // The dashboard's own check-in action, deliberately not a second path to the same write:
            // the card answers "did it go through", and the answer to that is the check-in the button
            // beside it performs. The session message is raised with it so the dashboard behind the
            // card acknowledges the tap the same way it would have if the button had been pressed.
            onConfirm = {
                sessionMessage = checkedInMessage
                viewModel.markQuarkKeeperCheckedIn()
            },
            onDismiss = viewModel::dismissQuarkKeeperReturnConfirmation,
        )
    }
}

/**
 * The question the app owes the user after it handed them to Quark and they came back.
 *
 * Centred and dismissible rather than the forced overlay the trip started from: the forced overlay is
 * a separate window this app put on top of everything, and it has already taken itself down — what is
 * left is a question about what the user did while they were away, and a question needs a way out that
 * is not an answer. Both ways out here are cheap, which is the point: "not yet" keeps the guard armed
 * and costs one tap, and back or a tap outside reads as the same thing rather than trapping the user in
 * a card they cannot close.
 *
 * The confirming button leads because the hand-off is what raised it: the user left for Quark in order
 * to check in, so "yes" is the answer that matches the intent that got them here.
 */
@Composable
private fun QuarkKeeperReturnConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quark_keeper_return_confirm_title)) },
        text = { Text(stringResource(R.string.quark_keeper_return_confirm_message)) },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.quark_keeper_return_confirm_done))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.quark_keeper_return_confirm_pending))
            }
        },
    )
}

/**
 * The answer to "open Quark now" on a device where it is not installed.
 *
 * A dialog rather than a silent redirect to the store: the user asked to *check in*, and the two ways
 * to do that without the app — install it, or use the web page — are a real choice that a one-tap
 * bounce to a store listing would make for them.
 */
@Composable
private fun QuarkKeeperQuarkMissingDialog(
    onInstall: () -> Unit,
    onOpenWeb: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quark_keeper_quark_missing_title)) },
        text = { Text(stringResource(R.string.quark_keeper_quark_missing_message)) },
        confirmButton = {
            Button(onClick = onInstall) {
                Text(stringResource(R.string.quark_keeper_quark_missing_install))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenWeb) {
                    Text(stringResource(R.string.quark_keeper_quark_missing_web))
                }
                OutlinedButton(onClick = onDismiss) {
                    Text(stringResource(R.string.quark_keeper_quark_missing_cancel))
                }
            }
        },
    )
}
