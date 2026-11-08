package com.ceroX.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = CeroXPrimary,
    secondary = CeroXSecondary,
    background = CeroXDarkBackground,
    surface = CeroXDarkSurface,
)

private val LightColorScheme = lightColorScheme(
    primary = CeroXPrimary,
    secondary = CeroXSecondary,
    background = CeroXLightBackground,
    surface = CeroXLightSurface,
)

/**
 * Tema único de cero-X.
 *
 * Antes redefinía la tipografía con `h1`, `h2`, `body1` y `body2`, nombres que
 * material3 eliminó. Se usa la tipografía compartida de Type.kt, que ya está en
 * la escala correcta, en vez de repetir la escala aquí.
 */
@Composable
fun CeroXTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = CeroXTypography,
        shapes = CeroXShapes,
        content = content,
    )
}
