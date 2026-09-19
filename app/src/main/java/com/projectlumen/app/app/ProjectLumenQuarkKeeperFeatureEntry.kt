package com.projectlumen.app.app

import com.projectlumen.app.core.quarkkeeper.QuarkKeeperClock
import com.projectlumen.app.core.quarkkeeper.QuarkKeeperSettings
import com.projectlumen.app.core.quarkkeeper.QuarkKeeperStore
import com.projectlumen.app.core.services.QuarkKeeperCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The guard's in-app actions, in the same shape as the other `*FeatureEntry` classes: the ViewModel
 * delegates to this, and this owns the write/read sequence of one feature so no call site has to.
 *
 * The store is the only writer and [QuarkKeeperCoordinator.reconcile] is the only thing that makes the
 * armed alarms and the posted notifications match it, so every action below is "write, then
 * reconcile". That is also why the two calls that need an `Application` — reconciling and raising the
 * alert — arrive as callbacks: this class deliberately holds no Android dependency, exactly like the
 * ViewModel that constructs it. The same goes for the launcher calls, which need a `Context`.
 */
internal class ProjectLumenQuarkKeeperFeatureEntry(
    private val scope: CoroutineScope,
    private val reconcile: suspend () -> Unit,
    private val raiseAlert: suspend () -> Unit,
    private val launchCheckIn: () -> Boolean,
    private val openStore: () -> Unit,
    private val openWeb: () -> Unit,
    private val recordHandledFailure: (Throwable) -> Unit,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    private val _quarkUnavailable = MutableStateFlow(false)

    /**
     * True once the user asked to check in and Quark could not be opened.
     *
     * Held here rather than inside the dashboard so the question survives a recomposition, and so the
     * two answers the user is offered — install it, or use the web page — sit next to the action that
     * raised it rather than being decided inside it.
     */
    val quarkUnavailable: StateFlow<Boolean> = _quarkUnavailable.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        writeSettings { current -> current.copy(enabled = enabled) }
    }

    /** A whole settings object, as the settings section hands it back; the store sanitizes it again. */
    fun setSettings(settings: QuarkKeeperSettings) {
        writeSettings { settings }
    }

    fun markCheckedIn() {
        guarded {
            QuarkKeeperStore.markCheckedIn(now())
            // Reconciling is what tears a still-open alert down, cancels tonight's remaining nodes and
            // re-arms tomorrow's. The in-app button owns no window, so there is nothing to dismiss here.
            reconcile()
        }
    }

    fun undoCheckIn() {
        guarded {
            val nowMillis = now()
            QuarkKeeperStore.undoCheckIn(QuarkKeeperStore.currentToday(nowMillis).dateKey, nowMillis)
            reconcile()
            // An undo after the deadline has to ask for the alert itself. Reconciliation stays silent
            // once today's nag round is above zero, and that round survives both the check-in and this
            // undo by design (see `QuarkKeeperStore.markCheckedIn`), so without this an in-app undo at
            // 23:00 would leave the day open and unannounced. Same two steps, in the same order, as
            // `QuarkKeeperReceiver`'s undo button.
            val settings = QuarkKeeperStore.snapshot().settings
            if (settings.enabled && QuarkKeeperCoordinator.deadlineReached(settings, nowMillis)) {
                raiseAlert()
            }
        }
    }

    /** One more round of the alert after [QuarkKeeperSettings.snoozeMinutes]. */
    fun snooze() {
        guarded {
            val nowMillis = now()
            val settings = QuarkKeeperStore.snapshot().settings
            // The cutoff is applied here for the same reason the alert's own button applies it: past it
            // there is no longer enough of the day left to postpone into, and a snooze that outlived the
            // deadline would skip the escalation the guard exists for.
            if (QuarkKeeperClock.isSnoozeAllowed(settings, nowMillis)) {
                QuarkKeeperStore.setSnooze(nowMillis + settings.snoozeMinutes * MILLIS_PER_MINUTE, nowMillis)
            }
            reconcile()
        }
    }

    fun openCheckIn() {
        _quarkUnavailable.value = !launchCheckIn()
    }

    fun openStoreListing() {
        _quarkUnavailable.value = false
        openStore()
    }

    fun openWebCheckIn() {
        _quarkUnavailable.value = false
        openWeb()
    }

    fun dismissQuarkUnavailable() {
        _quarkUnavailable.value = false
    }

    private fun writeSettings(transform: (QuarkKeeperSettings) -> QuarkKeeperSettings) {
        guarded {
            QuarkKeeperStore.updateSettings(transform)
            reconcile()
        }
    }

    /**
     * Runs one action, then reports a failure instead of throwing it into the caller's scope.
     *
     * The store writes through MMKV, which throws once initialization has failed, and an action must
     * not take the app down with it — a guard that cannot persist is a guard that stays off, which is
     * the safe direction.
     */
    private fun guarded(block: suspend () -> Unit) {
        scope.launch {
            runCatching { block() }.onFailure(recordHandledFailure)
        }
    }

    private companion object {
        private const val MILLIS_PER_MINUTE = 60_000L
    }
}
