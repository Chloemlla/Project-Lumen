package com.projectlumen.app.core.overlay

import android.content.Context
import android.provider.Settings
import com.chloemlla.lumen.crash.CrashBreadcrumbs
import com.projectlumen.app.core.toast.LumenToast
import com.projectlumen.app.core.toast.LumenToastKind

/**
 * The single gate that decides whether a reminder may pop over whatever the user is looking at.
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
        autoDismissSeconds: Int = LumenAlertOverlayService.DEFAULT_AUTO_DISMISS_SECONDS,
    ): Boolean {
        if (LumenToast.isAppForeground()) return false
        if (!Settings.canDrawOverlays(context)) return false
        val raised = LumenAlertOverlayService.show(context, title, message, kind, autoDismissSeconds)
        if (!raised) {
            CrashBreadcrumbs.record("Background alert overlay refused while foreground=false: $title")
        }
        return raised
    }
}
