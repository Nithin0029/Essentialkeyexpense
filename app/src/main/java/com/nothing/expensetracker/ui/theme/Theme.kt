package com.nothing.expensetracker.ui.theme

import android.app.Activity
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
import com.nothing.expensetracker.data.local.ThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = AppBackgroundDark,
    surface = AppSurfaceDark,
    surfaceVariant = AppSurfaceVariantDark,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = AppOnSurfaceVariantDark
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    background = AppBackgroundLight,
    surface = AppSurfaceLight,
    surfaceVariant = AppSurfaceVariantLight,
    onBackground = AppOnBackgroundLight,
    onSurface = AppOnBackgroundLight,
    onSurfaceVariant = AppOnSurfaceVariantLight
)

@Composable
fun EssentialExpenseTrackerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val baseScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // Keep the app's own black/white identity for backgrounds & text regardless of Material
    // You's wallpaper-derived tones — only the accent colors (primary, etc.) come from dynamic color.
    val colorScheme = if (darkTheme) {
        baseScheme.copy(
            background = AppBackgroundDark,
            surface = AppSurfaceDark,
            surfaceVariant = AppSurfaceVariantDark,
            onBackground = Color.White,
            onSurface = Color.White,
            onSurfaceVariant = AppOnSurfaceVariantDark
        )
    } else {
        baseScheme.copy(
            background = AppBackgroundLight,
            surface = AppSurfaceLight,
            surfaceVariant = AppSurfaceVariantLight,
            onBackground = AppOnBackgroundLight,
            onSurface = AppOnBackgroundLight,
            onSurfaceVariant = AppOnSurfaceVariantLight
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}