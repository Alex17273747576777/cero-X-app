package com.ceroX.app.modules.kisara

import android.graphics.Bitmap
import android.graphics.Color
import com.ceroX.app.core.engine.ModuleHealth
import com.ceroX.app.core.engine.ModuleInterface

/**
 * KisaraThemeModule — Sistema de temas de Kisara estabilizado.
 *
 * Características:
 *   • Auto-tema de color basado en la portada de cada entrada (entry View & Reader)
 *   • Tema personalizado con paletas de colores infinitas (7+ paletas)
 *   • Panorama cover (portada ancha en pantalla completa)
 *   • Modo claro/oscuro
 *   • Filtro oscuro AMOLED
 *   • Paletas de colores personalizables por el usuario
 */
class KisaraThemeModule : ModuleInterface() {

    override val name = "kisara-theme"
    override val version = "2.3.9-kisara-stable"
    override val dependencies = emptyList()

    private var autoThemeFromCover = true
    private var isDarkMode = true
    private var amoledDarkMode = false
    private var paletteSlots = mutableListOf<KisaraColorPalette>()
    private var currentPaletteId = "default"
    private var panoramaCoverEnabled = true

    override fun initialize(config: Map<String, Any>): Boolean {
        autoThemeFromCover = config["auto_theme_from_cover"] as? Boolean ?: true
        isDarkMode = config["dark_mode"] as? Boolean ?: true
        amoledDarkMode = config["amoled_dark_mode"] as? Boolean ?: false
        panoramaCoverEnabled = config["panorama_cover"] as? Boolean ?: true
        registerDefaultPalettes()
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
                "auto_theme_from_cover" to autoThemeFromCover,
                "dark_mode" to isDarkMode,
                "amoled_dark_mode" to amoledDarkMode,
                "palette_slots" to paletteSlots.size,
                "current_palette" to currentPaletteId,
                "panorama_cover" to panoramaCoverEnabled,
                "stability" to "stabilized"
            )
        )
    }

    private fun registerDefaultPalettes() {
        paletteSlots.addAll(listOf(
            KisaraColorPalette("default", "Por defecto", "#6366F1", "#22D3EE", "#0F172A", "#1E293B"),
            KisaraColorPalette("midnight", "Medianoche", "#8B5CF6", "#A855F7", "#020617", "#0F172A"),
            KisaraColorPalette("sunset", "Atardecer", "#EF4444", "#F59E0B", "#18181B", "#27272A"),
            KisaraColorPalette("forest", "Bosque", "#10B981", "#34D399", "#022C22", "#064E3B"),
            KisaraColorPalette("ocean", "Océano", "#06B6D4", "#14B8A6", "#030712", "#0F172A"),
            KisaraColorPalette("amazon", "Amazon", "#2563EB", "#3B82F6", "#0B1120", "#1E293B"),
            KisaraColorPalette("sakura", "Sakura", "#DB2777", "#F472B6", "#18181B", "#27272A")
        ))
    }

    fun extractDominantColor(bitmap: Bitmap): Int {
        val width = bitmap.width
        val height = bitmap.height
        var rSum = 0
        var gSum = 0
        var bSum = 0
        var count = 0
        val step = maxOf(1, minOf(width, height) / 50)
        for (y in 0 until height step step) {
            for (x in 0 until width step step) {
                val pixel = bitmap.getPixel(x, y)
                rSum += Color.red(pixel)
                gSum += Color.green(pixel)
                bSum += Color.blue(pixel)
                count++
            }
        }
        return Color.rgb(rSum / count, gSum / count, bSum / count)
    }

    fun generateThemeFromColor(dominantColor: Int): KisaraColorPalette {
        val r = Color.red(dominantColor)
        val g = Color.green(dominantColor)
        val b = Color.blue(dominantColor)
        val hex = String.format("#%02X%02X%02X", r, g, b)
        return KisaraColorPalette(
            id = "auto_${System.currentTimeMillis()}",
            name = "Auto desde portada",
            primaryColor = hex,
            accentColor = hex,
            backgroundColor = if (isDarkMode) "#0F172A" else "#FFFFFF",
            surfaceColor = if (isDarkMode) "#1E293B" else "#F5F5F5"
        )
    }

    fun addPalette(palette: KisaraColorPalette) {
        paletteSlots.add(palette)
    }

    fun setCurrentPalette(paletteId: String) {
        if (paletteSlots.any { it.id == paletteId }) {
            currentPaletteId = paletteId
        }
    }

    fun listPalettes(): List<KisaraColorPalette> = paletteSlots.toList()

    fun getCurrentPalette(): KisaraColorPalette =
        paletteSlots.find { it.id == currentPaletteId } ?: paletteSlots.first()

    fun toggleAutoThemeFromCover(enabled: Boolean) {
        autoThemeFromCover = enabled
    }

    fun toggleDarkMode(enabled: Boolean) {
        isDarkMode = enabled
    }

    fun toggleAmoledDarkMode(enabled: Boolean) {
        amoledDarkMode = enabled
    }

    fun togglePanoramaCover(enabled: Boolean) {
        panoramaCoverEnabled = enabled
    }
}

data class KisaraColorPalette(
    val id: String,
    val name: String,
    val primaryColor: String,
    val accentColor: String,
    val backgroundColor: String,
    val surfaceColor: String
)