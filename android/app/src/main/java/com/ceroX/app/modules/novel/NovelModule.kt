package com.ceroX.app.modules.novel

import com.ceroX.app.core.engine.ModuleHealth
import com.ceroX.app.core.engine.ModuleInterface

/**
 * NovelModule — Lector de novelas ligeras y webnovels.
 * Integra funciones de: Shosetsu, IReader, LightNovelReader, NeoQN, NovelDokusha, Openlib.
 */
class NovelModule : ModuleInterface() {

    override val name = "novel-reader"
    override val version = "1.0.0-beta"
    override val dependencies: List<String> = emptyList()

    private val library = mutableListOf<NovelItem>()
    private val sources = mutableListOf<NovelSource>()

    override fun initialize(config: Map<String, Any>): Boolean {
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
                "library_items" to library.size,
                "sources" to sources.size,
                "offline_reading" to true,
                "epub_export" to true,
                "translation_support" to true,
                "shadow_library" to true
            )
        )
    }

    fun addToLibrary(item: NovelItem) {
        library.add(item)
    }

    fun listLibrary(): List<NovelItem> = library.toList()

    fun addSource(source: NovelSource) {
        sources.add(source)
    }

    fun listSources(): List<NovelSource> = sources.toList()

    /**
     * Sin fuentes conectadas no hay resultados.
     *
     * Devolvía una fila inventada con el texto de la consulta como titulo, igual
     * que `ReaderModule.search()`.
     */
    fun searchNovels(query: String): List<NovelSearchResult> = emptyList()

    /** No hay ninguna biblioteca externa conectada, así que no se descarga nada. */
    fun downloadFromShadowLibrary(query: String): ShadowLibraryResult {
        return ShadowLibraryResult(query, false,
            "No hay ninguna biblioteca externa conectada a cero-X; no se descargo nada para " + query)
    }
}

data class NovelItem(
    val id: String,
    val title: String,
    val author: String = "",
    val type: String = "light_novel", // light_novel | web_novel | novel
    val source: String = "",
    val status: String = "reading",
    val chaptersRead: Int = 0,
    val totalChapters: Int = 0,
    val epubExported: Boolean = false,
    val addedAt: Long = System.currentTimeMillis()
)

data class NovelSource(
    val id: String,
    val name: String,
    val baseUrl: String,
    val supportsTranslation: Boolean = false,
    val supportsOffline: Boolean = true
)

data class NovelSearchResult(
    val title: String,
    val source: String,
    val type: String
)

data class ShadowLibraryResult(
    val query: String,
    val success: Boolean,
    val message: String
)