package com.projectlumen.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.projectlumen.app.core.enums.AppThemeMode

private val LightColors = lightColorScheme(
    primary = LumenTeal,
    onPrimary = LumenOnPrimary,
    primaryContainer = LumenTealContainer,
    onPrimaryContainer = LumenOnTealContainer,
    secondary = LumenCoral,
    tertiary = LumenIndigo,
    surface = LumenSurface,
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
    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (darkTheme) {
        DarkColors
    } else {
        LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = LumenTypography,
        content = content,
    )
}
