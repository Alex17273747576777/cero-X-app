package com.ceroX.app.modules.comic

import com.ceroX.app.core.engine.ModuleHealth
import com.ceroX.app.core.engine.ModuleInterface

/**
 * ComicModule — Lector de cómics y cómics western.
 * Integra funciones de: Cosmic Comics, KOReader, YACReader.
 */
class ComicModule : ModuleInterface() {

    override val name = "comic-reader"
    override val version = "1.0.0-beta"
    override val dependencies: List<String> = emptyList()

    private val library = mutableListOf<ComicItem>()
    private val supportedFormats = listOf("CBR", "CBZ", "CB7", "CBT", "ZIP", "RAR", "7z", "TAR", "PDF", "EPUB")

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
                "supported_formats" to supportedFormats.size,
                "formats" to supportedFormats,
                "customizable_viewer" to true,
                "bookshelf_layout" to true
            )
        )
    }

    fun addToLibrary(item: ComicItem) {
        library.add(item)
    }

    fun listLibrary(): List<ComicItem> = library.toList()

    fun getSupportedFormats(): List<String> = supportedFormats

    /**
     * Recorre la carpeta y añade lo que encuentra con los formatos soportados.
     *
     * Devolvía "Cómics importados" sin abrir la carpeta ni añadir nada a la
     * biblioteca. Aqui se listan los archivos reales, se filtran por las
     * extensiones que el modulo declara y se añaden uno a uno.
     */
    fun importFromFolder(folderPath: String): ImportResult {
        val dir = java.io.File(folderPath)
        if (!dir.isDirectory) {
            return ImportResult(folderPath, false, "No es una carpeta: $folderPath")
        }
        val formatos = supportedFormats.map { it.lowercase().removePrefix(".") }
        val ficheros = dir.listFiles { f -> f.isFile && f.extension.lowercase() in formatos }
            ?: return ImportResult(folderPath, false,
                "La carpeta existe pero no se puede leer (permisos): $folderPath")

        for (f in ficheros) {
            library.add(
                ComicItem(
                    id = "comic_" + folderPath.hashCode() + "_" + f.name.hashCode(),
                    title = f.nameWithoutExtension,
                    format = f.extension.lowercase(),
                    path = f.absolutePath
                )
            )
        }
        return ImportResult(
            folderPath,
            ficheros.isNotEmpty(),
            if (ficheros.isEmpty()) {
                "Ningun archivo con los formatos soportados ($formatos) en $folderPath"
            } else {
                "${ficheros.size} archivo(s) importados desde $folderPath"
            }
        )
    }
}

data class ComicItem(
    val id: String,
    val title: String,
    val author: String = "",
    val format: String = "",
    val path: String = "",
    val addedAt: Long = System.currentTimeMillis()
)

data class ImportResult(
    val folder: String,
    val success: Boolean,
    val message: String
)