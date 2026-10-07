package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AmoledDarkColorScheme = darkColorScheme(
    primary = AmoledPrimary,
    onPrimary = AmoledOnPrimary,
    primaryContainer = AmoledPrimaryContainer,
    onPrimaryContainer = AmoledOnPrimaryContainer,
    secondary = AmoledAccent,
    onSecondary = Color(0xFF000000),
    secondaryContainer = AmoledCard,
    onSecondaryContainer = AmoledTextPrimary,
    tertiary = AmoledWarning,
    onTertiary = Color(0xFF000000),
    background = AmoledBlack,
    onBackground = AmoledTextPrimary,
    surface = AmoledSurface,
    onSurface = AmoledTextPrimary,
    surfaceVariant = AmoledCard,
    onSurfaceVariant = AmoledTextSecondary,
    outline = AmoledBorder,
    outlineVariant = AmoledDivider,
    error = AmoledError,
    onError = Color(0xFF000000)
)

@Composable
fun AragonTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AmoledDarkColorScheme,
        typography = Typography,
        content = content
    )
}
