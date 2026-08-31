package com.projectlumen.app.app

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Binds scroll target(s) to a real settings module.
 *
 * Anchors must wrap the module instead of sitting as zero-height siblings in [LumenPage]:
 * sibling zero-height boxes still consume `spacedBy` gaps and make section spacing uneven.
 *
 * Nest multiple calls when a module needs anchors from different typed maps.
 */
@Composable
internal fun <T> SettingsScrollAnchor(
    target: T,
    scrollState: ScrollState?,
    anchorPositions: MutableMap<T, Int>,
    content: @Composable () -> Unit,
) {
    SettingsScrollAnchors(
        targets = listOf(target),
        scrollState = scrollState,
        anchorPositions = anchorPositions,
        content = content,
    )
}

@Composable
internal fun <T> SettingsScrollAnchors(
    targets: List<T>,
    scrollState: ScrollState?,
    anchorPositions: MutableMap<T, Int>,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val topOffsetPx = remember(density) { with(density) { 96.dp.toPx().roundToInt() } }
    // Plain holder, not snapshot state: the dedupe check runs in the layout phase and must not
    // register a snapshot read there.
    val lastReportedPosition = remember { intArrayOf(Int.MIN_VALUE) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                val activeScrollState = scrollState ?: return@onGloballyPositioned
                val position = (
                    activeScrollState.value +
                        coordinates.positionInRoot().y.roundToInt() -
                        topOffsetPx
                    ).coerceAtLeast(0)
                if (lastReportedPosition[0] == position) return@onGloballyPositioned
                lastReportedPosition[0] = position
                targets.forEach { target ->
                    anchorPositions[target] = position
                }
            },
    ) {
        content()
    }
}
