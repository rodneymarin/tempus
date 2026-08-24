package com.rodneymarin.tempus.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.rodneymarin.tempus.ui.theme.ThemeMode

private val LightColors = lightColorScheme(
    primary = TempusPrimary,
    onPrimary = TempusOnPrimary,
    primaryContainer = TempusPrimaryContainer,
    onPrimaryContainer = TempusOnPrimaryContainer,
    background = TempusLightBackground,
    surface = TempusLightBackground,
)
private val DarkColors = darkColorScheme(
    primary = TempusDarkPrimary,
    onPrimary = TempusDarkOnPrimary,
    primaryContainer = TempusDarkPrimaryContainer,
    onPrimaryContainer = TempusDarkOnPrimaryContainer,
)

@Composable
fun TempusTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = when {
        // Dynamic Color (Material You) — takes the accent from the user's wallpaper.
        // Requires Android 12+; falls back to the custom palette otherwise.
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val base = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            // Fondo ligeramente gris en claro para contraste con las cards blancas
            if (darkTheme) base else base.copy(background = TempusLightBackground, surface = TempusLightBackground)
        }
        else -> if (darkTheme) DarkColors else LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = TempusTypography,
        shapes = TempusShapes,
        content = content,
    )
}
