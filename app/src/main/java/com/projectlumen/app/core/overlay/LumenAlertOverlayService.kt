package com.projectlumen.app.core.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import com.projectlumen.app.MainActivity
import com.projectlumen.app.ProjectLumenApplication
import com.projectlumen.app.R
import com.projectlumen.app.core.constants.NotificationIds
import com.projectlumen.app.core.services.ForegroundServiceController
import com.projectlumen.app.core.toast.LumenToast
import com.projectlumen.app.core.toast.LumenToastKind

/**
 * The window that draws a reminder — or the running timer's status — over whatever app the user has in
 * front of them.
 *
 * Every reminder feature routes its background presentation here through [LumenAlertPresenter]: a
 * reminder is only useful if it is seen, and a shade entry is not seen. The window is a
 * [WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY] card drawn over the current app and tappable
 * to open the app, so that noticing it and acting on it are the same gesture.
 *
 * A foreground service rather than a plain overlay, for the same two reasons
 * [com.projectlumen.app.core.services.QuarkKeeperAlertService] is one: Android 10+ only lets a
 * background app add an overlay window while it is in the foreground *as a service* — an overlay
 * posted from a broadcast receiver is silently dropped — and the card has to survive the process
 * being trimmed while it is on screen.
 *
 * The card itself is [LumenToast.createAlertCard], deliberately the same view the in-app toast uses:
 * a reminder should not look like a different product depending on whether the app happened to be
 * open. What this service adds is the reach (it draws from a background-started service, so the window
 * actually lands), the duration, and the tap target.
 *
 * ## One window, two lifetimes
 *
 * The same slot carries two kinds of card, and only this service decides which is on screen, because
 * they would otherwise fight over the same coordinates:
 *
 * - An **alert** ([show]) is a reminder that arrived just now. It takes the slot, and after its few
 *   seconds it gives the slot back to whatever status was latched rather than closing.
 * - A **status** ([showStatus]) is the running timer, mirrored out of the notification shade because a
 *   shade entry is what users scroll past. It stays until [dismiss] or until the timer stops.
 *
 * The status is *latched*, not merely displayed: an alert that interrupts it restores it on expiry, so
 * a chip cannot be lost because a reminder happened to fire while it was up.
 *
 * The system notification is *not* replaced by this window. Every caller posts its notification first
 * and then asks for the popup, so a device where the overlay permission was never granted still gets
 * the reminder, and one where it was gets both.
 */
class LumenAlertOverlayService : Service() {

    private lateinit var app: ProjectLumenApplication
    private var card: LumenToast.AlertCard? = null
    private var cardKind: LumenToastKind? = null
    private var showingSticky = false

    /** The status an intervening alert has to give the slot back to. Null when none is latched. */
    private var stickyContent: AlertContent? = null

    private val dismissHandler = Handler(Looper.getMainLooper())
    private val dismissRunnable = Runnable { onAlertExpired() }

