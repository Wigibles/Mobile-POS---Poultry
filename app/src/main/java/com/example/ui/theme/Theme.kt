package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = Color.White,
    primaryContainer = BrandPrimaryContainer,
    secondary = BrandSecondary,
    background = BackgroundLight,
    surface = SurfaceLight,
    surfaceVariant = SurfaceVariant,
    surfaceContainer = SurfaceContainer,
    surfaceContainerHigh = SurfaceContainerHigh,
    onBackground = TextDark,
    onSurface = TextDark,
    onSurfaceVariant = TextMuted,
    outline = BorderLight,
    outlineVariant = BorderLight.copy(alpha = 0.5f),
    error = ColorUnpaid
)

private val DarkColorScheme = darkColorScheme(
    primary = BrandPrimaryDark,
    onPrimary = BackgroundDark,
    primaryContainer = BrandPrimaryDark.copy(alpha = 0.18f),
    secondary = BrandSecondary,
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerDark,
    onBackground = TextLight,
    onSurface = TextLight,
    onSurfaceVariant = TextMutedDark,
    outline = BorderDark,
    error = ColorUnpaid
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
