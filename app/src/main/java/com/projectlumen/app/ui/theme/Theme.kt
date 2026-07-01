package com.projectlumen.app.ui.theme

import android.os.Build
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import com.projectlumen.app.core.enums.AppThemeMode

private val LightColors = lightColorScheme(
    primary = LumenTeal,
    onPrimary = LumenOnPrimary,
    primaryContainer = LumenTealContainer,
    onPrimaryContainer = LumenOnTealContainer,
    secondary = LumenCoral,
    onSecondary = LumenOnCoral,
    secondaryContainer = LumenCoralContainer,
    onSecondaryContainer = LumenOnCoralContainer,
    tertiary = LumenIndigo,
    onTertiary = LumenOnIndigo,
    tertiaryContainer = LumenIndigoContainer,
    onTertiaryContainer = LumenOnIndigoContainer,
    surface = LumenSurface,
    surfaceVariant = LumenSurfaceVariant,
    surfaceContainer = LumenSurfaceContainer,
    background = LumenBackground,
    outline = LumenOutline,
    outlineVariant = LumenOutlineVariant,
)

private val DarkColors = darkColorScheme(
    primary = LumenTealDark,
    onPrimary = LumenOnPrimaryDark,
    primaryContainer = LumenTealContainerDark,
    onPrimaryContainer = LumenOnTealContainerDark,
    secondary = LumenCoralDark,
    onSecondary = LumenOnCoralDark,
    secondaryContainer = LumenCoralContainerDark,
    onSecondaryContainer = LumenOnCoralContainerDark,
    tertiary = LumenIndigoDark,
    onTertiary = LumenOnIndigoDark,
    tertiaryContainer = LumenIndigoContainerDark,
    onTertiaryContainer = LumenOnIndigoContainerDark,
    surface = LumenSurfaceDark,
    surfaceVariant = LumenSurfaceVariantDark,
    surfaceContainer = LumenSurfaceContainerDark,
    background = LumenBackgroundDark,
    outline = LumenOutlineDark,
    outlineVariant = LumenOutlineVariantDark,
)

@Composable
fun ProjectLumenTheme(
    themeMode: AppThemeMode,
    useDynamicColors: Boolean = true,
    themePrimaryColor: String? = null,
    themeBackgroundColor: String? = null,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val baseColorScheme = when {
        useDynamicColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    val colorScheme = if (useDynamicColors) {
        baseColorScheme
    } else {
        baseColorScheme.applyTemplatePalette(
            primaryHex = themePrimaryColor,
            backgroundHex = themeBackgroundColor,
            darkTheme = darkTheme,
        )
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = LumenTypography,
        content = content,
    )
}

private fun ColorScheme.applyTemplatePalette(
    primaryHex: String?,
    backgroundHex: String?,
    darkTheme: Boolean,
): ColorScheme {
    val primarySeed = parseTemplateColor(primaryHex) ?: return this
    val backgroundSeed = parseTemplateColor(backgroundHex) ?: primarySeed

    val primary = if (darkTheme) {
        primarySeed.blend(Color.White, 0.28f)
    } else {
        primarySeed
    }
    val primaryContainer = if (darkTheme) {
        primarySeed.blend(Color.Black, 0.42f)
    } else {
        primarySeed.blend(Color.White, 0.82f)
    }
    val surface = if (darkTheme) {
        backgroundSeed.blend(Color.Black, 0.78f)
    } else {
        backgroundSeed.blend(Color.White, 0.92f)
    }
    val surfaceContainer = if (darkTheme) {
        backgroundSeed.blend(Color.Black, 0.70f)
    } else {
        backgroundSeed.blend(Color.White, 0.76f)
    }
    val surfaceVariant = if (darkTheme) {
        backgroundSeed.blend(Color.Black, 0.58f)
    } else {
        backgroundSeed.blend(Color.White, 0.62f)
    }
    val background = if (darkTheme) {
        backgroundSeed.blend(Color.Black, 0.84f)
    } else {
        backgroundSeed.blend(Color.White, 0.86f)
    }

    return copy(
        primary = primary,
        onPrimary = primary.contentColor(),
        primaryContainer = primaryContainer,
        onPrimaryContainer = primaryContainer.contentColor(),
        surface = surface,
        onSurface = surface.contentColor(),
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = surfaceVariant.contentColor(variant = true),
        surfaceContainer = surfaceContainer,
        background = background,
        onBackground = background.contentColor(),
        outline = primarySeed.blend(if (darkTheme) Color.White else Color.Black, if (darkTheme) 0.46f else 0.54f),
        outlineVariant = primarySeed.blend(surfaceVariant, 0.72f),
    )
}

private fun parseTemplateColor(hex: String?): Color? {
    if (hex.isNullOrBlank() || !hex.trim().startsWith("#")) return null
    return runCatching { Color(AndroidColor.parseColor(hex.trim())) }.getOrNull()
}

private fun Color.blend(target: Color, fraction: Float): Color {
    val amount = fraction.coerceIn(0f, 1f)
    return Color(
        red = red + (target.red - red) * amount,
        green = green + (target.green - green) * amount,
        blue = blue + (target.blue - blue) * amount,
        alpha = alpha + (target.alpha - alpha) * amount,
    )
}

private fun Color.contentColor(variant: Boolean = false): Color {
    return if (luminance() > if (variant) 0.48f else 0.42f) {
        Color(0xFF111318)
    } else {
        Color.White
    }
}
