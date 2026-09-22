package com.projectlumen.app.core.overlay

import android.content.Context
import android.provider.Settings
import com.chloemlla.lumen.crash.CrashBreadcrumbs
import com.projectlumen.app.core.toast.LumenToast
import com.projectlumen.app.core.toast.LumenToastKind

/**
 * The single gate that decides whether anything of this app's may be drawn over what the user is
 * looking at — a reminder ([present]) or the running timer's status ([presentStatus]).
 *
 * Two conditions have to hold, and both are deliberate:
 *
 * - **The app is not on screen.** With the app open, the reminder's own screen is already showing the
 *   same thing and a popup over it is noise. "Not on screen" is [LumenToast]'s own activity-tracked
 *   foreground flag, so this gate and the toast router can never disagree about what counts as
 *   background.
 * - **The overlay permission is granted.** It is the only thing that lets a window be drawn over
 *   another app, and it is the switch the user already owns — there is no separate setting for this.
 *
 * Everything else — which reminder, which colour, how long — belongs to the caller.
 */
object LumenAlertPresenter {

    /**
     * Whether the app was last asked to show the running status over another app. Only used to keep
     * [clearStatus] from tearing a service down on every tick while the app is on screen and no chip
     * was ever raised.
     */
    @Volatile
    private var statusRequested = false

    /**
     * Pops [message] over the current app when the app is in the background and the overlay
     * permission is held.
     *
     * @return true if a popup was raised. False covers both "not applicable" (app on screen, or no
     *   permission) and "refused" (the platform blocking the background service start); callers that
     *   must not lose the reminder post a notification as well, which is what every reminder path
     *   does. The refused case is the one that is worth knowing about, so only that one is recorded.
     */
    fun present(
        context: Context,
        title: String,
        message: String,
        kind: LumenToastKind = LumenToastKind.INFO,
        autoDismissSeconds: Int = LumenAlertOverlayService.ALERT_AUTO_DISMISS_SECONDS,
    ): Boolean {
        if (LumenToast.isAppForeground()) return false
        if (!Settings.canDrawOverlays(context)) return false
        val raised = LumenAlertOverlayService.show(context, title, message, kind, autoDismissSeconds)
        if (!raised) {
            CrashBreadcrumbs.record("Background alert overlay refused while foreground=false: $title")
        }
        return raised
    }

    /**
     * Mirrors the running status onto the current app, or takes it down when it does not apply.
     *
     * This is called on the timer's own tick rather than only when the status text changes, because
     * the chip has to *appear* when the app drops into the background, and no change in the text marks
     * that moment. Re-publishing identical text costs one in-process service start, and the card
     * updates in place — see [LumenAlertOverlayService].
     *
     * A refusal is not recorded, unlike [present]: a reminder is a one-off that would otherwise be
     * lost silently, whereas this is re-asked every second and so repairs itself.
     *
     * @return true if the chip is up.
     */
    fun presentStatus(context: Context, title: String, message: String): Boolean {
        if (LumenToast.isAppForeground() || !Settings.canDrawOverlays(context)) {
            clearStatus(context)
            return false
        }
        statusRequested = true
        return LumenAlertOverlayService.showStatus(context, title, message)
    }

    /** Takes the status chip down, at the end of a timer or when the app comes back on screen. */
    fun clearStatus(context: Context) {
        if (!statusRequested) return
        statusRequested = false
        LumenAlertOverlayService.dismiss(context)
    }
}
