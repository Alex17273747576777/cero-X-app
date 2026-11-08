package com.ceroX.app.modules.kisara

import com.ceroX.app.core.engine.ModuleHealth
import com.ceroX.app.core.engine.ModuleInterface

/**
 * KisaraModule — Integración de Kisara (ifritraen/kisara) en cero-X.
 *
 * Kisara es un fork de Komikku con características deseadas, estabilizado
 * para eliminar la inestabilidad de etapa temprana.
 *
 * Características de Kisara (fork de Komikku, estabilizadas):
 *   • Lector de manga basado en TachiyomiSY & Mihon/Tachiyomi
 *   • Sugerencias automáticas de recomendaciones del sitio fuente
 *   • Categorías ocultas (privacidad)
 *   • Auto-tema de color basado en la portada de cada entrada
 *   • Tema personalizado con paletas de colores infinitas
 *   • Bulk-favorite de múltiples entradas a la vez
 *   • Iconos de fuente e idioma en Biblioteca y varios lugares
 *   • Feed que soporta TODAS las fuentes con más items
 *   • Navegación rápida para bibliotecas grandes
 *   • Entradas agrupadas en pestaña de Actualizaciones
 *   • Notificación de actualización con portada del manga
 *   • Sincronización automática 2-way con trackers
 *   • Chips para búsqueda guardada en navegación de fuentes
 *   • Panorama cover (portada ancha a pantalla completa)
 *   • Fusionar múltiples entradas de biblioteca a la vez
 *   • Selección por rango para Migración
 *   • Habilitar/deshabilitar repo con icono
 *   • Pantalla de Error de Actualización y migrarlas
 *   • Pantalla "por actualizar" con smart-update
 *   • Buscar fuentes y filtro rápido NSFW en Extensiones, Navegación y Migración
 *   • Backup/restauración/sincronización/reorden de Feed
 *   • Long-click para añadir/quitar entrada individual a/biblioteca en todas partes
 *   • Botón Docking Leer/Reanudar a izquierda/derecha
 *   • Banner de progreso en-app (sincronización, restauración, actualización)
 *   • Auto-instalación de actualización de app
 *   • Intervalo configurable para refrescar entradas desde almacenamiento
 *
 * Mejoras específicas de Kisara (más allá de Komikku):
 *   • Motor OCR ONNX (PP-OCRv6_manga) para traducción de páginas on-device
 *   • Colorización neural ONNX (manga_colorization_v2_int8.onnx)
 *   • Traductor de manga con IA (llama.cpp, Index-Translate 2B, LaMa inpainting)
 *   • Soporte para extensiones Keiyoushi/Tachiyomi
 *   • Filtro por idioma, ocultar fuentes 18+
 *   • WebView para pasar verificación Cloudflare
 *   • 7 paletas de colores, AMOLED dark mode
 *   • Motor de búsqueda avanzado (exclude, quotes, etc.)
 *   • Detección automática de webtoon
 *   • Recomendaciones de manga (MAL, Anilist, Neko Similar Manga)
 */
class KisaraModule : ModuleInterface() {

    override val name = "kisara-reader"
    override val version = "2.3.9-kisara-stable"
    override val dependencies = emptyList()

    private val sources = mutableListOf<KisaraSource>()
    private val libraryEntries = mutableListOf<KisaraLibraryEntry>()
    private val categories = mutableListOf<KisaraCategory>()
    private val hiddenCategories = mutableSetOf<String>()
    private var autoThemeFromCover = true
    private var feedItemsPerSource = 20
    private var fastBrowsingEnabled = true
    private var imageCacheSizeMb = 512
    private var pagePreloadCount = 3
    private var autoscrollEnabled = false
    private var webtoonAutoDetect = true
    private var forceDisableWebtoonZoom = false
    private var lewdFilterEnabled = false
    private var trackingFilterEnabled = false
    private var ocrEnabled = true
    private var neuralColorizationEnabled = true
    private var translationEnabled = true
    private var keiyoushiExtensionsEnabled = true
    private var cloudflareBypassEnabled = true

