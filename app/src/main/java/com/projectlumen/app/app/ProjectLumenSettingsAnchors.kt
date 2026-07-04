package com.projectlumen.app.app

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun <T> SettingsScrollAnchor(
    target: T,
    scrollState: ScrollState?,
    anchorPositions: MutableMap<T, Int>,
) {
    val topOffsetPx = with(LocalDensity.current) { 96.dp.toPx().roundToInt() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.dp)
            .onGloballyPositioned { coordinates ->
                val activeScrollState = scrollState ?: return@onGloballyPositioned
                anchorPositions[target] = (
                    activeScrollState.value +
                        coordinates.positionInRoot().y.roundToInt() -
                        topOffsetPx
                    ).coerceAtLeast(0)
            },
    )
}
