package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = PrimaryGreen,
    onPrimary = Color.White,
    primaryContainer = PrimaryGreenContainer,
    onPrimaryContainer = OnPrimaryGreenContainer,
    secondary = AccentSaffron,
    onSecondary = Color.White,
    secondaryContainer = AccentSaffronContainer,
    onSecondaryContainer = OnAccentSaffronContainer,
    tertiary = PrimaryGreenLight,
    onTertiary = Color.White,
    background = CreamBackground,
    onBackground = OnSurfaceDark,
    surface = PureWhiteSurface,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantWarm,
    onSurfaceVariant = OnSurfaceVariantMuted,
    error = NotEligibleRed,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimaryGreen,
    onPrimary = OnPrimaryGreenContainer,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = PrimaryGreenContainer,
    secondary = DarkSecondarySaffron,
    onSecondary = OnAccentSaffronContainer,
    secondaryContainer = OnAccentSaffronContainer,
    onSecondaryContainer = AccentSaffronContainer,
    background = DarkSurface,
    onBackground = Color(0xFFE6EAE7),
    surface = DarkSurfaceVariant,
    onSurface = Color(0xFFE6EAE7),
    surfaceVariant = Color(0xFF2B332E),
    onSurfaceVariant = Color(0xFFC2C9C4),
    error = Color(0xFFFFB4AB)
)

@Composable
fun SchemeSaathiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    largeTextMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val typography = buildSchemeSaathiTypography(largeTextMode = largeTextMode)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content
    )
}
