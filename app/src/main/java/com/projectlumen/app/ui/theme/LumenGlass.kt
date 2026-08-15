package com.projectlumen.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorFilter
import com.kyant.backdrop.effects.vibrancy

/**
 * The active [LayerBackdrop] captured by [LumenGlassHost]. Null outside the host, in which case
 * [Modifier.lumenGlass] degrades to a plain translucent surface.
 */
val LocalLumenBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/**
 * Root host for the liquid-glass effect. Draws a full-screen theme gradient into a [LayerBackdrop]
 * and provides it to descendants so any surface can render frosted glass via [Modifier.lumenGlass].
 */
@Composable
fun LumenGlassHost(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val backdrop = rememberLayerBackdrop()
    Box(modifier = modifier.fillMaxSize()) {
        val scheme = MaterialTheme.colorScheme
        val background = remember(scheme) {
            Brush.verticalGradient(
                colors = listOf(
                    scheme.primary.copy(alpha = 0.55f),
                    scheme.primaryContainer.copy(alpha = 0.45f),
                    scheme.tertiaryContainer.copy(alpha = 0.40f),
                    scheme.surface,
                ),
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .layerBackdrop(backdrop)
                .background(background),
        )
        CompositionLocalProvider(LocalLumenBackdrop provides backdrop) {
            content()
        }
    }
}

/**
 * Frosted-glass surface that samples the [LocalLumenBackdrop] captured behind it. Falls back to a
 * translucent tinted surface when the glass host is not present (e.g. previews, API < 31).
 */
@Composable
fun Modifier.lumenGlass(
    shape: Shape = RectangleShape,
    blurRadius: Float = 14f,
    tint: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.30f),
): Modifier {
    val backdrop = LocalLumenBackdrop.current
    if (backdrop == null) {
        return this.clip(shape).background(tint)
    }
    // Hoist the whole chain so its elements stay referentially stable across recompositions.
    // drawBackdrop wraps `shape` in a fresh ShapeProvider per call and DrawBackdropElement
    // compares it by identity, so a rebuilt chain would re-run update()/RenderEffect rebuild
    // every recomposition. The explicit receiver type keeps blur/colorFilter resolvable.
    val glassModifier = remember(backdrop, shape, blurRadius, tint) {
        val effects: BackdropEffectScope.() -> Unit = {
            blur(blurRadius)
            colorFilter(ColorFilter.tint(tint))
        }
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = effects,
        )
    }
    return this.clip(shape).then(glassModifier)
}

/**
 * Liquid-glass dock surface for the floating bottom navigation bar. Uses vibrancy for a saturated
 * backdrop with a subtle tint. Falls back to a translucent tinted surface without the glass host.
 */
@Composable
fun Modifier.lumenDockGlass(
    shape: Shape = RoundedCornerShape(50.dp),
    blurRadius: Dp = 14.dp,
    tint: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
): Modifier {
    val backdrop = LocalLumenBackdrop.current
    if (backdrop == null) {
        return this.clip(shape).background(tint)
    }
    val glassModifier = remember(backdrop, shape, blurRadius, tint) {
        val effects: BackdropEffectScope.() -> Unit = {
            vibrancy()
            blur(blurRadius.toPx())
        }
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = effects,
            onDrawSurface = {
                drawRect(tint)
            },
        )
    }
    return this.clip(shape).then(glassModifier)
}
