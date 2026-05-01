package com.example.foodbridge.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary            = GreenPrimary,
    onPrimary          = SurfaceWhite,
    primaryContainer   = GreenContainer,
    onPrimaryContainer = GreenOnContainer,
    secondary          = GreenLight,
    onSecondary        = SurfaceWhite,
    background         = BackgroundLight,
    onBackground       = TextPrimary,
    surface            = SurfaceWhite,
    onSurface          = TextPrimary,
    surfaceVariant     = GreenContainer,
    outline            = DividerColor,
    error              = StatusExpired
)

private val DarkColorScheme = darkColorScheme(
    primary            = GreenLight,
    onPrimary          = DarkBackground,
    primaryContainer   = GreenDark,
    onPrimaryContainer = GreenContainer,
    background         = DarkBackground,
    onBackground       = SurfaceWhite,
    surface            = DarkSurface,
    onSurface          = SurfaceWhite,
    error              = StatusExpired
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(28.dp)
)

@Composable
fun FoodBridgeTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        shapes      = AppShapes,
        content     = content
    )
}
