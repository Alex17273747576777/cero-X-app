package com.ceroX.app.modules.tools

import com.ceroX.app.core.engine.ModuleHealth
import com.ceroX.app.core.engine.ModuleInterface

/**
 * ToolsModule — Herramientas de descarga y utilidades.
 * Integra: gallery-dl, Anime Downloader NX, Free Manga Downloader 2, JAM, HDoujin Downloader, etc.
 */
class ToolsModule : ModuleInterface() {

    override val name = "download-tools"
    override val version = "1.0.0-beta"
    override val dependencies: List<String> = emptyList()

    private val availableTools = mutableListOf<ToolInfo>()

    override fun initialize(config: Map<String, Any>): Boolean {
        registerDefaultTools()
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
                "tools_available" to availableTools.size,
                "tools_ejecutables" to availableTools.count { it.ejecutable },
                // El valor de cada una dice si se puede ejecutar desde la app.
                // Ninguna puede: son binarios de escritorio o scripts de
                // navegador, y aqui solo se listan.
                "gallery_dl" to false,
                "anime_downloader_nx" to false,
                "free_manga_downloader_2" to false,
                "jam" to false,
                "hdoujin_downloader" to false,
                "mangaplus_downloader" to false
            )
        )
    }

    private fun registerDefaultTools() {
        availableTools.add(ToolInfo("gallery_dl", "gallery-dl", "Command-line manga downloader", "CLI", true))
        availableTools.add(ToolInfo("anime_downloader_nx", "Anime Downloader NX", "Anime downloader", "Anime", true))
        availableTools.add(ToolInfo("free_manga_downloader_2", "Free Manga Downloader 2", "Manga downloader", "Manga", true))
        availableTools.add(ToolInfo("jam", "JAM", "Manga downloader", "Manga", true))
        availableTools.add(ToolInfo("hdoujin_downloader", "HDoujin Downloader", "Doujin downloader", "Specific cases", true))
        availableTools.add(ToolInfo("mangaplus_downloader_ng", "MangaPlus Downloader NG", "MangaPlus downloader", "Specific cases", true))
    }

    fun listTools(): List<ToolInfo> = availableTools.toList()

    /**
     * No ejecuta la herramienta, y dice por que.
     *
     * Devolvía "Tool $toolId executed with N args" sin ejecutar nada. Estas son
     * herramientas de escritorio (gallery-dl) o scripts de navegador (JAM,
     * HDoujin Downloader, MangaPlus Downloader NG): cero-X no las lleva dentro y
     * no puede lanzarlas.
     */
    fun runTool(toolId: String, args: List<String>): ToolResult {
        val tool = availableTools.firstOrNull { it.id == toolId }
            ?: return ToolResult(toolId, false, "Tool not found")
        if (!tool.ejecutable) {
            return ToolResult(
                toolId, false,
                tool.name + " no se ejecuta desde cero-X: es una herramienta de escritorio o un " +
                    "script de navegador. Aqui solo se lista, con " + args.size + " argumento(s) sin usar."
            )
        }
        return ToolResult(toolId, false,
            "Ninguna herramienta de este catalogo esta marcada como ejecutable en este build")
    }
}

/**
 * Una herramienta del catalogo.
 *
 * `available` dice si está en el catálogo, no si se puede lanzar: todas las que
 * hay son de escritorio o de navegador. Lo que dice si cero-X puede ejecutarla es
 * `ejecutable`, y en este build no hay ninguna en true.
 */
data class ToolInfo(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val available: Boolean,
    val ejecutable: Boolean = false
)

data class ToolResult(
    val toolId: String,
    val success: Boolean,
    val message: String
)