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
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.projectlumen.app.ProjectLumenApplication
import com.projectlumen.app.R
import com.projectlumen.app.core.constants.NotificationIds
import kotlin.math.max

class EyeProtectionOverlayService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var overlayView: View? = null
    private var removeAtMillis: Long = 0L
    private var countdownText: TextView? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as ProjectLumenApplication
        ServiceCompat.startForeground(
            this,
            NotificationIds.OVERLAY_FOREGROUND,
            app.notifications.buildOverlayForegroundNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )
        if (!Settings.canDrawOverlays(this)) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: getString(R.string.break_title)
        val message = intent?.getStringExtra(EXTRA_MESSAGE) ?: getString(R.string.break_message)
        val durationSeconds = intent?.getIntExtra(EXTRA_DURATION_SECONDS, 20)?.coerceIn(5, 300) ?: 20
        showOverlay(title, message, durationSeconds)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        removeOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showOverlay(title: String, message: String, durationSeconds: Int) {
        removeOverlay()
        removeAtMillis = System.currentTimeMillis() + durationSeconds * 1000L
        val windowManager = getSystemService(WindowManager::class.java)
        countdownText = TextView(this).apply {
            text = getString(R.string.overlay_countdown_seconds, durationSeconds)
            textSize = 56f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        val view = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.argb(232, 20, 24, 28))
            isClickable = true
            isFocusable = true
            systemUiVisibility = immersiveSystemUiFlags()
            addView(TextView(context).apply {
                text = title
                textSize = 28f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            })
            addView(TextView(context).apply {
                text = message
                textSize = 18f
                setTextColor(Color.rgb(226, 232, 240))
                gravity = Gravity.CENTER
                setPadding(0, 18, 0, 28)
            })
            addView(countdownText)
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT,
        )
        windowManager.addView(view, params)
        overlayView = view
        forceImmersive(view)
        tickCountdown()
    }

    private fun forceImmersive(view: View) {
        view.systemUiVisibility = immersiveSystemUiFlags()
        ViewCompat.getWindowInsetsController(view)?.let { controller ->
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
        }
    }

    private fun immersiveSystemUiFlags(): Int {
        return View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    private fun tickCountdown() {
        val remainingSeconds = max(0L, (removeAtMillis - System.currentTimeMillis() + 999L) / 1000L)
        countdownText?.text = getString(R.string.overlay_countdown_seconds, remainingSeconds.toInt())
        if (remainingSeconds <= 0L) {
            stopSelf()
            return
        }
        handler.postDelayed(::tickCountdown, 250L)
    }

    private fun removeOverlay() {
        val view = overlayView ?: return
        overlayView = null
        runCatching {
            getSystemService(WindowManager::class.java).removeView(view)
        }
    }

    companion object {
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_MESSAGE = "message"
        private const val EXTRA_DURATION_SECONDS = "durationSeconds"

        fun show(context: Context, title: String, message: String, durationSeconds: Int) {
            if (!Settings.canDrawOverlays(context)) return
            val intent = Intent(context, EyeProtectionOverlayService::class.java)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_MESSAGE, message)
                .putExtra(EXTRA_DURATION_SECONDS, durationSeconds)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
