package com.ceroX.app.modules.theme

import com.ceroX.app.core.engine.ModuleHealth
import com.ceroX.app.core.engine.ModuleInterface

/**
 * ThemeEngineModule — cinco paletas de colores y dos preferencias de desenfoque.
 *
 * La cabecera lo llamaba «Motor de personalización de UI super personalizable» y
 * lo decía «Inspirado en» Kiroku, Kototoro, AniZen y namida-snapshots, con
 * efectos concretos (iOS 26 blur, haze glassmorphism, navigation pills, dynamic
 * palettes). Lo que hay en realidad es un `AppTheme` por diseño: cuatro colores en
 * hexadecimal y dos enums. Ni una línea de esos proyectos, y ninguno de esos
 * efectos está implementado —no hay desenfoque, no hay navegación por islas, no
 * hay paletas dinámicas sacadas del fondo de pantalla—.
 *
 * Los nombres se conservan porque describen el aire que cada paleta imita, y se
 * dice en cada `designSource` que la paleta es de cero-X. Se quita la cuarta
 * atribución, a `namidaco/namida-snapshots`: ese repositorio son 4 KB con builds
 * beta, no tiene UI que mirar, y el Namida real es una EULA propietaria que
 * prohíbe usar su código. No había nada de lo que inspirarse ni nada que atribuir.
 *
 * Estado honesto de lo que guarda:
 *
 *   • Los colores **sí** se pueden convertir a un `ColorScheme` de Material 3
 *     (`UIThemeManager.colorSchemeFor` ya lo hace), pero todavía nada los aplica:
 *     `CeroXTheme` usa los colores fijos de `ui/theme/Color.kt`. Conectar las dos
 *     piezas es el paso siguiente y está anotado en `docs/LIMPIEZA.md`.
 *   • `liquidGlassEnabled` y `glassBlurRadius` se guardan y se leen desde la
 *     pantalla de Diseño, y **ningún renderer los usa**: no hay desenfoque en la
 *     app. El healthCheck lo dice con `desenfoque_aplicado = false`.
 *   • Los enums `NavigationStyle` y `AnimationStyle` se guardan por tema y nada
 *     los interpreta.
 */
class ThemeEngineModule : ModuleInterface() {

    override val name = "theme-engine"
    override val version = "1.0.0-beta"
    override val dependencies: List<String> = emptyList()

    private val availableThemes = mutableListOf<AppTheme>()
    private var currentThemeId = "kiroku_liquid_glass"
    private var liquidGlassEnabled = true
    private var glassBlurRadius = 24f
    private var customPalette: String? = null

    override fun initialize(config: Map<String, Any>): Boolean {
        liquidGlassEnabled = config["liquid_glass"] as? Boolean ?: true
        glassBlurRadius = config["glass_blur_radius"] as? Float ?: 24f
        registerDefaultThemes()
        _initialized = true
        return true
    }

    override fun start(): Boolean {
        if (!_initialized) return false
        _started = true
        return true
    }

    override fun stop(): Boolean {
        _started = false
        return true
    }

    override fun healthCheck(): ModuleHealth {
        return ModuleHealth(
            name, version,
            if (_started) "healthy" else "stopped",
            mapOf(
                "available_themes" to availableThemes.size,
                "current_theme" to currentThemeId,
                "liquid_glass" to liquidGlassEnabled,
                "glass_blur_radius" to glassBlurRadius,
                "custom_palette" to ((customPalette != null)),
                // Los ids reales de las paletas, no una lista de marcas. Y el
                // hecho que faltaba: nada aplica el desenfoque.
                "paletas" to availableThemes.map { it.id },
                "desenfoque_aplicado" to false
            )
        )
    }

