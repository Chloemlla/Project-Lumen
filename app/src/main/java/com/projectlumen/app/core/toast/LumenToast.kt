package com.projectlumen.app.core.toast

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.projectlumen.app.R
import com.projectlumen.app.core.constants.NotificationIds
import com.projectlumen.app.core.services.NotificationChannels
import java.lang.ref.WeakReference
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val POST_NOTIFICATIONS_PERMISSION = "android.permission.POST_NOTIFICATIONS"

enum class LumenToastKind(
    val glyph: String,
    val accentColorLight: Int,
    val accentColorDark: Int,
    @StringRes val titleRes: Int,
) {
    INFO(
        glyph = "i",
        accentColorLight = Color.parseColor("#126B66"),
        accentColorDark = Color.parseColor("#8ED6D1"),
        titleRes = R.string.toast_title_info,
    ),
    WARNING(
        glyph = "!",
        accentColorLight = Color.parseColor("#B85C38"),
        accentColorDark = Color.parseColor("#FFB59A"),
        titleRes = R.string.toast_title_warning,
    ),
    TIMER(
        glyph = "T",
        accentColorLight = Color.parseColor("#525DAA"),
        accentColorDark = Color.parseColor("#C2C6FF"),
        titleRes = R.string.toast_title_timer,
    ),
    SUCCESS(
        glyph = "OK",
        accentColorLight = Color.parseColor("#126B66"),
        accentColorDark = Color.parseColor("#8ED6D1"),
        titleRes = R.string.toast_title_success,
    );

    fun accentColor(darkTheme: Boolean): Int = if (darkTheme) accentColorDark else accentColorLight

    /** Backward-compatible accent for rich-message callers. */
    val accentColor: Int get() = accentColorLight
}

/**
 * Adaptive toast aligned with the app card paradigm:
 * soft surface, 20dp corners, accent chip, responsive width for phone pages.
 */
