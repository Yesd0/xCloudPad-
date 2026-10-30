package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val XCloudPadMonochromeColorScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF1F1F1F),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFD4D4D4),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF262626),
    onSecondaryContainer = Color.White,
    tertiary = Color.White,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianCard,
    onSurfaceVariant = TextSecondary,
    error = Color(0xFFE5E5E5)
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = XCloudPadMonochromeColorScheme,
        typography = Typography,
        content = content
    )
}
