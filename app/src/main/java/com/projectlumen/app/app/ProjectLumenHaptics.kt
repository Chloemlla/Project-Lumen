package com.projectlumen.app.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

// LongPress / TextHandleMove are the only universally available HapticFeedbackType
// constants across Compose versions; newer ones (Confirm, etc.) would risk the CI build.
@Composable
internal fun hapticClick(
    type: HapticFeedbackType = HapticFeedbackType.LongPress,
    onClick: () -> Unit,
): () -> Unit {
    val haptics = LocalHapticFeedback.current
    return {
        haptics.performHapticFeedback(type)
        onClick()
    }
}
