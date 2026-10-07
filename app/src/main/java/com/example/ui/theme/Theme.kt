package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = AragonPrimary,
    onPrimary = AragonOnPrimary,
    primaryContainer = AragonPrimaryContainer,
    onPrimaryContainer = AragonOnPrimaryContainer,
    secondary = AragonSecondary,
    onSecondary = Color(0xFF022C22),
    tertiary = AragonTertiary,
    background = AragonObsidianBg,
    onBackground = AragonTextPrimary,
    surface = AragonSurface,
    onSurface = AragonTextPrimary,
    surfaceVariant = AragonSurfaceVariant,
    onSurfaceVariant = AragonTextSecondary,
    outline = AragonOutline,
    error = AragonError
)

@Composable
fun AragonTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
