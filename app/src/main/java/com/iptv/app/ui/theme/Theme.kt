package com.iptv.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF7C4DFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3700B3),
    onPrimaryContainer = Color(0xFFEDE7F6),
    secondary = Color(0xFF00D2D3),
    onSecondary = Color(0xFF002021),
    secondaryContainer = Color(0xFF004D40),
    onSecondaryContainer = Color(0xFF80CBC4),
    tertiary = Color(0xFFFF7675),
    onTertiary = Color.White,
    background = Color(0xFF0C0E14),
    onBackground = Color(0xFFF1F2F6),
    surface = Color(0xFF141722),
    onSurface = Color(0xFFF1F2F6),
    surfaceVariant = Color(0xFF1F2335),
    onSurfaceVariant = Color(0xFFA4B0BE),
    surfaceContainer = Color(0xFF1A1D2B),
    surfaceContainerHigh = Color(0xFF23273A),
    surfaceContainerHighest = Color(0xFF2C324B),
    outline = Color(0xFF57606F),
    outlineVariant = Color(0xFF2F3542),
    error = Color(0xFFFF4757),
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6C5CE7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFECE7FC),
    onPrimaryContainer = Color(0xFF2E1065),
    secondary = Color(0xFF00A896),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCF2EC),
    onSecondaryContainer = Color(0xFF003830),
    tertiary = Color(0xFFEB4D4B),
    onTertiary = Color.White,
    background = Color(0xFFF8F9FA),
    onBackground = Color(0xFF1E272E),
    surface = Color.White,
    onSurface = Color(0xFF1E272E),
    surfaceVariant = Color(0xFFEDF0F5),
    onSurfaceVariant = Color(0xFF485460),
    surfaceContainer = Color(0xFFF1F3F6),
    surfaceContainerHigh = Color(0xFFE8EBF0),
    surfaceContainerHighest = Color(0xFFDFE4EA),
    outline = Color(0xFFBDC581),
    outlineVariant = Color(0xFFD2DAE2),
    error = Color(0xFFEA2027),
    onError = Color.White
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

