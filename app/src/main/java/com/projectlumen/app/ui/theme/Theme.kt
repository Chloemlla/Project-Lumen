package com.projectlumen.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
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
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = LumenTypography,
        content = content,
    )
}