object LumenToast {
    private const val SHORT_DURATION_MILLIS = 2_600L
    private const val LONG_DURATION_MILLIS = 4_200L

    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentActivity = WeakReference<Activity?>(null)
    @Volatile private var foreground = false
    private var foregroundView: View? = null
    private var overlayView: View? = null
    private var dismissRunnable: Runnable? = null

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
            dismissRunnable?.let(mainHandler::removeCallbacks)
            val activity = currentActivity.get()
            when {
                foreground && activity != null && !activity.isFinishing && !activity.isDestroyed -> {
                    showInActivity(activity, message, kind, duration, trailingIcon)
                }
                Settings.canDrawOverlays(appContext) -> {
                    showOverlay(appContext, message, kind, duration, trailingIcon)
                }
                else -> showFallbackNotification(appContext, message, kind)
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
            if (bold) {
                setSpan(StyleSpan(Typeface.BOLD), start, start + keyword.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    private fun showInActivity(
        activity: Activity,
        message: CharSequence,
        kind: LumenToastKind,
        duration: Long,
        trailingIcon: Boolean,
    ) {
        val root = activity.window.decorView as? ViewGroup
            ?: return showFallbackNotification(activity, message, kind)
        foregroundView?.let { runCatching { root.removeView(it) } }
        overlayView?.let {
            runCatching {
                activity.applicationContext.getSystemService(WindowManager::class.java)?.removeView(it)
            }
            overlayView = null
        }

        val metrics = ToastLayoutMetrics.from(activity)
        val view = createToastView(activity, message, kind, trailingIcon, metrics)
        val params = FrameLayout.LayoutParams(
            metrics.toastWidthPx,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL,
        ).apply {
            topMargin = metrics.topMarginPx
        }
        root.addView(view, params)
        foregroundView = view
        animateIn(view)
        scheduleDismiss(duration) {
            animateOut(view) {
                runCatching { root.removeView(view) }
                if (foregroundView === view) foregroundView = null
            }
        }
    }

    private fun showOverlay(
        context: Context,
        message: CharSequence,
        kind: LumenToastKind,
        duration: Long,
        trailingIcon: Boolean,
    ) {
        val windowManager = context.getSystemService(WindowManager::class.java) ?: return
        overlayView?.let { runCatching { windowManager.removeView(it) } }
        foregroundView = null

        val metrics = ToastLayoutMetrics.from(context)
        val view = createToastView(context, message, kind, trailingIcon, metrics)
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
        windowManager.addView(view, params)
        overlayView = view
        animateIn(view)
        scheduleDismiss(duration) {
            animateOut(view) {
                runCatching { windowManager.removeView(view) }
                if (overlayView === view) overlayView = null
            }
        }
    }

    private fun scheduleDismiss(duration: Long, action: () -> Unit) {
        val runnable = Runnable(action)
        dismissRunnable = runnable
        mainHandler.postDelayed(runnable, duration)
    }

    private fun animateIn(view: View) {
        view.alpha = 0f
        view.scaleX = 0.96f
        view.scaleY = 0.96f
        view.translationY = -dp(view.context, 18f)
        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(200L)
            .start()
    }

    private fun animateOut(view: View, endAction: () -> Unit) {
        view.animate()
            .alpha(0f)
            .scaleX(0.97f)
            .scaleY(0.97f)
            .translationY(-dp(view.context, 12f))
            .setDuration(160L)
            .withEndAction(endAction)
            .start()
    }

    private fun createToastView(
        context: Context,
        message: CharSequence,
        kind: LumenToastKind,
        trailingIcon: Boolean,
        metrics: ToastLayoutMetrics,
    ): View {
        val darkTheme = metrics.darkTheme
        val accent = kind.accentColor(darkTheme)
        val surface = if (darkTheme) Color.parseColor("#1A211E") else Color.parseColor("#FFFCFA")
        val surfaceSoft = if (darkTheme) Color.parseColor("#111815") else Color.parseColor("#F0F4F1")
        val outline = if (darkTheme) Color.parseColor("#404B47") else Color.parseColor("#C4CECA")
        val titleColor = accent
        val bodyColor = if (darkTheme) Color.parseColor("#E7EFEC") else Color.parseColor("#263331")
        val chipFill = ColorUtils.setAlphaComponent(accent, if (darkTheme) 48 else 28)
        val chipStroke = ColorUtils.setAlphaComponent(accent, if (darkTheme) 120 else 80)

        val corner = dp(context, 20f)
        val chipCorner = dp(context, 12f)
        val stroke = max(1, dp(context, 1f).toInt())

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(context, metrics.minHeightDp).toInt()
            setPadding(
                dp(context, 12f).toInt(),
                dp(context, 12f).toInt(),
                dp(context, 14f).toInt(),
                dp(context, 12f).toInt(),
            )
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(surface, surfaceSoft),
            ).apply {
                cornerRadius = corner
                setStroke(stroke, outline)
            }
            elevation = dp(context, if (darkTheme) 8f else 6f)
            clipToOutline = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            contentDescription = buildString {
                append(context.getString(kind.titleRes))
                append(". ")
                append(message)
            }
        }

        val iconChip = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = chipCorner
                setColor(chipFill)
                setStroke(stroke, chipStroke)
            }
            minimumWidth = dp(context, metrics.iconSizeDp).toInt()
            minimumHeight = dp(context, metrics.iconSizeDp).toInt()
        }
        val glyphView = TextView(context).apply {
            text = kind.glyph
            setTextSize(TypedValue.COMPLEX_UNIT_SP, metrics.iconTextSp)
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            setTextColor(accent)
            gravity = Gravity.CENTER
            includeFontPadding = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        iconChip.addView(
            glyphView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER,
            ),
        )
        val iconParams = LinearLayout.LayoutParams(
            dp(context, metrics.iconSizeDp).toInt(),
            dp(context, metrics.iconSizeDp).toInt(),
        )