    override fun initialize(config: Map<String, Any>): Boolean {
        feedItemsPerSource = config["feed_items_per_source"] as? Int ?: 20
        fastBrowsingEnabled = config["fast_browsing"] as? Boolean ?: true
        imageCacheSizeMb = config["image_cache_size_mb"] as? Int ?: 512
        pagePreloadCount = config["page_preload_count"] as? Int ?: 3
        autoscrollEnabled = config["autoscroll_enabled"] as? Boolean ?: false
        webtoonAutoDetect = config["webtoon_auto_detect"] as? Boolean ?: true
        forceDisableWebtoonZoom = config["force_disable_webtoon_zoom"] as? Boolean ?: false
        lewdFilterEnabled = config["lewd_filter"] as? Boolean ?: false
        trackingFilterEnabled = config["tracking_filter"] as? Boolean ?: false
        autoThemeFromCover = config["auto_theme_from_cover"] as? Boolean ?: true
        ocrEnabled = config["ocr_enabled"] as? Boolean ?: true
        neuralColorizationEnabled = config["neural_colorization"] as? Boolean ?: true
        translationEnabled = config["translation_enabled"] as? Boolean ?: true
        keiyoushiExtensionsEnabled = config["keiyoushi_extensions"] as? Boolean ?: true
        cloudflareBypassEnabled = config["cloudflare_bypass"] as? Boolean ?: true

        registerDefaultSources()
        registerDefaultCategories()
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
                "sources" to sources.size,
                "library_entries" to libraryEntries.size,
                "categories" to categories.size,
                "hidden_categories" to hiddenCategories.size,
                "auto_theme_from_cover" to autoThemeFromCover,
                "feed_items_per_source" to feedItemsPerSource,
                "fast_browsing" to fastBrowsingEnabled,
                "image_cache_size_mb" to imageCacheSizeMb,
                "page_preload_count" to pagePreloadCount,
                "autoscroll" to autoscrollEnabled,
                "webtoon_auto_detect" to webtoonAutoDetect,
                "force_disable_webtoon_zoom" to forceDisableWebtoonZoom,
                "lewd_filter" to lewdFilterEnabled,
                "tracking_filter" to trackingFilterEnabled,
                "ocr_enabled" to ocrEnabled,
                "neural_colorization" to neuralColorizationEnabled,
                "translation_enabled" to translationEnabled,
                "keiyoushi_extensions" to keiyoushiExtensionsEnabled,
                "cloudflare_bypass" to cloudflareBypassEnabled,
                "stability" to "stabilized",
                "fork_of" to "komikku (ifritraen/kisara)",
                "min_android" to "8.0 (API 26)"
            )
        )
    }

    private fun registerDefaultSources() {
        sources.addAll(listOf(
            KisaraSource("mangadex", "MangaDex", "https://mangadex.org", true, false, "en"),
            KisaraSource("nhentai", "NHentai", "https://nhentai.net", true, true, "en"),
            KisaraSource("e_hentai", "E-Hentai", "https://e-hentai.org", true, true, "en"),
            KisaraSource("exhentai", "ExHentai", "https://exhentai.org", true, true, "en"),
            KisaraSource("mangaplus", "MangaPlus", "https://mangaplus.shueisha.co.jp", true, false, "en"),
            KisaraSource("webtoon", "Webtoon", "https://webtoon.com", true, false, "en"),
            KisaraSource("tap", "Tapas", "https://tapas.io", true, false, "en"),
            KisaraSource("keiyoushi", "Keiyoushi Repo", "https://keiyoushi.sourceforge.net", true, false, "multi")
        ))
    }

    private fun registerDefaultCategories() {
        categories.addAll(listOf(
            KisaraCategory("reading", "Leyendo", false),
            KisaraCategory("completed", "Completado", false),
            KisaraCategory("plan_to_read", "Plan de lectura", false),
            KisaraCategory("dropped", "Abandonado", false),
            KisaraCategory("favorites", "Favoritos", false),
            KisaraCategory("hidden", "Oculto", true)
        ))
        hiddenCategories.add("hidden")
    }

    fun addSource(source: KisaraSource) {
        sources.add(source)
    }

    fun listSources(): List<KisaraSource> = sources.toList()

    fun toggleSourceEnabled(sourceId: String, enabled: Boolean) {
        val source = sources.find { it.id == sourceId }
        if (source != null) {
            val idx = sources.indexOf(source)
            sources[idx] = source.copy(enabled = enabled)
        }
    }

    fun addLibraryEntry(entry: KisaraLibraryEntry) {
        libraryEntries.add(entry)
    }

    fun listLibraryEntries(): List<KisaraLibraryEntry> = libraryEntries.toList()

    fun bulkFavorite(entryIds: List<String>, favorite: Boolean) {
        for (id in entryIds) {
            val entry = libraryEntries.find { it.id == id }
            if (entry != null) {
                val idx = libraryEntries.indexOf(entry)
                libraryEntries[idx] = entry.copy(isFavorite = favorite)
            }
        }
    }

    fun mergeLibraryEntries(primaryId: String, duplicateIds: List<String>) {
        val primary = libraryEntries.find { it.id == primaryId } ?: return
        for (dupId in duplicateIds) {
            val dup = libraryEntries.find { it.id == dupId } ?: continue
            val mergedProgress = maxOf(primary.progress, dup.progress)
            val mergedChapters = primary.chaptersRead + dup.chaptersRead
            val idx = libraryEntries.indexOf(primary)
            libraryEntries[idx] = primary.copy(
                progress = mergedProgress,
                chaptersRead = mergedChapters,
                isFavorite = primary.isFavorite || dup.isFavorite,
                sources = (primary.sources + dup.sources).distinct()
            )
            libraryEntries.removeAll { it.id == dupId }
        }
    }

    fun toggleCategoryHidden(categoryId: String, hidden: Boolean) {
        if (hidden) hiddenCategories.add(categoryId) else hiddenCategories.remove(categoryId)
    }

    fun listVisibleCategories(): List<KisaraCategory> =
        categories.filter { !hiddenCategories.contains(it.id) }

    fun setAutoThemeFromCover(enabled: Boolean) {
        autoThemeFromCover = enabled
    }

    fun setFeedItemsPerSource(count: Int) {
        feedItemsPerSource = count.coerceIn(5, 100)
    }

    fun setImageCacheSizeMb(sizeMb: Int) {
        imageCacheSizeMb = sizeMb.coerceIn(64, 2048)
    }

    fun setPagePreloadCount(count: Int) {
        pagePreloadCount = count.coerceIn(1, 10)
    }

    fun toggleAutoscroll(enabled: Boolean) {
        autoscrollEnabled = enabled
    }

    fun toggleWebtoonAutoDetect(enabled: Boolean) {
        webtoonAutoDetect = enabled
    }

    fun toggleForceDisableWebtoonZoom(enabled: Boolean) {
        forceDisableWebtoonZoom = enabled
    }

    fun toggleLewdFilter(enabled: Boolean) {
        lewdFilterEnabled = enabled
    }

    fun toggleTrackingFilter(enabled: Boolean) {
        trackingFilterEnabled = enabled
    }

    fun toggleOcr(enabled: Boolean) {
        ocrEnabled = enabled
    }

    fun toggleNeuralColorization(enabled: Boolean) {
        neuralColorizationEnabled = enabled
    }

    fun toggleTranslation(enabled: Boolean) {
        translationEnabled = enabled
    }

    fun toggleKeiyoushiExtensions(enabled: Boolean) {
        keiyoushiExtensionsEnabled = enabled
    }

    fun toggleCloudflareBypass(enabled: Boolean) {
        cloudflareBypassEnabled = enabled
    }

    fun searchLibrary(query: String, exclude: Boolean = false): List<KisaraLibraryEntry> {
        return libraryEntries.filter { entry ->
            val matches = entry.title.contains(query, ignoreCase = true) ||
                          entry.author.contains(query, ignoreCase = true) ||
                          entry.tags.any { it.contains(query, ignoreCase = true) }
            if (exclude) !matches else matches
        }
    }

    fun getSuggestions(sourceId: String, entryId: String): List<KisaraSuggestion> {
        return emptyList() // Placeholder: se conectaría con la API del source
    }
}

