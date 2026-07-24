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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import com.projectlumen.app.core.enums.AppThemeMode
import org.json.JSONObject

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
    themePaletteJson: String? = null,
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
            paletteJson = themePaletteJson,
        )
    }
    CompositionLocalProvider(
        LocalFixedColorRoles provides FixedColorRoles.fromActiveScheme(colorScheme),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = LumenTypography,
            shapes = LumenShapes,
            content = content,
        )
    }
}

private fun ColorScheme.applyTemplatePalette(
    primaryHex: String?,
    backgroundHex: String?,
    paletteJson: String?,
): ColorScheme {
    parseTemplatePalette(paletteJson)?.let { palette ->
        return copy(
            primary = palette.primary100,
            onPrimary = palette.primary100.contentColor(),
            primaryContainer = palette.primary300,
            onPrimaryContainer = palette.primary300.contentColor(),
            secondary = palette.accent100,
            onSecondary = palette.accent100.contentColor(),
            secondaryContainer = palette.accent200,
            onSecondaryContainer = palette.accent200.contentColor(),
            tertiary = palette.primary200,
            onTertiary = palette.primary200.contentColor(),
            tertiaryContainer = palette.primary300,
            onTertiaryContainer = palette.primary300.contentColor(),
            surface = palette.bg100,
            onSurface = palette.text100,
            surfaceVariant = palette.bg300,
            onSurfaceVariant = palette.text200,
            surfaceContainer = palette.bg200,
            background = palette.bg100,
            onBackground = palette.text100,
            outline = palette.text200,
            outlineVariant = palette.bg300,
        )
    }

    val primarySeed = parseTemplateColor(primaryHex) ?: return this
    val backgroundSeed = parseTemplateColor(backgroundHex) ?: primarySeed
    val darkTheme = backgroundSeed.luminance() < 0.42f

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

private data class TemplatePalette(
    val primary100: Color,
    val primary200: Color,
    val primary300: Color,
    val accent100: Color,
    val accent200: Color,
    val text100: Color,
    val text200: Color,
    val bg100: Color,
    val bg200: Color,
    val bg300: Color,
)

private fun parseTemplatePalette(json: String?): TemplatePalette? {
    if (json.isNullOrBlank()) return null
    return runCatching {
        val palette = JSONObject(json).optJSONObject("palette") ?: return null
        TemplatePalette(
            primary100 = palette.getTemplateColor("primary-100"),
            primary200 = palette.getTemplateColor("primary-200"),
            primary300 = palette.getTemplateColor("primary-300"),
            accent100 = palette.getTemplateColor("accent-100"),
            accent200 = palette.getTemplateColor("accent-200"),
            text100 = palette.getTemplateColor("text-100"),
            text200 = palette.getTemplateColor("text-200"),
            bg100 = palette.getTemplateColor("bg-100"),
            bg200 = palette.getTemplateColor("bg-200"),
            bg300 = palette.getTemplateColor("bg-300"),
        )
    }.getOrNull()
}

private fun JSONObject.getTemplateColor(key: String): Color {
    return parseTemplateColor(getString(key)) ?: error("Invalid template color: $key")
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
