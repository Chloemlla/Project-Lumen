package com.projectlumen.app.app

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.projectlumen.app.core.database.entities.TipTemplateEntity
import kotlin.math.max

internal val LocalLumenPageScrollState = staticCompositionLocalOf<ScrollState?> { null }

internal fun smartWrapDisplayText(value: String): String {
    val normalized = value.trim().ifBlank { "-" }
    if (normalized.length <= DISPLAY_WRAP_CHUNK_SIZE) return normalized

    val zeroWidthChar = ZERO_WIDTH_SPACE.first()
    val wrapped = StringBuilder(normalized.length + normalized.length / 2)
    var tokenLength = 0
    normalized.forEachIndexed { index, char ->
        wrapped.append(char)
        if (char.isWhitespace() || char == zeroWidthChar) {
            tokenLength = 0
        } else {
            tokenLength += 1
            if (tokenLength >= DISPLAY_WRAP_CHUNK_SIZE) {
                wrapped.append(ZERO_WIDTH_SPACE)
                tokenLength = 0
            }
        }
        val isBreakpoint = when (char) {
            '/', '?', '&', '=', '.', '-', '_', '@', ':' -> true
            else -> false
        }
        if (isBreakpoint) {
            wrapped.append(ZERO_WIDTH_SPACE)
            tokenLength = 0
            if (char == '/' && index >= 2 && normalized[index - 1] == '/' && normalized[index - 2] == ':') {
                wrapped.append(ZERO_WIDTH_SPACE)
            }
        }
    }
    return wrapped.toString()
}

@Composable
internal fun RowScope.SmallMetric(@StringRes labelRes: Int, value: String) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(LumenPreferenceShape)
            .background(lumenNestedContainerColor)
            .animateContentSize(animationSpec = spring(stiffness = 420f, dampingRatio = 0.82f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            stringResource(labelRes),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        AnimatedContent(
            targetState = value,
            transitionSpec = {
                (fadeIn(tween(150)) + slideInVertically(tween(150)) { it / 2 }) togetherWith
                    (fadeOut(tween(100)) + slideOutVertically(tween(100)) { -it / 2 })
            },
            label = "metricValue",
        ) { metricValue ->
            Text(
                smartWrapDisplayText(metricValue),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                softWrap = true,
            )
        }
    }
}

@Composable
internal fun MetricRow(@StringRes labelRes: Int, value: String) {
    MetricRow(stringResource(labelRes), value)
}

@Composable
internal fun MetricRow(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(LumenPreferenceShape)
            .background(lumenNestedContainerColor)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            label,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            softWrap = true,
        )
        AnimatedContent(
            targetState = value,
            modifier = Modifier.fillMaxWidth(),
            transitionSpec = {
                (fadeIn(tween(140)) + slideInVertically(tween(140)) { it / 2 }) togetherWith
                    (fadeOut(tween(100)) + slideOutVertically(tween(100)) { -it / 2 })
            },
            label = "metricRowValue",
        ) { metricValue ->
            Text(
                smartWrapDisplayText(metricValue),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                softWrap = true,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

private const val DISPLAY_WRAP_CHUNK_SIZE = 24
private const val ZERO_WIDTH_SPACE = "\u200B"

@Composable
internal fun ColorSwatch(color: Color, size: Dp = 44.dp) {
    val animatedColor by animateColorAsState(color, tween(180), label = "colorSwatch")
    Box(
        modifier = Modifier
            .size(size)
            .clip(LumenIconChipShape)
            .background(animatedColor),
    )
}

@Composable
internal fun TemplateColorSwatch(template: TipTemplateEntity) {
    ColorSwatch(templateBackgroundColor(template))
}

@Composable
internal fun LumenPage(horizontalAlignment: Alignment.Horizontal = Alignment.Start, content: @Composable ColumnScope.() -> Unit) {
    val pageTokens = rememberLumenUiTokens(LocalContext.current).page
    val fallbackScrollState = rememberScrollState()
    val scrollState = LocalLumenPageScrollState.current ?: fallbackScrollState
    // Android 16+ large screens ignore forced orientation/aspect locks. Keep content readable
    // with a centered max-width column and slightly larger horizontal gutters on wide pages.
    val configuration = LocalConfiguration.current
    val widthDp = configuration.screenWidthDp
    val horizontalPadding = when {
        widthDp >= 840 -> maxOf(pageTokens.contentPaddingStartDp, 24f)
        widthDp >= 600 -> maxOf(pageTokens.contentPaddingStartDp, 16f)
        else -> pageTokens.contentPaddingStartDp
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = pageTokens.maxContentWidthDp.dp)
                // No animateContentSize here: animating the height of the same node that owns
                // verticalScroll fights the scroll offset and drops frames whenever a card
                // expands mid-scroll. Individual cards animate their own size instead.
                .verticalScroll(scrollState)
                .padding(
                    PaddingValues(
                        start = horizontalPadding.dp,
                        top = pageTokens.contentPaddingTopDp.dp,
                        end = horizontalPadding.dp,
                        bottom = pageTokens.contentPaddingBottomDp.dp,
                    ),
                ),
            horizontalAlignment = horizontalAlignment,
            verticalArrangement = Arrangement.spacedBy(pageTokens.sectionGapDp.dp),
            content = content,
        )
    }
}
