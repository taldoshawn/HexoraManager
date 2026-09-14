package com.hexora.manager.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Ink = Color(0xFF0A0A0D)
private val SurfaceDark = Color(0xFF111216)
private val SurfaceDarkHigh = Color(0xFF191A20)
private val Paper = Color(0xFFF7F8FB)
private val SurfaceLight = Color(0xFFFFFFFF)
private val Accent = Color(0xFF4D5BFF)
private val AccentLight = Color(0xFF6571FF)

private val DarkColors = darkColorScheme(
    primary = AccentLight,
    onPrimary = Color.White,
    background = Ink,
    onBackground = Color(0xFFF2F3F8),
    surface = SurfaceDark,
    onSurface = Color(0xFFF2F3F8),
    surfaceVariant = SurfaceDarkHigh,
    onSurfaceVariant = Color(0xFFC4C6D0),
    outline = Color(0xFF3A3B44),
    error = Color(0xFFFFB4AB),
)

private val LightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    background = Paper,
    onBackground = Color(0xFF15161A),
    surface = SurfaceLight,
    onSurface = Color(0xFF15161A),
    surfaceVariant = Color(0xFFEEEFF5),
    onSurfaceVariant = Color(0xFF5B5D67),
    outline = Color(0xFFD1D3DC),
    error = Color(0xFFBA1A1A),
)

@Composable
fun HexoraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
