package com.ceroX.app.modules.mode

import com.ceroX.app.core.engine.ModuleHealth
import com.ceroX.app.core.engine.ModuleInterface

/**
 * ModeManagerModule — Gestor de modos de la app.
 *
 * Permite alternar entre modos dedicados:
 *   • Audio (música, podcasts, YouTube audio)
 *   • Novela / EPUB (lectura de novelas, libros electrónicos)
 *   • Manga / Comics (lector de cómics, CBZ/CBR/PDF)
 *   • Video / Imagen (booru) (video streaming, imageboards)
 *   • Personalizado (modes definidos por el usuario)
 *
 * Cada modo adapta la navegación, el lector/reproductor y los controles rápidos.
 */
class ModeManagerModule : ModuleInterface() {

    override val name = "mode-manager"
    override val version = "1.0.0-beta"
    override val dependencies: List<String> = emptyList()

    private val availableModes = mutableListOf<AppMode>()
    private var currentModeId = "dashboard"
    private val modeHistory = mutableListOf<String>()

    init {
        // Los modos están desde la construcción: la pantalla de modos los lista en
        // cuanto se abre, y initialize() corre en el hilo de arranque.
        registerDefaultModes()
    }

    override fun initialize(config: Map<String, Any>): Boolean {
        registerDefaultModes()
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
                "available_modes" to availableModes.size,
                "current_mode" to currentModeId,
                "history_length" to modeHistory.size,
                "supported_modes" to availableModes.map { it.id }
            )
        )
    }

    private fun registerDefaultModes() {
        // Idempotente: se llama en init y otra vez en initialize().
        if (availableModes.isNotEmpty()) return
        availableModes.addAll(listOf(
            AppMode(
                id = "dashboard",
                name = "Panel Principal",
                icon = "dashboard",
                description = "Dashboard con estado de todos los módulos",
                category = ModeCategory.SYSTEM,
                compatiblePlatforms = listOf("android", "android_tv", "windows", "linux", "huawei")
            ),
            AppMode(
                id = "audio",
                name = "Audio",
                icon = "music_note",
                description = "Música, podcasts, audio de YouTube, listas de reproducción",
                category = ModeCategory.MEDIA,
                compatiblePlatforms = listOf("android", "android_tv", "windows", "linux", "huawei")
            ),
            AppMode(
                id = "novel",
                name = "Novela / EPUB",
                icon = "book",
                description = "Lectura de novelas, EPUB, libros electrónicos, shadow library",
                category = ModeCategory.READING,
                compatiblePlatforms = listOf("android", "android_tv", "windows", "linux", "huawei")
            ),
            AppMode(
                id = "manga_comics",
                name = "Manga / Cómics",
                icon = "menu_book",
                description = "Lector de cómics CBZ/CBR/CB7/CBT/PDF/EPUB, importación desde carpeta",
                category = ModeCategory.READING,
                compatiblePlatforms = listOf("android", "android_tv", "windows", "linux", "huawei")
            ),
            AppMode(
                id = "video_booru",
                name = "Video / Imagen (Booru)",
                icon = "image",
                description = "Video streaming, imageboards booru, galerías de imágenes",
                category = ModeCategory.MEDIA,
                compatiblePlatforms = listOf("android", "android_tv", "windows", "linux", "huawei")
            ),
            AppMode(
                id = "custom",
                name = "Personalizado",
                icon = "psychology",
                description = "Modos definidos por el usuario, combinaciones personalizadas",
                category = ModeCategory.CUSTOM,
                compatiblePlatforms = listOf("android", "android_tv", "windows", "linux", "huawei")
            ),
            AppMode(
                id = "catalog",
                name = "Catálogo de Apps",
                icon = "list",
                description = "40+ apps de Miyomi, Wotaku.wiki, EverythingMoe",
                category = ModeCategory.SYSTEM,
                compatiblePlatforms = listOf("android", "android_tv", "windows", "linux", "huawei")
            ),
            AppMode(
                id = "sanbox_extensions",
                name = "SanTBox Extensiones",
                icon = "security",
                description = "Instalación de extensiones (Shizuku, Sistema, Sandbox Interno)",
                category = ModeCategory.SYSTEM,
                compatiblePlatforms = listOf("android", "huawei")
            ),
            AppMode(
                id = "extension_factory",
                name = "Fábrica de Extensiones",
                icon = "factory",
                description = "Crear y compilar extensiones propias a APK",
                category = ModeCategory.SYSTEM,
                compatiblePlatforms = listOf("android", "windows", "linux")
            ),
            AppMode(
                id = "ai_assistant",
                name = "Asistentes IA",
                icon = "smart_assistant",
                description = "AI Hub, Kai, Neural AI, entrenamiento IA local",
                category = ModeCategory.AI,
                compatiblePlatforms = listOf("android", "android_tv", "windows", "linux", "huawei")
            ),
            AppMode(
                id = "servers",
                name = "Servidores Multimedia",
                icon = "cloud",
                description = "Jellyfin, Plex, Seanime, Komga, Kavita, LANraragi, Suwayomi",
                category = ModeCategory.SYSTEM,
                compatiblePlatforms = listOf("android", "android_tv", "windows", "linux", "huawei")
            ),
            AppMode(
                id = "tools",
                name = "Herramientas de Descarga",
                icon = "build",
                description = "gallery-dl, Anime Downloader NX, FMD2, JAM, HDoujin",
                category = ModeCategory.TOOLS,
                compatiblePlatforms = listOf("android", "windows", "linux")
            ),
            AppMode(
                id = "environments",
                name = "Entornos",
                icon = "storage",
                description = "Debian, contenedores, ISOs, Linux sandbox",
                category = ModeCategory.SYSTEM,
                compatiblePlatforms = listOf("android", "windows", "linux")
            ),
            AppMode(
                id = "reverse_engineering",
                name = "Ingeniería Inversa",
                icon = "code",
                description = "Antigravity, DeepSeek Harness, CodeComparator",
                category = ModeCategory.TOOLS,
                compatiblePlatforms = listOf("windows", "linux")
            )
        ))
    }

    fun setMode(modeId: String): Boolean {
        if (modeId !in MODOS_NAVEGABLES) return false
        val mode = availableModes.find { it.id == modeId } ?: return false
        if (currentModeId != modeId) {
            modeHistory.add(currentModeId)
            if (modeHistory.size > 50) modeHistory.removeAt(0)
        }
        currentModeId = modeId
        return true
    }

    fun getMode(modeId: String): AppMode? = availableModes.find { it.id == modeId }

    /** Null si todavía no hay modos registrados, en vez de una excepción. */
    fun getCurrentMode(): AppMode? =
        availableModes.find { it.id == currentModeId } ?: availableModes.firstOrNull()
    /** Sólo modos con un recorrido Android real; el resto no se vende como opción. */
    fun listModes(): List<AppMode> = availableModes.filter { it.id in MODOS_NAVEGABLES }
    fun listModesByCategory(category: ModeCategory): List<AppMode> =
        listModes().filter { it.category == category }
    fun listModesForPlatform(platform: String): List<AppMode> =
        listModes().filter { it.compatiblePlatforms.contains(platform) }

    fun getHistory(): List<String> = modeHistory.toList()

    fun addCustomMode(mode: AppMode) {
        availableModes.add(mode)
    }

    fun removeCustomMode(modeId: String) {
        availableModes.removeAll { it.id == modeId && it.category == ModeCategory.CUSTOM }
    }

    companion object {
        val MODOS_NAVEGABLES = setOf(
            "dashboard", "manga_comics", "novel", "catalog", "sanbox_extensions", "ai_assistant",
        )
    }
}

enum class ModeCategory {
    SYSTEM, MEDIA, READING, AI, TOOLS, CUSTOM
}

data class AppMode(
    val id: String,
    val name: String,
    val icon: String,
    val description: String,
    val category: ModeCategory,
    val compatiblePlatforms: List<String>,
    val customConfig: String? = null
)