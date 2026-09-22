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
import android.view.View
import android.view.WindowManager
import com.projectlumen.app.MainActivity
import com.projectlumen.app.ProjectLumenApplication
import com.projectlumen.app.R
import com.projectlumen.app.core.constants.NotificationIds
import com.projectlumen.app.core.services.ForegroundServiceController
import com.projectlumen.app.core.toast.LumenToast
import com.projectlumen.app.core.toast.LumenToastKind

/**
 * The top-level popup for a reminder that has to reach a user who is looking at another app.
 *
 * Every reminder feature routes its background presentation here through [LumenAlertPresenter] — a
 * reminder is only useful if it is seen, and a shade entry is not seen. The window is a
 * [WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY] card drawn over the current app, tappable to
 * open the app, and taken down by itself after a few seconds so it never becomes something the user
 * has to clear.
 *
 * A foreground service rather than a plain overlay, for the same two reasons
 * [com.projectlumen.app.core.services.QuarkKeeperAlertService] is one: Android 10+ only lets a
 * background app add an overlay window while it is in the foreground *as a service* — an overlay
 * posted from a broadcast receiver is silently dropped — and the card has to survive the process
 * being trimmed while it is on screen.
 *
 * The card itself is [LumenToast.createAlertCard], deliberately the same view the in-app toast uses:
 * a reminder should not look like a different product depending on whether the app happened to be
 * open. What this service adds is the reach (it draws from a background-started service, so the
 * window actually lands), the duration, and the tap target.
 *
 * The system notification is *not* replaced by this window. Every caller posts its notification
 * first and then asks for the popup, so a device where the overlay permission was never granted
 * still gets the reminder, and one where it was gets both.
 */
class LumenAlertOverlayService : Service() {

    private lateinit var app: ProjectLumenApplication
    private var alertView: View? = null
    private val dismissHandler = Handler(Looper.getMainLooper())

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
        val title = intent?.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { getString(R.string.app_name) }
        val message = intent?.getStringExtra(EXTRA_MESSAGE).orEmpty()
        val kind = resolveKind(intent?.getStringExtra(EXTRA_KIND))
        val autoDismissSeconds = intent
            ?.getIntExtra(EXTRA_AUTO_DISMISS_SECONDS, DEFAULT_AUTO_DISMISS_SECONDS)
            ?: DEFAULT_AUTO_DISMISS_SECONDS
        val promoted = ForegroundServiceController.promote(
            service = this,
            notificationId = NotificationIds.ALERT_OVERLAY,
            notificationProvider = { app.notifications.buildAlertOverlayForegroundNotification(title, message) },
            foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )
        if (!promoted) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (!showAlert(title, message, kind, autoDismissSeconds)) {
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        dismissHandler.removeCallbacksAndMessages(null)
        removeAlert()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun resolveKind(name: String?): LumenToastKind {
        return LumenToastKind.entries.firstOrNull { it.name == name } ?: LumenToastKind.INFO
    }

    private fun showAlert(
        title: String,
        message: String,
        kind: LumenToastKind,
        autoDismissSeconds: Int,
    ): Boolean {
        // A second reminder arriving while one is up replaces the card rather than stacking: the
        // newest reminder is the actionable one, and two cards at the same coordinates would only
        // obscure each other.
        removeAlert()
        val windowManager = getSystemService(WindowManager::class.java) ?: return false
        val metrics = LumenToast.layoutMetrics(this)
        val view = LumenToast.createAlertCard(this, title, message, kind).apply {
            // The card is the whole window, so making the root clickable makes the whole popup a tap
            // target. Nothing else in the window can receive input: FLAG_NOT_FOCUSABLE removes key
            // focus only, and taps outside the card fall through to the app underneath.
            isClickable = true
            setOnClickListener {
                openApp()
                stopSelf()
            }
            contentDescription = getString(R.string.alert_overlay_content_description, title, message)
        }
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
        val added = runCatching { windowManager.addView(view, params) }
            .onFailure(app::recordHandledFailure)
            .isSuccess
        if (!added) return false
        alertView = view
        if (autoDismissSeconds > 0) {
            dismissHandler.postDelayed({ stopSelf() }, autoDismissSeconds * 1000L)
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

    private fun removeAlert() {
        val view = alertView ?: return
        alertView = null
        runCatching { getSystemService(WindowManager::class.java).removeView(view) }
    }

    companion object {
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_MESSAGE = "message"
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_AUTO_DISMISS_SECONDS = "autoDismissSeconds"

        /** Long enough to be noticed by someone looking at another app, short enough to not nag. */
        const val DEFAULT_AUTO_DISMISS_SECONDS = 8

        /**
         * Returns false when the permission is missing or the platform refuses the background
         * start; both are outcomes the caller handles, not errors.
         */
        fun show(
            context: Context,
            title: String,
            message: String,
            kind: LumenToastKind,
            autoDismissSeconds: Int = DEFAULT_AUTO_DISMISS_SECONDS,
        ): Boolean {
            if (!Settings.canDrawOverlays(context)) return false
            val intent = Intent(context, LumenAlertOverlayService::class.java)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_MESSAGE, message)
                .putExtra(EXTRA_KIND, kind.name)
                .putExtra(EXTRA_AUTO_DISMISS_SECONDS, autoDismissSeconds)
            return ForegroundServiceController.start(context, intent)
        }
    }
}
