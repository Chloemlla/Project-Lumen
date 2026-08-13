package com.projectlumen.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
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
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorFilter

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
    val shapeProvider = remember(shape) { { shape } }
    val glassEffects: BackdropEffectScope.() -> Unit = remember(blurRadius, tint) {
        val tintFilter = ColorFilter.tint(tint)
        { blur(blurRadius); colorFilter(tintFilter) }
    }
    if (backdrop == null) {
        return this.clip(shape).background(tint)
    }
    // Hoist the whole chain so its elements are referentially stable across recompositions.
    // drawBackdrop wraps `shape` in a fresh ShapeProvider per call, and DrawBackdropElement
    // compares shapeProvider by identity, so a rebuilt chain would re-run update()/RenderEffect
    // rebuild every recomposition even with stable lambdas.
    val glassModifier = remember(backdrop, shapeProvider, glassEffects) {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = shapeProvider,
            effects = glassEffects,
        )
    }
    return this.then(glassModifier)
}
