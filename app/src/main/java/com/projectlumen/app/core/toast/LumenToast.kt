package com.projectlumen.app.core.toast

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.graphics.drawable.GradientDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.projectlumen.app.R
import com.projectlumen.app.core.constants.NotificationIds
import com.projectlumen.app.core.services.NotificationChannels
import java.lang.ref.WeakReference

private const val POST_NOTIFICATIONS_PERMISSION = "android.permission.POST_NOTIFICATIONS"

enum class LumenToastKind(
    val icon: String,
    val accentColor: Int,
    @StringRes val titleRes: Int,
) {
    INFO("i", Color.rgb(45, 212, 191), R.string.toast_title_info),
    WARNING("⚠", Color.rgb(251, 191, 36), R.string.toast_title_warning),
    TIMER("⏱", Color.rgb(96, 165, 250), R.string.toast_title_timer),
    SUCCESS("✓", Color.rgb(74, 222, 128), R.string.toast_title_success),
}

object LumenToast {
    private const val SHORT_DURATION_MILLIS = 2_600L
    private const val LONG_DURATION_MILLIS = 4_200L

    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentActivity = WeakReference<Activity?>(null)
    @Volatile private var foreground = false
    private var foregroundView: View? = null
    private var overlayView: View? = null

    fun install(application: Application) {
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                foreground = true
                currentActivity = WeakReference(activity)
            }

            override fun onActivityResumed(activity: Activity) {
                foreground = true
                currentActivity = WeakReference(activity)
            }