    private fun registerDefaultThemes() {
        availableThemes.addAll(listOf(
            AppTheme(
                id = "kiroku_liquid_glass",
                name = "Kiroku Liquid Glass",
                designSource = "Nahiro18/Kiroku (ReDantotsu)",
                description = "Índigo y cian sobre azul muy oscuro. Imita el aire de Kiroku; la paleta es de cero-X y el desenfoque que sugiere el nombre no está implementado.",
                primaryColor = "#6366F1",
                accentColor = "#22D3EE",
                backgroundColor = "#0F172A",
                surfaceColor = "#1E293B",
                glassEnabled = true,
                blurRadius = 24f,
                navigationStyle = NavigationStyle.PILL_BOTTOM,
                animationStyle = AnimationStyle.SPRING_60FPS
            ),
            AppTheme(
                id = "kototoro_compose_glass",
                name = "Kototoro Compose Glass",
                designSource = "Kototoro-app/Kototoro",
                description = "Violeta sobre gris azulado. Imita el aire de Kototoro; la paleta es de cero-X, sin código copiado.",
                primaryColor = "#8B5CF6",
                accentColor = "#A855F7",
                backgroundColor = "#111827",
                surfaceColor = "#1F2937",
                glassEnabled = true,
                blurRadius = 20f,
                navigationStyle = NavigationStyle.READER_PANELS,
                animationStyle = AnimationStyle.FLUID_COMPOSE
            ),
            AppTheme(
                id = "anizen_island_glassmorphism",
                name = "AniZen Island Glassmorphism",
                designSource = "salmanbappi/AniZen",
                description = "Cian y verde agua sobre negro azulado. Imita el aire de AniZen; la paleta es de cero-X, sin código copiado.",
                primaryColor = "#06B6D4",
                accentColor = "#14B8A6",
                backgroundColor = "#0B0F19",
                surfaceColor = "#111827",
                glassEnabled = true,
                blurRadius = 28f,
                navigationStyle = NavigationStyle.ISLAND_FEED,
                animationStyle = AnimationStyle.GLASSMORPHISM
            ),
            AppTheme(
                id = "media_card_grid",
                name = "Media en tarjetas",
                designSource = "cero-X (paleta propia)",
                // Antes esta paleta llevaba el nombre de la app de Namida y se
                // atribuía a su repositorio de snapshots, con una descripción que
                // prometía tarjetas unificadas, paletas dinámicas y un widget de
                // inicio. Ese repositorio son 4 KB de builds beta y Namida es una
                // EULA propietaria: no había fuente que atribuir. Los cuatro
                // colores los eligió este proyecto.
                description = "Rojo y ámbar sobre negro. Paleta propia de cero-X para música y vídeo en tarjetas.",
                primaryColor = "#EF4444",
                accentColor = "#F59E0B",
                backgroundColor = "#09090B",
                surfaceColor = "#18181B",
                glassEnabled = false,
                blurRadius = 0f,
                navigationStyle = NavigationStyle.CARD_GRID,
                animationStyle = AnimationStyle.SMOOTH_FADE
            ),
            AppTheme(
                id = "custom_user_defined",
                name = "Personalizado",
                designSource = "Usuario",
                description = "Diseño totalmente personalizable por el usuario",
                primaryColor = "#6366F1",
                accentColor = "#22D3EE",
                backgroundColor = "#0F172A",
                surfaceColor = "#1E293B",
                glassEnabled = false,
                blurRadius = 0f,
                navigationStyle = NavigationStyle.PILL_BOTTOM,
                animationStyle = AnimationStyle.SPRING_60FPS
            )
        ))
    }

    fun setTheme(themeId: String) {
        if (availableThemes.any { it.id == themeId }) {
            currentThemeId = themeId
        }
    }

    fun getTheme(themeId: String): AppTheme? = availableThemes.find { it.id == themeId }

    /**
     * El tema activo.
     *
     * Terminaba en `!!`: si `currentThemeId` no estaba en la lista, la pantalla de
     * Diseño se cerraba con una excepción en vez de enseñar algo. Pasa al cambiar el
     * id de una paleta —como acaba de pasar con la que se atribuía a Namida— y pasa
     * con cualquier preferencia guardada de una versión anterior.
     */
    fun getCurrentTheme(): AppTheme =
        availableThemes.find { it.id == currentThemeId } ?: availableThemes.first()
    fun listThemes(): List<AppTheme> = availableThemes.toList()

    fun setLiquidGlass(enabled: Boolean) {
        liquidGlassEnabled = enabled
    }

    fun setBlurRadius(radius: Float) {
        glassBlurRadius = radius.coerceIn(0f, 64f)
    }

    fun setCustomPalette(hexColors: String) {
        customPalette = hexColors
    }

    fun getLiquidGlassEnabled(): Boolean = liquidGlassEnabled
    fun getBlurRadius(): Float = glassBlurRadius
}

enum class NavigationStyle {
    PILL_BOTTOM, READER_PANELS, ISLAND_FEED, CARD_GRID, CUSTOM
}

enum class AnimationStyle {
    SPRING_60FPS, FLUID_COMPOSE, GLASSMORPHISM, SMOOTH_FADE, CUSTOM
}

data class AppTheme(
    val id: String,
    val name: String,
    val designSource: String,
    val description: String,
    val primaryColor: String,
    val accentColor: String,
    val backgroundColor: String,
    val surfaceColor: String,
    val glassEnabled: Boolean,
    val blurRadius: Float,
    val navigationStyle: NavigationStyle,
    val animationStyle: AnimationStyle
)