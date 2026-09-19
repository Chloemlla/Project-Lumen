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
 * reconcile". That is also why the calls that need an `Application` — reconciling, raising the alert,
 * and the two day nodes — arrive as callbacks: this class deliberately holds no Android dependency,
 * exactly like the ViewModel that constructs it. The same goes for the launcher calls, which need a
 * `Context`.
 */
internal class ProjectLumenQuarkKeeperFeatureEntry(
    private val scope: CoroutineScope,
    private val reconcile: suspend () -> Unit,
    private val raiseAlert: suspend () -> Unit,
    private val fireReminderNode: suspend () -> Unit,
    private val fireDeadlineNode: suspend () -> Unit,
    private val launchCheckIn: () -> Boolean,
    private val openStore: () -> Unit,
    private val openWeb: () -> Unit,
    private val recordHandledFailure: (Throwable) -> Unit,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    private val _quarkUnavailable = MutableStateFlow(false)
    private val _returnConfirmation = MutableStateFlow(false)

    /**
     * True once the user asked to check in and Quark could not be opened.
     *
     * Held here rather than inside the dashboard so the question survives a recomposition, and so the
     * two answers the user is offered — install it, or use the web page — sit next to the action that
     * raised it rather than being decided inside it.
     */
    val quarkUnavailable: StateFlow<Boolean> = _quarkUnavailable.asStateFlow()

    /**
     * True while the user is being asked whether the trip to Quark ended in a check-in.
     *
     * Raised by [onForeground] and lowered by either answer. It is a question about one hand-off, not a
     * mode of the screen, so it cannot be derived from the stored stamp alone: the stamp outlives the
     * question by design (it is what the next foreground reads), and a card rebuilt from it on every
     * recomposition would come back after it had been answered.
     */
    val returnConfirmation: StateFlow<Boolean> = _returnConfirmation.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        writeSettings { current -> current.copy(enabled = enabled) }
    }

    /** A whole settings object, as the settings section hands it back; the store sanitizes it again. */
    fun setSettings(settings: QuarkKeeperSettings) {
        writeSettings { settings }
    }

    fun markCheckedIn() {
        guarded {
            val nowMillis = now()
            QuarkKeeperStore.markCheckedIn(nowMillis)
            // Any check-in settles the return question, whichever button raised it: a stamp left behind
            // would re-ask about a hand-off that is already answered on the next foreground.
            QuarkKeeperStore.clearAwaitingReturn(nowMillis)
            _returnConfirmation.value = false
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

    /**
     * The app reached the foreground; asks about a hand-off that may still be open.
     *
     * The Activity resuming is the only part of this cycle the app can honestly observe. The PRD's
     * "the user went back to the launcher" fires no callback — a launcher is another app and this one is
     * not told when its task is left — but leaving for Quark and coming back always resumes the host
     * Activity, so that is where the question is asked. On every other foreground the stamp is absent
     * and this is a read and nothing else.
     *
     * A stamp whose window has closed is dropped here rather than left for later: the spec's "silent
     * reset" has to happen on the same foreground that would otherwise have shown the card, or the
     * question would surface the next time the user opens the app for something unrelated. Nothing is
     * reconciled — the stamp arms nothing, and the guard's own alarms were never touched by the trip.
     */
    fun onForeground() {
        val nowMillis = now()
        guarded {
            val today = QuarkKeeperStore.currentToday(nowMillis)
            val open = today.awaitingReturn &&
                QuarkKeeperClock.isReturnConfirmationOpen(today.awaitingReturnAtMillis, nowMillis)
            _returnConfirmation.value = open
            if (today.awaitingReturn && !open) {
                QuarkKeeperStore.clearAwaitingReturn(nowMillis)
            }
        }
    }

    /**
     * The user answered "not yet": the card closes and the stamp goes.
     *
     * Deliberately the whole effect. The day is still open, so the guard stays armed and the user stays
     * one tap from checking in — the answer only ends the question. Dropping the stamp is what keeps it
     * ended: leaving it would raise the card again on the next foreground, which would read as the app
     * refusing to take no for an answer.
     */
    fun dismissReturnConfirmation() {
        guarded {
            _returnConfirmation.value = false
            QuarkKeeperStore.clearAwaitingReturn(now())
        }
    }

    /**
     * One more round of the alert after [QuarkKeeperSettings.snoozeMinutes].
     *
     * Unreachable from the UI, and kept only until the next milestone removes it. The live snooze is
     * the forced overlay's own button, which broadcasts `QuarkKeeperReceiver.ACTION_SNOOZE` into the
     * guard's receiver; that path owns the re-arm, the nag round and the alert window, none of which an
     * in-app call can see. Anything that needs a snooze must go through it rather than through here, or
     * the alert and the stored state will describe different days.
     */
    @Deprecated(
        message = "Dead entry point: nothing in the UI calls it. The live snooze is the forced " +
            "overlay's button, which broadcasts QuarkKeeperReceiver.ACTION_SNOOZE. Kept until the " +
            "next milestone.",
    )
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
        val launched = launchCheckIn()
        if (launched) {
            // Stamped only once the hand-off actually happened: a stamp from a launch that failed would
            // raise the return question on the next foreground for a trip the user never took. The stamp
            // is also why the launch must come first — it is the instant the return window is measured
            // from, and the window is only meaningful if the user really was sent away.
            guarded { QuarkKeeperStore.markAwaitingReturn(now()) }
        }
        _quarkUnavailable.value = !launched
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

    /**
     * Developer mode: run one reminder node now, gates and all.
     *
     * The gates are the reason the button is useful rather than a limitation of it. It exists so the
     * reminder can be seen without waiting for its time, and a version that fired while the guard was
     * off or the day was already answered would be demonstrating a state the guard can never actually
     * be in. Nothing is written here: the node's own path owns the notification and the reconcile.
     */
    fun triggerReminderNow() {
        guarded { fireReminderNode() }
    }

    /**
     * Developer mode: run the deadline node now, gates and all.
     *
     * Unlike the alarm that normally runs this node, it is not gated on the clock — the alert's whole
     * purpose is to be seen at 22:30, so a button that only worked after 22:30 would be useless for the
     * case it exists for. The two conditions it does honour are the day's own, and it spends the day's
     * nag round exactly as the real alert would. That is deliberate: the round records that the user was
     * interrupted, so afterwards the guard behaves as it does after any other round, rather than being
     * left in a state no real night produces.
     */
    fun triggerDeadlineNow() {
        guarded { fireDeadlineNode() }
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