            override fun onActivityStopped(activity: Activity) {
                if (currentActivity.get() === activity) {
                    foreground = false
                    currentActivity = WeakReference(null)
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) {
                if (currentActivity.get() === activity) currentActivity = WeakReference(null)
            }
        })
    }

    fun show(
        context: Context,
        message: CharSequence,
        kind: LumenToastKind = LumenToastKind.INFO,
        long: Boolean = false,
        trailingIcon: Boolean = false,
    ) {
        val appContext = context.applicationContext
        val duration = if (long) LONG_DURATION_MILLIS else SHORT_DURATION_MILLIS
        mainHandler.post {
            val activity = currentActivity.get()
            if (foreground && activity != null && !activity.isFinishing) {
                showInActivity(activity, message, kind, duration, trailingIcon)
            } else if (Settings.canDrawOverlays(appContext)) {
                showOverlay(appContext, message, kind, duration, trailingIcon)
            } else {
                showFallbackNotification(appContext, message, kind)
            }
        }
    }

    fun richMessage(
        text: String,
        keyword: String,
        color: Int,
        bold: Boolean = true,
    ): CharSequence {
        val start = text.indexOf(keyword)
        if (start < 0) return text
        return SpannableString(text).apply {
            setSpan(ForegroundColorSpan(color), start, start + keyword.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (bold) setSpan(StyleSpan(Typeface.BOLD), start, start + keyword.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun showInActivity(
        activity: Activity,
        message: CharSequence,
        kind: LumenToastKind,
        duration: Long,
        trailingIcon: Boolean,
    ) {
        val root = activity.window.decorView as? ViewGroup ?: return showFallbackNotification(activity, message, kind)
        foregroundView?.let { runCatching { root.removeView(it) } }
        val view = createToastView(activity, message, kind, trailingIcon)
        val params = FrameLayout.LayoutParams(
            activity.resources.displayMetrics.widthPixels - activity.resources.displayMetrics.density.times(32f).toInt(),
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL,
        ).apply {
            topMargin = activity.resources.displayMetrics.density.times(24f).toInt()
        }
        root.addView(view, params)
        foregroundView = view
        view.alpha = 0f
        view.scaleX = 0.98f
        view.scaleY = 0.98f
        view.translationY = -28f
        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(210L)
            .start()
        mainHandler.postDelayed({
            view.animate().alpha(0f).scaleX(0.98f).scaleY(0.98f).translationY(-18f).setDuration(170L).withEndAction {
                runCatching { root.removeView(view) }
                if (foregroundView === view) foregroundView = null
            }.start()
        }, duration)
    }

    private fun showOverlay(
        context: Context,
        message: CharSequence,
        kind: LumenToastKind,
        duration: Long,
        trailingIcon: Boolean,
    ) {
        val windowManager = context.getSystemService(WindowManager::class.java)
        overlayView?.let { runCatching { windowManager.removeView(it) } }
        val view = createToastView(context, message, kind, trailingIcon)
        val params = WindowManager.LayoutParams(
            context.resources.displayMetrics.widthPixels - context.resources.displayMetrics.density.times(32f).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = context.resources.displayMetrics.density.times(36f).toInt()
        }
        windowManager.addView(view, params)
        overlayView = view
        mainHandler.postDelayed({
            runCatching { windowManager.removeView(view) }
            if (overlayView === view) overlayView = null
        }, duration)
    }

    private fun createToastView(
        context: Context,
        message: CharSequence,
        kind: LumenToastKind,
        trailingIcon: Boolean,
    ): View {
        val density = context.resources.displayMetrics.density
        val cornerRadius = 8 * density
        val strokeWidth = (1 * density).toInt().coerceAtLeast(1)
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding((0 * density).toInt(), 0, (14 * density).toInt(), 0)
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(
                Color.argb(248, 18, 24, 30),
                Color.argb(244, 27, 34, 42),
            )).apply {
                setCornerRadius(cornerRadius)
                setStroke(strokeWidth, Color.argb(190, 82, 92, 106))
            }
            elevation = 12 * density
            clipToOutline = true
        }
        val accentRail = View(context).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(
                Color.argb(255, Color.red(kind.accentColor), Color.green(kind.accentColor), Color.blue(kind.accentColor)),
                Color.argb(130, Color.red(kind.accentColor), Color.green(kind.accentColor), Color.blue(kind.accentColor)),
            )).apply {
                cornerRadii = floatArrayOf(cornerRadius, cornerRadius, 0f, 0f, 0f, 0f, cornerRadius, cornerRadius)
            }
        }
        val accentParams = LinearLayout.LayoutParams((4 * density).toInt().coerceAtLeast(4), LinearLayout.LayoutParams.MATCH_PARENT)
        val iconView = TextView(context).apply {
            text = kind.icon
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(kind.accentColor)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(42, Color.red(kind.accentColor), Color.green(kind.accentColor), Color.blue(kind.accentColor)))
                setStroke(strokeWidth, Color.argb(220, Color.red(kind.accentColor), Color.green(kind.accentColor), Color.blue(kind.accentColor)))
            }
        }
        val iconParams = LinearLayout.LayoutParams((34 * density).toInt(), (34 * density).toInt()).apply {
            leftMargin = (12 * density).toInt()
            topMargin = (14 * density).toInt()
        }
        val textColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (11 * density).toInt(), 0, (12 * density).toInt())
        }
        val titleView = TextView(context).apply {
            text = context.getString(kind.titleRes)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
            setTextColor(Color.argb(210, Color.red(kind.accentColor), Color.green(kind.accentColor), Color.blue(kind.accentColor)))
            includeFontPadding = false
        }
        val textView = TextView(context).apply {
            text = message
            textSize = 14.5f
            setTextColor(Color.rgb(244, 247, 251))
            maxLines = 3
            includeFontPadding = true
            setLineSpacing(1.5f * density, 1.0f)
        }
        textColumn.addView(titleView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        textColumn.addView(textView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = (4 * density).toInt()
        })
        val textParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            leftMargin = (10 * density).toInt()
            rightMargin = if (trailingIcon) (8 * density).toInt() else 0
        }
        row.minimumHeight = (62 * density).toInt()
        row.addView(accentRail, accentParams)
        if (trailingIcon) {
            row.addView(textColumn, textParams)
            row.addView(iconView, iconParams)
        } else {
            row.addView(iconView, iconParams)
            row.addView(textColumn, textParams)
        }
        return row
    }

    private fun showFallbackNotification(context: Context, message: CharSequence, kind: LumenToastKind) {
        if (!canPostNotifications(context)) return
        val notification = NotificationCompat.Builder(context, NotificationChannels.PROXIMITY)
            .setSmallIcon(R.drawable.ic_notification_lumen)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(message.toString())
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.toString()))
            .setColor(ContextCompat.getColor(context, android.R.color.holo_blue_light))
            .setPriority(if (kind == LumenToastKind.WARNING) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NotificationIds.GLOBAL_TOAST, notification)
        } catch (_: SecurityException) {
            return
        }
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, POST_NOTIFICATIONS_PERMISSION) == PackageManager.PERMISSION_GRANTED
    }
}

fun Context.showLumenToast(
    message: CharSequence,
    kind: LumenToastKind = LumenToastKind.INFO,
    long: Boolean = false,
    trailingIcon: Boolean = false,
) {
    LumenToast.show(this, message, kind, long, trailingIcon)
}
