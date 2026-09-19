package com.projectlumen.app.core.services

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import com.projectlumen.app.R
import com.projectlumen.app.ui.theme.LumenCoralDark
import com.projectlumen.app.ui.theme.LumenOnCoralDark
import com.projectlumen.app.ui.theme.LumenOnSurfaceDark
import com.projectlumen.app.ui.theme.LumenOnSurfaceVariantDark
import com.projectlumen.app.ui.theme.LumenOutlineVariantDark
import com.projectlumen.app.ui.theme.LumenSurfaceContainerDark
import com.projectlumen.app.ui.theme.LumenTealDark

/**
 * Builds the forced-alert screen shown by [QuarkKeeperAlertService].
 *
 * Plain `android.view` widgets, never Compose: this view is attached to a
 * [android.view.WindowManager] from a [android.app.Service], which has no `LifecycleOwner` /
 * `SavedStateRegistryOwner` to host a composition and no `Activity` to own the saved-state and
 * back-press contracts a Compose tree expects.
 * [com.projectlumen.app.core.overlay.EyeProtectionOverlayService] is built the same way for the same
 * reason.
 *
 * Every string is resolved here rather than handed in pre-formatted, so the caller only passes the
 * values that drive the wording (how much of the day is left, the snooze length) and the text itself
 * stays in the resource files where it can be translated.
 */
internal object QuarkKeeperAlertOverlayView {

    /**
     * Near-opaque rather than fully opaque: the window is added with
     * [android.graphics.PixelFormat.TRANSLUCENT], and an alpha of 255 on a translucent window still
     * costs the compositor a full-screen blend every frame. 244 hides whatever is behind the alert
     * for practical purposes while keeping the surface cheap on the low-end devices this app targets.
     */
    private val SCRIM_COLOR = ColorUtils.setAlphaComponent(LumenSurfaceContainerDark.toArgb(), 244)

