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
        val primary = Color(theme.primaryColor)
        val secondary = Color(theme.accentColor)
        val background = Color(theme.backgroundColor)
        val surface = Color(theme.surfaceColor)

        return if (isDark) {
            darkColorScheme(
                primary = primary,
                secondary = secondary,
                background = background,
                surface = surface,
                surfaceContainerHighest = Color(theme.surfaceColor),
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
}