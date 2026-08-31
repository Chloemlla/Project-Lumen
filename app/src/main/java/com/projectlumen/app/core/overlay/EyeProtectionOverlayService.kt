package com.projectlumen.app.core.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.projectlumen.app.ProjectLumenApplication
import com.projectlumen.app.R
import com.projectlumen.app.core.constants.NotificationIds
import com.projectlumen.app.core.services.ForegroundServiceController
import kotlin.math.max

class EyeProtectionOverlayService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val countdownTicker = Runnable { tickCountdown() }
    private var overlayView: View? = null
    private var removeAtMillis: Long = 0L
    private var titleText: TextView? = null
    private var messageText: TextView? = null
    private var countdownText: TextView? = null
    private var renderedRemainingSeconds: Int = -1

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as ProjectLumenApplication
        if (!Settings.canDrawOverlays(this)) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val promoted = ForegroundServiceController.promote(
            service = this,
            notificationId = NotificationIds.OVERLAY_FOREGROUND,
            notificationProvider = { app.notifications.buildOverlayForegroundNotification() },
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
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: getString(R.string.break_title)
        val message = intent?.getStringExtra(EXTRA_MESSAGE) ?: getString(R.string.break_message)
        val durationSeconds = intent?.getIntExtra(EXTRA_DURATION_SECONDS, 20)?.coerceIn(5, 300) ?: 20
        if (!showOverlay(app, title, message, durationSeconds)) {
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        removeOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showOverlay(
        app: ProjectLumenApplication,
        title: String,
        message: String,
        durationSeconds: Int,
    ): Boolean {
        val now = System.currentTimeMillis()
        val requestedRemoveAt = now + durationSeconds * 1000L
        if (overlayView != null && removeAtMillis > now) {
            titleText?.text = title
            messageText?.text = message
            removeAtMillis = max(removeAtMillis, requestedRemoveAt)
            renderedRemainingSeconds = -1
            return true
        }
        removeOverlay()
        handler.removeCallbacks(countdownTicker)
        val windowManager = getSystemService(WindowManager::class.java) ?: return false
        val countdown = TextView(this).apply {
            text = getString(R.string.overlay_countdown_seconds, durationSeconds)
            textSize = 56f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        val heading = TextView(this).apply {
            text = title
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        val body = TextView(this).apply {
            text = message
            textSize = 18f
            setTextColor(Color.rgb(226, 232, 240))
            gravity = Gravity.CENTER
            setPadding(0, 18, 0, 28)
        }
        val view = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.argb(232, 20, 24, 28))
            isClickable = true
            addView(heading)
            addView(body)
            addView(countdown)
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            overlayWindowFlags(),
            PixelFormat.TRANSLUCENT,
        )
        val added = runCatching { windowManager.addView(view, params) }
            .onFailure(app::recordHandledFailure)
            .isSuccess
        if (!added) return false
        overlayView = view
        titleText = heading
        messageText = body
        countdownText = countdown
        removeAtMillis = requestedRemoveAt
        renderedRemainingSeconds = durationSeconds
        forceImmersive(view)
        tickCountdown()
        return true
    }

    private fun overlayWindowFlags(): Int {
        val modernFlags =
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            modernFlags
        } else {
            modernFlags or legacyFullscreenWindowFlags()
        }
    }

    @Suppress("DEPRECATION")
    private fun legacyFullscreenWindowFlags(): Int {
        return WindowManager.LayoutParams.FLAG_FULLSCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR
    }

    private fun forceImmersive(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.windowInsetsController?.let { controller ->
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            }
        } else {
            applyLegacyImmersiveFlags(view)
        }
    }

    @Suppress("DEPRECATION")
    private fun applyLegacyImmersiveFlags(view: View) {
        view.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    private fun tickCountdown() {
        val remainingSeconds = max(0L, (removeAtMillis - System.currentTimeMillis() + 999L) / 1000L)
        val secondsToRender = remainingSeconds.toInt()
        if (secondsToRender != renderedRemainingSeconds) {
            renderedRemainingSeconds = secondsToRender
            countdownText?.text = getString(R.string.overlay_countdown_seconds, secondsToRender)
        }
        if (remainingSeconds <= 0L) {
            stopSelf()
            return
        }
        handler.postDelayed(countdownTicker, 250L)
    }

    private fun removeOverlay() {
        val view = overlayView ?: return
        overlayView = null
        titleText = null
        messageText = null
        countdownText = null
        runCatching {
            getSystemService(WindowManager::class.java).removeView(view)
        }
    }

    companion object {
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_MESSAGE = "message"
        private const val EXTRA_DURATION_SECONDS = "durationSeconds"

        fun show(context: Context, title: String, message: String, durationSeconds: Int): Boolean {
            if (!Settings.canDrawOverlays(context)) return false
            val intent = Intent(context, EyeProtectionOverlayService::class.java)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_MESSAGE, message)
                .putExtra(EXTRA_DURATION_SECONDS, durationSeconds)
            return ForegroundServiceController.start(context, intent)
        }
    }
}
