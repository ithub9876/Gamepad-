package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NeonPadDarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = ObsidianBlack,
    primaryContainer = CarbonElevated,
    onPrimaryContainer = NeonCyan,
    secondary = NeonAzure,
    onSecondary = Color.White,
    secondaryContainer = DeepSpaceNavy,
    onSecondaryContainer = NeonCyan,
    tertiary = TurboAmber,
    onTertiary = ObsidianBlack,
    background = ObsidianBlack,
    onBackground = TextPrimaryWhite,
    surface = DeepSpaceNavy,
    onSurface = TextPrimaryWhite,
    surfaceVariant = CarbonSurface,
    onSurfaceVariant = TextSecondarySlate,
    outline = DeckBorderIdle,
    error = AlertCrimson,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = NeonPadDarkColorScheme,
        typography = Typography,
        content = content
    )
}