data class KisaraSource(
    val id: String,
    val name: String,
    val baseUrl: String,
    val enabled: Boolean,
    val nsfw: Boolean,
    val languageCode: String,
    val iconUrl: String? = null,
    val customDescription: String? = null,
    val openInApp: Boolean = false,
    val batchAddToLibrary: Boolean = false,
    val keiyoushiExtension: Boolean = false
)

data class KisaraLibraryEntry(
    val id: String,
    val title: String,
    val author: String,
    val coverUrl: String?,
    val panoramaUrl: String?,
    val progress: Float = 0.0f,
    val chaptersRead: Int = 0,
    val totalChapters: Int = 0,
    val isFavorite: Boolean = false,
    val categoryId: String? = null,
    val sourceId: String?,
    val sources: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val language: String? = null,
    val lastUpdated: Long = System.currentTimeMillis(),
    val isTracked: Boolean = false,
    val isLewd: Boolean = false,
    val readingDirection: String = "ltr",
    val smartBackground: Boolean = true,
    val autoscroll: Boolean = false,
    val pagePreload: Int = 3,
    val webtoonMode: Boolean = false,
    val webtoonZoomDisabled: Boolean = false,
    val ocrTranslated: Boolean = false,
    val neuralColorized: Boolean = false
)

data class KisaraCategory(
    val id: String,
    val name: String,
    val hidden: Boolean,
    val color: String? = null,
    val icon: String? = null
)

data class KisaraSuggestion(
    val id: String,
    val title: String,
    val coverUrl: String?,
    val sourceId: String,
    val relationType: String
)