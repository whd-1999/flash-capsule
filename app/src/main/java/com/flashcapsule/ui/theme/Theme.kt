package com.flashcapsule.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Purple = Color(0xFF6C4AB6)
private val PurpleDark = Color(0xFFB79CF0)

private val LightColors = lightColorScheme(
    primary = Purple,
    secondary = Color(0xFF625B71),
    background = Color(0xFFF7F5FB),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEDE7F6),
)

private val DarkColors = darkColorScheme(
    primary = PurpleDark,
    secondary = Color(0xFFCCC2DC),
    background = Color(0xFF141218),
    surface = Color(0xFF1D1B20),
    surfaceVariant = Color(0xFF2B2930),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
        content = content,
    )
}
