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
 *
 * The check-in confirmation is the one place this view does not report a tap immediately. The other
 * two buttons call back as they are pressed; "I have checked in" plays its animation first and then
 * calls back, because the alert closes on the callback and a window that leaves at the instant of the
 * tap would give the user who just answered a full-screen interrupt nothing back.
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
        remainingMinutes: Int,
        snoozeAllowed: Boolean,
        snoozeMinutes: Int,
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
        }
        card.addView(
            label(
                context = context,
                text = context.getString(R.string.quark_keeper_overlay_title),
                sizeSp = 24f,
                color = LumenOnSurfaceDark.toArgb(),
                bold = true,
            ),
        )
        card.addView(
            label(
                context = context,
                text = context.getString(R.string.quark_keeper_overlay_message, remainingText),
                sizeSp = 16f,
                color = LumenOnSurfaceVariantDark.toArgb(),
                bold = false,
            ),
        )
        card.addView(
            actionButton(
                context = context,
                text = context.getString(R.string.quark_keeper_action_go),
                style = ButtonStyle.FILLED,
                enabled = true,
                onClick = onGoCheckIn,
            ),
        )
        card.addView(
            actionButton(
                context = context,
                text = if (snoozeAllowed) {
                    context.getString(R.string.quark_keeper_action_snooze, snoozeMinutes)
                } else {
                    // Past the cutoff the extra round could not be honoured before midnight, so the
                    // button stays visible but inert. Hiding it outright would leave the user
                    // wondering whether the option had ever existed, and a live button would promise a
                    // re-alert the guard has already decided not to arm — so the alternative is
                    // disabled, and says why. The figure is the time left in the day rather than the
                    // cutoff itself: at the cutoff the day is what is running out.
                    context.getString(R.string.quark_keeper_snooze_rejected, remainingMinutes)
                },
                style = ButtonStyle.OUTLINED,
                enabled = snoozeAllowed,
                onClick = onSnooze,
            ),
        )
        card.addView(
            actionButton(
                context = context,
                text = context.getString(R.string.quark_keeper_action_check_in),
                style = ButtonStyle.TEXT,
                enabled = true,
                // The only button whose tap is reported late: the confirmation replaces the card
                // first and `onMarkDone` runs when it has been on screen long enough to read.
                onClick = { playCheckInConfirmation(context, card, onMarkDone) },
            ),
        )

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

    /**
     * Replaces the alert with the check-in confirmation, then reports the tap.
     *
     * The guard's other two buttons close the alert as they are pressed, which is right for them — one
     * hands the user to another app, the other buys time. This one is the user telling the guard it can
     * stand down, and the alert closing on the same frame would mean a full-screen interrupt answered
     * with no acknowledgement at all. So the card is swapped for the confirmation, the confirmation
     * animates in, and only then does `onMarkDone` run and the window go away.
     *
     * Swapping the content rather than drawing over it is also what makes a double tap impossible: the
     * buttons are gone before the first animation frame is drawn, so there is no second tap to consume
     * and no need to disable anything.
     *
     * The dismissal is held for [SUCCESS_HOLD_MILLIS] after the entrance finishes. An animation that
     * ends by deleting itself is not feedback; the check has to be readable before the alert leaves.
     */
    private fun playCheckInConfirmation(context: Context, card: LinearLayout, onMarkDone: () -> Unit) {
        card.removeAllViews()
        val confirmation = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            // A glyph rather than a drawable so it scales with the text under it; the app ships no
            // standalone check asset, and this alert has no themed Activity to borrow one from.
            addView(label(context, "✓", 52f, LumenTealDark.toArgb(), bold = true))
            addView(
                label(
                    context = context,
                    text = context.getString(R.string.quark_keeper_success_title),
                    sizeSp = 24f,
                    color = LumenOnSurfaceDark.toArgb(),
                    bold = true,
                ),
            )
            addView(
                label(
                    context = context,
                    text = context.getString(R.string.quark_keeper_success_message),
                    sizeSp = 16f,
                    color = LumenOnSurfaceVariantDark.toArgb(),
                    bold = false,
                ),
            )
        }
        card.addView(confirmation)

        confirmation.alpha = 0f
        confirmation.scaleX = SUCCESS_ENTER_SCALE
        confirmation.scaleY = SUCCESS_ENTER_SCALE
        confirmation.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(SUCCESS_ENTER_MILLIS)
            .start()

        // Posted on the card, not the confirmation: the card is the view that already has a window, and
        // this delay is the only thing standing between the tap and the guard being told.
        card.postDelayed(onMarkDone, SUCCESS_ENTER_MILLIS + SUCCESS_HOLD_MILLIS)
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

    private fun dp(context: Context, value: Float): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }

    /** Long enough to read as motion rather than as a jump cut, short enough not to feel like a delay. */
    private const val SUCCESS_ENTER_MILLIS = 260L

    /** Held after the entrance finishes, so the check is readable before the alert leaves. */
    private const val SUCCESS_HOLD_MILLIS = 850L

    /** Starts just under full size; 1f would leave the whole entrance to opacity alone. */
    private const val SUCCESS_ENTER_SCALE = 0.82f

    private val DISABLED_TEXT_COLOR = ColorUtils.setAlphaComponent(LumenOnSurfaceVariantDark.toArgb(), 120)
    private val DISABLED_FILL_COLOR = ColorUtils.setAlphaComponent(LumenOutlineVariantDark.toArgb(), 90)
}