        val textColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleView = TextView(context).apply {
            text = context.getString(kind.titleRes)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, metrics.titleSp)
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            setTextColor(titleColor)
            letterSpacing = 0.02f
            includeFontPadding = false
            maxLines = 1
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val messageView = TextView(context).apply {
            text = message
            setTextSize(TypedValue.COMPLEX_UNIT_SP, metrics.bodySp)
            setTextColor(bodyColor)
            maxLines = metrics.maxLines
            includeFontPadding = false
            setLineSpacing(dp(context, 1.5f), 1.05f)
        }
        textColumn.addView(
            titleView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        textColumn.addView(
            messageView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(context, 3f).toInt()
            },
        )
        val textParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(context, 12f).toInt()
            marginEnd = if (trailingIcon) dp(context, 8f).toInt() else 0
        }

        if (trailingIcon) {
            root.addView(textColumn, textParams)
            root.addView(iconChip, iconParams)
        } else {
            root.addView(iconChip, iconParams)
            root.addView(textColumn, textParams)
        }
        return root
    }

    private fun showFallbackNotification(context: Context, message: CharSequence, kind: LumenToastKind) {
        if (!canPostNotifications(context)) return
        val darkTheme = isDarkTheme(context)
        val notification = NotificationCompat.Builder(context, NotificationChannels.PROXIMITY)
            .setSmallIcon(R.drawable.ic_notification_lumen)
            .setContentTitle(context.getString(kind.titleRes))
            .setContentText(message.toString())
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.toString()))
            .setColor(kind.accentColor(darkTheme))
            .setPriority(
                if (kind == LumenToastKind.WARNING) {
                    NotificationCompat.PRIORITY_HIGH
                } else {
                    NotificationCompat.PRIORITY_DEFAULT
                },
            )
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
        return ContextCompat.checkSelfPermission(context, POST_NOTIFICATIONS_PERMISSION) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun isDarkTheme(context: Context): Boolean {
        val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return night == Configuration.UI_MODE_NIGHT_YES
    }

    private fun dp(context: Context, value: Float): Float {
        return value * context.resources.displayMetrics.density
    }

    private data class ToastLayoutMetrics(
        val toastWidthPx: Int,
        val topMarginPx: Int,
        val darkTheme: Boolean,
        val minHeightDp: Float,
        val iconSizeDp: Float,
        val iconTextSp: Float,
        val titleSp: Float,
        val bodySp: Float,
        val maxLines: Int,
    ) {
        companion object {
            fun from(context: Context): ToastLayoutMetrics {
                val density = context.resources.displayMetrics.density
                val screenWidthPx = context.resources.displayMetrics.widthPixels
                val screenWidthDp = screenWidthPx / density

                // Match page content rhythm: side gutters grow slightly on wider phones,
                // and toast width is clamped so it never looks like a full-bleed system banner.
                val horizontalGutterDp = when {
                    screenWidthDp < 360f -> 12f
                    screenWidthDp < 400f -> 14f
                    screenWidthDp < 480f -> 16f
                    else -> 20f
                }
                val maxContentWidthDp = when {
                    screenWidthDp < 360f -> 336f
                    screenWidthDp < 600f -> 420f
                    else -> 520f
                }
                val desiredWidthDp = min(screenWidthDp - horizontalGutterDp * 2f, maxContentWidthDp)
                val toastWidthPx = max((desiredWidthDp * density).roundToInt(), (280f * density).roundToInt())
                    .coerceAtMost(screenWidthPx - (horizontalGutterDp * 2f * density).roundToInt())

                val statusBarInset = resolveStatusBarInsetPx(context)
                val topMarginPx = statusBarInset + (10f * density).roundToInt()

                val compact = screenWidthDp < 360f
                return ToastLayoutMetrics(
                    toastWidthPx = toastWidthPx,
                    topMarginPx = topMarginPx,
                    darkTheme = isDarkTheme(context),
                    minHeightDp = if (compact) 56f else 60f,
                    iconSizeDp = if (compact) 34f else 36f,
                    iconTextSp = if (compact) 12f else 13f,
                    titleSp = if (compact) 11.5f else 12f,
                    bodySp = if (compact) 13.5f else 14.5f,
                    maxLines = if (compact) 3 else 4,
                )
            }

            private fun resolveStatusBarInsetPx(context: Context): Int {
                val activity = context as? Activity
                if (activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val insets = activity.window.decorView.rootWindowInsets
                        ?.getInsets(WindowInsets.Type.statusBars())
                    if (insets != null) return insets.top
                }
                val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
                if (resourceId > 0) {
                    return context.resources.getDimensionPixelSize(resourceId)
                }
                return (28f * context.resources.displayMetrics.density).roundToInt()
            }

            private fun isDarkTheme(context: Context): Boolean {
                val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                return night == Configuration.UI_MODE_NIGHT_YES
            }
        }
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

fun Context.showLumenToast(
    @StringRes messageRes: Int,
    kind: LumenToastKind = LumenToastKind.INFO,
    long: Boolean = false,
    trailingIcon: Boolean = false,
) {
    showLumenToast(getString(messageRes), kind, long, trailingIcon)
}
