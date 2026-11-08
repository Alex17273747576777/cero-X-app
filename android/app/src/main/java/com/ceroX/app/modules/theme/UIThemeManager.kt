package com.ceroX.app.modules.theme

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * UIThemeManager — Gestor de temas para Compose UI.
 *
 * Convierte los AppTheme del ThemeEngine en ColorScheme de Material3 y
 * aplica los estilos de navegación y animación correspondientes.
 */
class UIThemeManager(private val context: Context) {

    private var themeEngine: ThemeEngineModule? = null

    fun bindThemeEngine(engine: ThemeEngineModule) {
        themeEngine = engine
    }

    fun colorSchemeFor(theme: AppTheme, isDark: Boolean = true): ColorScheme {
        // AppTheme guarda los colores como texto ("#6200EE"), no como Int.
        // `Color(theme.primaryColor)` esperaba un Int y por eso no compilaba:
        // hay que interpretar el hexadecimal primero.
        val primary = hexToColor(theme.primaryColor)
        val secondary = hexToColor(theme.accentColor)
        val background = hexToColor(theme.backgroundColor)
        val surface = hexToColor(theme.surfaceColor)

        return if (isDark) {
            darkColorScheme(
                primary = primary,
                secondary = secondary,
                background = background,
                surface = surface,
                surfaceContainerHighest = hexToColor(theme.surfaceColor),
                onPrimary = Color.White,
                onSecondary = Color.White,
                onBackground = Color.White,
                onSurface = Color.White
            )
        } else {
            lightColorScheme(
                primary = primary,
                secondary = secondary,
                background = background,
                surface = surface,
                onPrimary = Color.White,
                onSecondary = Color.White,
                onBackground = Color.Black,
                onSurface = Color.Black
            )
        }
    }

    fun navigationStyleFor(theme: AppTheme): NavigationStyle = theme.navigationStyle

    fun animationStyleFor(theme: AppTheme): AnimationStyle = theme.animationStyle

    fun isLiquidGlassEnabled(): Boolean = themeEngine?.getLiquidGlassEnabled() ?: false

    fun getBlurRadius(): Float = themeEngine?.getBlurRadius() ?: 24f

    /**
     * Convierte un color en texto a Color de Compose.
     *
     * Acepta las tres formas que aparecen en los temas del ecosistema:
     *   "#RRGGBB"    (6 digitos, opaco)
     *   "#AARRGGBB"  (8 digitos, con alfa)
     *   "RRGGBB"     (sin almohadilla)
     *
     * Si el texto no es un color valido devuelve gris en vez de lanzar una
     * excepcion: un tema con un color mal escrito debe verse feo, no cerrar la app.
     */
    private fun hexToColor(value: String): Color {
        val limpio = value.trim().removePrefix("#")
        val conAlfa = when (limpio.length) {
            6 -> "FF$limpio"   // sin alfa explicito: opaco
            8 -> limpio
            else -> return Color.Gray
        }
        val numero = conAlfa.toLongOrNull(16) ?: return Color.Gray
        return Color(numero)
    }
}
