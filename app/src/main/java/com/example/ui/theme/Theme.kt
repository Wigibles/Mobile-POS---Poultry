package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = CoralPrimaryDark,
    secondary = CoralSecondary,
    background = BackgroundDark,
    surface = SurfaceDark,
    onPrimary = SurfaceDark,
    onBackground = TextLight,
    onSurface = TextLight,
    outline = BorderDark
)

private val LightColorScheme = lightColorScheme(
    primary = CoralPrimary,
    secondary = CoralSecondary,
    background = BackgroundLight,
    surface = SurfaceLight,
    onPrimary = SurfaceLight,
    onBackground = TextDark,
    onSurface = TextDark,
    outline = BorderLight
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Set to false to force our custom beautiful theme by default
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
