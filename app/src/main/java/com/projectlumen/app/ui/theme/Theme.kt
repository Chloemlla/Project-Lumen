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
    tertiary = LumenIndigo,
    surface = LumenSurface,
    surfaceVariant = LumenSurfaceVariant,
    surfaceContainer = LumenSurfaceContainer,
    background = LumenBackground,
)

private val DarkColors = darkColorScheme(
    primary = LumenTealDark,
    onPrimary = LumenOnPrimaryDark,
    primaryContainer = LumenTealContainerDark,
    onPrimaryContainer = LumenOnTealContainerDark,
    secondary = LumenCoralDark,
    tertiary = LumenIndigoDark,
    surface = LumenSurfaceDark,
    surfaceVariant = LumenSurfaceVariantDark,
    surfaceContainer = LumenSurfaceContainerDark,
    background = LumenBackgroundDark,
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
