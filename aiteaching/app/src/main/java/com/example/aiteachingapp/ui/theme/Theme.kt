package com.example.aiteachingapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary            = ClaudeOrange,
    onPrimary          = Color.White,
    secondary          = Color(0xFF10B981),
    background         = DarkBg,
    surface            = DarkSurface,
    surfaceVariant     = DarkSurface2,
    onBackground       = DarkText,
    onSurface          = DarkText,
    onSurfaceVariant   = DarkTextSecondary,
    outline            = DarkDivider,
    outlineVariant     = DarkSurface3,
)

@Composable
fun AITeachingTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