    override fun onCreate() {
        super.onCreate()
        app = application as ProjectLumenApplication
        // This service can be started by an alarm on a process that was just spun up, and the
        // foreground promotion below posts into a channel that may not exist yet on that process.
        app.notifications.ensureChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Without the permission there is no window to draw; the caller's notification is the
        // fallback, so this path stays quiet and just gives up the slot.
        if (!Settings.canDrawOverlays(this)) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        // Re-promoted on every start rather than once: each `startForegroundService` arms a fresh
        // deadline for this call, and re-posting the identical notification is inert thanks to
        // `setOnlyAlertOnce` and `FOREGROUND_SERVICE_IMMEDIATE`.
        if (!promote()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val content = contentFrom(intent)
        val sticky = intent?.getStringExtra(EXTRA_MODE) == MODE_STATUS
        if (sticky) {
            stickyContent = content
            val current = card
            when {
                // The status card is up: refresh its text in place. This runs on every tick, and
                // rebuilding the window that often would flicker where setting two strings does not.
                current != null && showingSticky && cardKind == content.kind -> {
                    current.applyContent(content)
                    return START_NOT_STICKY
                }
                // An alert is mid-display. It owns the slot until it expires, and then hands it back
                // to the status latched above — a reminder that arrives now outranks the timer's
                // progress, but must not cost the user the status they had.
                current != null -> return START_NOT_STICKY
            }
        }
        if (!showCard(content, sticky = sticky)) {
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        dismissHandler.removeCallbacks(dismissRunnable)
        removeCard()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Called when an alert's few seconds are up. The slot goes back to the latched status, or the
     * service ends when there is none.
     */
    private fun onAlertExpired() {
        val sticky = stickyContent
        if (sticky == null) {
            stopSelf()
            return
        }
        showCard(sticky, sticky = true)
    }

    private fun contentFrom(intent: Intent?): AlertContent {
        return AlertContent(
            title = intent?.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { getString(R.string.app_name) },
            message = intent?.getStringExtra(EXTRA_MESSAGE).orEmpty(),
            kind = resolveKind(intent?.getStringExtra(EXTRA_KIND)),
            autoDismissMillis = (intent?.getIntExtra(EXTRA_AUTO_DISMISS_SECONDS, ALERT_AUTO_DISMISS_SECONDS)
                ?: ALERT_AUTO_DISMISS_SECONDS).coerceAtLeast(0) * 1000L,
        )
    }

    private fun resolveKind(name: String?): LumenToastKind {
        return LumenToastKind.entries.firstOrNull { it.name == name } ?: LumenToastKind.INFO
    }

    private fun promote(): Boolean {
        return ForegroundServiceController.promote(
            service = this,
            notificationId = NotificationIds.ALERT_OVERLAY,
            notificationProvider = { app.notifications.buildAlertOverlayForegroundNotification() },
            foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )
    }

    private fun showCard(content: AlertContent, sticky: Boolean): Boolean {
        dismissHandler.removeCallbacks(dismissRunnable)
        val existing = card
        // The accent and glyph belong to the kind, and applyContent only rewrites text, so a card of
        // a different kind has to be rebuilt rather than re-labelled.
        if (existing != null && cardKind == content.kind) {
            existing.applyContent(content)
        } else {
            removeCard()
            val windowManager = getSystemService(WindowManager::class.java) ?: return false
            val metrics = LumenToast.layoutMetrics(this)
            val built = LumenToast.createAlertCard(this, content.title, content.message, content.kind)
            built.root.apply {
                // The card is the whole window, so making the root clickable makes the whole popup a
                // tap target. Nothing else in the window can receive input: FLAG_NOT_FOCUSABLE
                // removes key focus only, and taps outside the card fall through to the app
                // underneath — which is what keeps a card that stays for hours from blocking the app
                // it is drawn over.
                isClickable = true
                setOnClickListener {
                    // The card is taken down even when it is the sticky status: the tap opens this
                    // app, the chip has no business covering it, and the publisher raises it again on
                    // the next update if the app goes back to the background.
                    openApp()
                    stopSelf()
                }
            }
            built.applyContent(content)
            val params = WindowManager.LayoutParams(
                metrics.toastWidthPx,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = metrics.topMarginPx
            }
            val added = runCatching { windowManager.addView(built.root, params) }
                .onFailure(app::recordHandledFailure)
                .isSuccess
            if (!added) return false
            card = built
        }
        cardKind = content.kind
        showingSticky = sticky
        // A zero duration means the caller wants the card to stay until something else replaces it,
        // which is the same lifetime as a status.
        if (!sticky && content.autoDismissMillis > 0L) {
            dismissHandler.postDelayed(dismissRunnable, content.autoDismissMillis)
        }
        return true
    }

    /**
     * Opens the app. The overlay permission is what makes this legal from the background: an app
     * holding `SYSTEM_ALERT_WINDOW` is exempt from the Android 10+ background-activity-start
     * restriction, and the tap that got here was a real user interaction on this app's own window.
     */
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .setClass(this, MainActivity::class.java)
            .setPackage(packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        runCatching { startActivity(intent) }.onFailure(app::recordHandledFailure)
    }

    /**
     * Rewrites the card's text and its accessibility description together. The description is a
     * formatted string baked in when the window was built, so refreshing only the text would leave
     * TalkBack reading whatever the card said the first time it appeared.
     */
    private fun LumenToast.AlertCard.applyContent(content: AlertContent) {
        update(content.title, content.message)
        root.contentDescription = getString(
            R.string.alert_overlay_content_description,
            content.title,
            content.message,
        )
    }

    private fun removeCard() {
        val built = card ?: return
        card = null
        cardKind = null
        runCatching { getSystemService(WindowManager::class.java).removeView(built.root) }
    }

    private data class AlertContent(
        val title: String,
        val message: String,
        val kind: LumenToastKind,
        val autoDismissMillis: Long,
    )

    companion object {
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_MESSAGE = "message"
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_AUTO_DISMISS_SECONDS = "autoDismissSeconds"

        private const val MODE_ALERT = "alert"
        private const val MODE_STATUS = "status"

        /** Long enough to be noticed by someone looking at another app, short enough to not nag. */
        const val ALERT_AUTO_DISMISS_SECONDS = 8

        /**
         * Returns false when the permission is missing or the platform refuses the background
         * start; both are outcomes the caller handles, not errors.
         */
        fun show(
            context: Context,
            title: String,
            message: String,
            kind: LumenToastKind,
            autoDismissSeconds: Int = ALERT_AUTO_DISMISS_SECONDS,
        ): Boolean {
            if (!Settings.canDrawOverlays(context)) return false
            return start(context, title, message, kind, MODE_ALERT, autoDismissSeconds)
        }

        /**
         * Latches a card that stays until [dismiss]. Used for the running timer, which the user has
         * to be able to see without opening the shade — the whole reason the overlay permission is
         * worth holding.
         */
        fun showStatus(
            context: Context,
            title: String,
            message: String,
            kind: LumenToastKind = LumenToastKind.TIMER,
        ): Boolean {
            if (!Settings.canDrawOverlays(context)) return false
            return start(context, title, message, kind, MODE_STATUS, autoDismissSeconds = 0)
        }

        /**
         * Takes the window down. The service owns the slot, so this tears the service down rather
         * than asking it to clear one of its two kinds of card: a status is only ever dismissed when
         * the timer behind it has stopped, and an alert caught in that moment has nothing left to
         * announce.
         */
        fun dismiss(context: Context) {
            runCatching {
                context.applicationContext.stopService(Intent(context, LumenAlertOverlayService::class.java))
            }
        }

        private fun start(
            context: Context,
            title: String,
            message: String,
            kind: LumenToastKind,
            mode: String,
            autoDismissSeconds: Int,
        ): Boolean {
            val intent = Intent(context, LumenAlertOverlayService::class.java)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_MESSAGE, message)
                .putExtra(EXTRA_KIND, kind.name)
                .putExtra(EXTRA_MODE, mode)
                .putExtra(EXTRA_AUTO_DISMISS_SECONDS, autoDismissSeconds)
            return ForegroundServiceController.start(context, intent)
        }
    }
}
