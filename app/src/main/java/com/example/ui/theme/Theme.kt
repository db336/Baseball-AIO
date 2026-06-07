package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkStadiumColorScheme = darkColorScheme(
    primary = SleekPrimary,
    secondary = SleekPrimaryContainer,
    tertiary = SkyOutfield,
    background = Color(0xFF111214), // Dark sleek background
    surface = Color(0xFF1B1C1F),    // Dark sleek card surface
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onTertiary = Color.White,
    onBackground = Color(0xFFE2E2E6),
    onSurface = Color(0xFFE2E2E6),
    outline = Color(0xFF44474E)
)

private val LightStadiumColorScheme = lightColorScheme(
    primary = SleekPrimary,
    secondary = SleekPrimaryDark,
    tertiary = SkyOutfield,
    background = SleekBg,          // #F7F9FC
    surface = SleekWhite,         // #FFFFFF
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = SleekTextPrimary, // #1B1B1F
    onSurface = SleekTextPrimary,    // #1B1B1F
    outline = SleekBorderMedium,     // #C4C7CF
    outlineVariant = SleekBorderLight // #E1E2EC
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) {
        DarkStadiumColorScheme
    } else {
        // High Contrast Light Stadium also looks professional!
        LightStadiumColorScheme
    }

    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content
    )
}
