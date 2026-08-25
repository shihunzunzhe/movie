package com.earthvideo.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = White,
    primaryContainer = PrimarySoft,
    onPrimaryContainer = PrimaryDeep,
    secondary = PrimaryLight,
    onSecondary = White,
    background = PageBg,
    onBackground = TextPrimary,
    surface = CardBg,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = DividerStrong,
    outlineVariant = Divider,
    error = HotRed,
    onError = White,
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryLight,
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF00497D),
    onPrimaryContainer = Color(0xFFD3E5FF),
    secondary = PrimaryLight,
    onSecondary = Color(0xFF003258),
    background = Color(0xFF0F1419),
    onBackground = Color(0xFFE1E2E6),
    surface = Color(0xFF1A2027),
    onSurface = Color(0xFFE1E2E6),
    surfaceVariant = Color(0xFF262D36),
    onSurfaceVariant = Color(0xFFB0B8C1),
    outline = Color(0xFF3A4450),
    outlineVariant = Color(0xFF2D3540),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

@Composable
fun EarthVideoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            window.navigationBarColor = if (darkTheme) Color(0xFF1A2027).toArgb() else CardBg.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = darkTheme
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}