    fun create(
        context: Context,
        remainingText: String,
        snoozeAllowed: Boolean,
        snoozeMinutes: Int,
        snoozeCutoffMinuteOfDay: Int,
        onGoCheckIn: () -> Unit,
        onSnooze: () -> Unit,
        onMarkDone: () -> Unit,
    ): View {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(context, 24f), dp(context, 28f), dp(context, 24f), dp(context, 20f))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(context, 24f).toFloat()
                setColor(LumenSurfaceContainerDark.toArgb())
                setStroke(dp(context, 1f), LumenOutlineVariantDark.toArgb())
            }
            addView(
                label(
                    context = context,
                    text = context.getString(R.string.quark_keeper_overlay_title),
                    sizeSp = 24f,
                    color = LumenOnSurfaceDark.toArgb(),
                    bold = true,
                ),
            )
            addView(
                label(
                    context = context,
                    text = context.getString(R.string.quark_keeper_overlay_message, remainingText),
                    sizeSp = 16f,
                    color = LumenOnSurfaceVariantDark.toArgb(),
                    bold = false,
                ),
            )
            addView(
                actionButton(
                    context = context,
                    text = context.getString(R.string.quark_keeper_action_go),
                    style = ButtonStyle.FILLED,
                    enabled = true,
                    onClick = onGoCheckIn,
                ),
            )
            addView(
                actionButton(
                    context = context,
                    text = if (snoozeAllowed) {
                        context.getString(R.string.quark_keeper_action_snooze, snoozeMinutes)
                    } else {
                        // Past the cutoff the extra round could not be honoured before midnight, so the
                        // button stays visible but inert. Removing it would leave the user wondering
                        // whether the option had ever existed; leaving it live would promise a re-alert
                        // the guard has already decided not to arm. The cutoff is named so the rule
                        // reads as a clock time rather than as a broken button.
                        context.getString(
                            R.string.quark_keeper_overlay_snooze_blocked,
                            minuteOfDayLabel(context, snoozeCutoffMinuteOfDay),
                        )
                    },
                    style = ButtonStyle.OUTLINED,
                    enabled = snoozeAllowed,
                    onClick = onSnooze,
                ),
            )
            addView(
                actionButton(
                    context = context,
                    text = context.getString(R.string.quark_keeper_action_check_in),
                    style = ButtonStyle.TEXT,
                    enabled = true,
                    onClick = onMarkDone,
                ),
            )
        }

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(context, 20f), dp(context, 20f), dp(context, 20f), dp(context, 20f))
            setBackgroundColor(SCRIM_COLOR)
            // Consumes taps that land outside the card. The window is not FLAG_NOT_TOUCHABLE, so the
            // platform would swallow them regardless; being clickable keeps that explicit instead of
            // relying on the flag not being added by a later edit.
            isClickable = true
            addView(
                card,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
    }

    /** Three weights of action, which is all the alert needs; a fuller set would belong in a theme. */
    private enum class ButtonStyle { FILLED, OUTLINED, TEXT }

    private fun actionButton(
        context: Context,
        text: String,
        style: ButtonStyle,
        enabled: Boolean,
        onClick: () -> Unit,
    ): TextView {
        // A TextView, not a Button: the platform Button paints its own background tint that overrides
        // a programmatic background on most OEM skins, and there is no Material theme to inherit a
        // ButtonStyle from — the context here is the service, not a themed Activity.
        return TextView(context).apply {
            setText(text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(if (enabled) style.enabledTextColor() else DISABLED_TEXT_COLOR)
            gravity = Gravity.CENTER
            // 48dp is the minimum accessible touch target. This alert is answered one-handed, at
            // night, possibly half-asleep, so it is worth more than the visual weight needs.
            minHeight = dp(context, 48f)
            setPadding(dp(context, 16f), dp(context, 12f), dp(context, 16f), dp(context, 12f))
            background = style.background(context, enabled)
            isEnabled = enabled
            isClickable = true
            isFocusable = true
            if (enabled) setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(context, 12f) }
        }
    }

    private fun ButtonStyle.enabledTextColor(): Int {
        return when (this) {
            // Coral is this app's warning accent, and the alert exists because the deadline is close —
            // the primary action carries that urgency instead of reading as a neutral call to action.
            ButtonStyle.FILLED -> LumenOnCoralDark.toArgb()
            ButtonStyle.OUTLINED -> LumenOnSurfaceDark.toArgb()
            ButtonStyle.TEXT -> LumenTealDark.toArgb()
        }
    }

    private fun ButtonStyle.background(context: Context, enabled: Boolean): GradientDrawable? {
        return when (this) {
            ButtonStyle.FILLED -> GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(context, 14f).toFloat()
                setColor(if (enabled) LumenCoralDark.toArgb() else DISABLED_FILL_COLOR)
            }
            ButtonStyle.OUTLINED -> GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(context, 14f).toFloat()
                setColor(android.graphics.Color.TRANSPARENT)
                setStroke(
                    dp(context, 1f),
                    if (enabled) LumenOutlineVariantDark.toArgb() else DISABLED_FILL_COLOR,
                )
            }
            // The lowest-emphasis action is a bare label: giving it a surface would make the two ways
            // out of the alert look equally weighted, and this one has to stay the quietest.
            ButtonStyle.TEXT -> null
        }
    }

    private fun label(
        context: Context,
        text: String,
        sizeSp: Float,
        color: Int,
        bold: Boolean,
    ): TextView {
        return TextView(context).apply {
            setText(text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            setTextColor(color)
            gravity = Gravity.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(context, if (bold) 0f else 10f) }
        }
    }

    /**
     * "23:30" for a minute of the day. Reuses the guard's shared clock format rather than pulling in
     * a locale-aware formatter: the cutoff is a time the user picked in the same 24-hour style on the
     * settings screen, and an AM/PM rendering here would contradict that screen.
     */
    private fun minuteOfDayLabel(context: Context, minuteOfDay: Int): String {
        return context.getString(R.string.quark_keeper_history_time, minuteOfDay / 60, minuteOfDay % 60)
    }

    private fun dp(context: Context, value: Float): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }

    private val DISABLED_TEXT_COLOR = ColorUtils.setAlphaComponent(LumenOnSurfaceVariantDark.toArgb(), 120)
    private val DISABLED_FILL_COLOR = ColorUtils.setAlphaComponent(LumenOutlineVariantDark.toArgb(), 90)
}
