package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.model.ThemePreference

private val StudioDarkColorScheme = darkColorScheme(
    primary = KhmerGold,
    onPrimary = Obsidian950,
    primaryContainer = KhmerGoldContainer,
    onPrimaryContainer = KhmerGoldLight,
    secondary = ElectricCyan,
    onSecondary = Obsidian950,
    secondaryContainer = ElectricCyanContainer,
    onSecondaryContainer = ElectricCyan,
    tertiary = VioletAccent,
    onTertiary = Obsidian950,
    tertiaryContainer = VioletContainer,
    onTertiaryContainer = Color(0xFFEDE9FE),
    background = Obsidian950,
    onBackground = InkLight,
    surface = Obsidian900,
    onSurface = InkLight,
    surfaceVariant = Slate850,
    onSurfaceVariant = InkMuted,
    surfaceContainerLowest = Obsidian950,
    surfaceContainerLow = Obsidian900,
    surfaceContainer = Slate850,
    surfaceContainerHigh = Slate800,
    surfaceContainerHighest = Slate700,
    outline = Slate700,
    outlineVariant = Color(0xFF202A42),
    error = RoseAlert,
    onError = Color.White,
    errorContainer = RoseContainer,
    onErrorContainer = Color(0xFFFFD9DF)
)

private val StudioLightColorScheme = lightColorScheme(
    primary = GoldLightPrimary,
    onPrimary = Color.White,
    primaryContainer = GoldLightContainer,
    onPrimaryContainer = Color(0xFF3A2000),
    secondary = CyanLightSecondary,
    onSecondary = Color.White,
    secondaryContainer = CyanLightContainer,
    onSecondaryContainer = Color(0xFF003642),
    tertiary = Color(0xFF6D28D9),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEDE9FE),
    onTertiaryContainer = Color(0xFF2E1065),
    background = Alabaster50,
    onBackground = InkDark,
    surface = Color.White,
    onSurface = InkDark,
    surfaceVariant = Alabaster100,
    onSurfaceVariant = InkDarkMuted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Alabaster50,
    surfaceContainer = Alabaster100,
    surfaceContainerHigh = Alabaster200,
    surfaceContainerHighest = Color(0xFFCBD5E1),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Alabaster200,
    error = Color(0xFFBE123C),
    onError = Color.White,
    errorContainer = Color(0xFFFFE4E6),
    onErrorContainer = Color(0xFF4C0519)
)

val StudioShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun KhmerDubberTheme(
    themePreference: ThemePreference = ThemePreference.DARK_STUDIO,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themePreference) {
        ThemePreference.DARK_STUDIO -> true
        ThemePreference.LIGHT_STUDIO -> false
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = if (darkTheme) StudioDarkColorScheme else StudioLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = StudioShapes,
        content = content
    )
}
