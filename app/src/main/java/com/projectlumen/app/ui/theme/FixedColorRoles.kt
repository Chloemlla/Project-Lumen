package com.projectlumen.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Fixed accent roles used by undraw-style [com.projectlumen.app.ui.svg.DynamicColorImageVectors].
 *
 * Simplified (no Monet tonal palettes): map Material 3 light/dark scheme containers so
 * illustrations stay readable across dynamic color and template palettes.
 */
@Immutable
data class FixedColorRoles(
    val primaryFixed: Color,
    val primaryFixedDim: Color,
    val onPrimaryFixed: Color,
    val onPrimaryFixedVariant: Color,
    val secondaryFixed: Color,
    val secondaryFixedDim: Color,
    val onSecondaryFixed: Color,
    val onSecondaryFixedVariant: Color,
    val tertiaryFixed: Color,
    val tertiaryFixedDim: Color,
    val onTertiaryFixed: Color,
    val onTertiaryFixedVariant: Color,
) {
    companion object {
        @Stable
        fun fromColorSchemes(
            lightColors: ColorScheme,
            darkColors: ColorScheme,
        ): FixedColorRoles {
            return FixedColorRoles(
                primaryFixed = lightColors.primaryContainer,
                onPrimaryFixed = lightColors.onPrimaryContainer,
                onPrimaryFixedVariant = darkColors.primaryContainer,
                secondaryFixed = lightColors.secondaryContainer,
                onSecondaryFixed = lightColors.onSecondaryContainer,
                onSecondaryFixedVariant = darkColors.secondaryContainer,
                tertiaryFixed = lightColors.tertiaryContainer,
                onTertiaryFixed = lightColors.onTertiaryContainer,
                onTertiaryFixedVariant = darkColors.tertiaryContainer,
                primaryFixedDim = darkColors.primary,
                secondaryFixedDim = darkColors.secondary,
                tertiaryFixedDim = darkColors.tertiary,
            )
        }

        /**
         * Approximate fixed roles from a single active [ColorScheme] when separate light/dark
         * schemes are unavailable (template / dynamic single scheme).
         */
        @Stable
        fun fromActiveScheme(scheme: ColorScheme): FixedColorRoles {
            // Prefer stronger primary/secondary/tertiary as "dim" accents and containers
            // for softer fixed fills so undraw vectors track the active theme.
            return FixedColorRoles(
                primaryFixed = scheme.primaryContainer,
                primaryFixedDim = scheme.primary,
                onPrimaryFixed = scheme.onPrimaryContainer,
                onPrimaryFixedVariant = scheme.primaryContainer,
                secondaryFixed = scheme.secondaryContainer,
                secondaryFixedDim = scheme.secondary,
                onSecondaryFixed = scheme.onSecondaryContainer,
                onSecondaryFixedVariant = scheme.secondaryContainer,
                tertiaryFixed = scheme.tertiaryContainer,
                tertiaryFixedDim = scheme.tertiary,
                onTertiaryFixed = scheme.onTertiaryContainer,
                onTertiaryFixedVariant = scheme.tertiaryContainer,
            )
        }
    }
}

val LocalFixedColorRoles = staticCompositionLocalOf {
    FixedColorRoles.fromColorSchemes(
        lightColors = lightColorScheme(),
        darkColors = darkColorScheme(),
    )
}
