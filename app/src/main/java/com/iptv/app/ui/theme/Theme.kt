package com.iptv.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF6C5CE7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4834D4),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF00CEC9),
    onSecondary = Color.Black,
    background = Color(0xFF0F111A),
    onBackground = Color(0xFFECEFF1),
    surface = Color(0xFF1A1C29),
    onSurface = Color(0xFFECEFF1),
    surfaceVariant = Color(0xFF26293D),
    onSurfaceVariant = Color(0xFFB0B3C7),
    error = Color(0xFFFF5252)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6C5CE7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E3FF),
    onPrimaryContainer = Color(0xFF1E1464),
    secondary = Color(0xFF00B894),
    onSecondary = Color.White,
    background = Color(0xFFF8F9FA),
    onBackground = Color(0xFF212529),
    surface = Color.White,
    onSurface = Color(0xFF212529),
    surfaceVariant = Color(0xFFEDF0F5),
    onSurfaceVariant = Color(0xFF495057),
    error = Color(0xFFD63031)
)

@Composable
fun IptvTheme(
    darkTheme: Boolean = true, // Default to sleek TV/Media dark mode
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
