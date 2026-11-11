package com.ceroX.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
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
 * Tema de cero-X.
 *
 * Antes redefinía la tipografía con `h1`, `h2`, `body1` y `body2`, nombres que
 * material3 eliminó. Se usa la tipografía compartida de Type.kt, que ya está en la
 * escala correcta, en vez de repetir la escala aquí.
 *
 * `colorSchemeOverride` es lo que hace que la pantalla de Diseño sirva para algo:
 * convierte la paleta elegida en el esquema con el que pinta Compose. Sin él, esta
 * función usaba siempre los colores fijos de Color.kt y la elección se guardaba sin
 * efecto visible —el conversor (`UIThemeManager.colorSchemeFor`) existía y no lo
 * llamaba nadie—. Null, que es el valor por defecto, deja los colores de siempre.
 *
 * Límite honesto: esto cambia los colores de Compose. Las pantallas en XML siguen
 * con el tema del sistema, porque los `?attr/` de un layout no leen de aquí.
 */
@Composable
fun CeroXTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorSchemeOverride: ColorScheme? = null,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = colorSchemeOverride ?: if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = CeroXTypography,
        shapes = CeroXShapes,
        content = content,
    )
}
