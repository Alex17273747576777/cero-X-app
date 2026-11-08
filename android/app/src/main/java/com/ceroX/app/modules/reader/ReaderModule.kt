package com.ceroX.app.modules.reader

import android.content.Context
import com.ceroX.app.core.engine.ModuleHealth
import com.ceroX.app.core.engine.ModuleInterface
import java.io.File

class ReaderModule : ModuleInterface() {

    override val name = "reader"
    override val version = "0.1.0"
    override val dependencies: List<String> = emptyList()

    private var context: Context? = null
    private val library = mutableListOf<LibraryItem>()
    private val extensions = mutableListOf<ExtensionInfo>()
    private var cacheSizeMb = 512

    override fun initialize(config: Map<String, Any>): Boolean {
        cacheSizeMb = config["cache_size_mb"] as? Int ?: 512
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
                "extensions" to extensions.size,
                "cache_size_mb" to cacheSizeMb
            )
        )
    }

    fun addToLibrary(item: LibraryItem) {
        library.add(item)
    }

    fun listLibrary(): List<LibraryItem> = library.toList()

    fun registerExtension(extensionId: String, sourceUrl: String): Boolean {
        extensions.add(ExtensionInfo(extensionId, sourceUrl, System.currentTimeMillis()))
        return true
    }

    fun search(query: String): List<SearchResult> {
        // Placeholder: search through registered extensions
        return listOf(
            SearchResult("Resultado para: $query", "placeholder", "manga")
        )
    }

    fun getCacheDir(): File? {
        return context?.externalCacheDir
    }
}

data class LibraryItem(
    val id: String,
    val title: String,
    val author: String = "",
    val type: String = "manga",
    val path: String = "",
    val addedAt: Long = System.currentTimeMillis()
)

data class ExtensionInfo(
    val id: String,
    val sourceUrl: String,
    val registeredAt: Long
)

data class SearchResult(
    val title: String,
    val source: String,
    val type: String
)