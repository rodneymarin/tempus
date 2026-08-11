package com.rodneymarin.tempus.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.rodneymarin.tempus.ui.theme.ThemeMode

import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = TempusPrimary,
    onPrimary = TempusOnPrimary,
    primaryContainer = TempusPrimaryContainer,
    onPrimaryContainer = TempusOnPrimaryContainer,
    background = Color.White,
    surface = Color.White,
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
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = androidx.compose.material3.Typography(),
        content = content,
    )
}
