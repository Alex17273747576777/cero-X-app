package com.ceroX.app.modules.booru

import com.ceroX.app.core.engine.ModuleHealth
import com.ceroX.app.core.engine.ModuleInterface

/**
 * BooruModule — Integración de weekanya/Booru en cero-X.
 *
 * Booru es un cliente Android para buscar, navegar y gestionar medios de los
 * imageboards booru más populares (Rule34, Gelbooru, Danbooru, etc.).
 *
 * Características integradas:
 * - Visualizador de medios a pantalla completa con zoom, pan y doble toque
 * - Reproducción de video con AndroidX Media3 ExoPlayer
 * - Hoja de detalles con dimensiones, puntuación, clasificación, fecha, fuente y chips de etiquetas
 * - Favoritos persistentes con SQLite via Room
 * - Caché de imágenes con Coil (25% RAM, 200 MB disco LRU)
 * - Descargas directas a almacenamiento público con MediaStore
 * - Credenciales cifradas con AndroidX Security Crypto (EncryptedSharedPreferences)
 * - Soporte: JPEG, PNG, WebP, GIF animado, MP4, WebM
 */
class BooruModule : ModuleInterface() {

    override val name = "booru-viewer"
    override val version = "1.0.0-beta"
    override val dependencies: List<String> = emptyList()

    private val supportedSites = mutableListOf<String>()
    private val favoritePosts = mutableListOf<BooruFavorite>()
    private var cacheSizeMb = 200
    private var coilMemoryCachePercent = 25

    init {
        // Los sitios soportados están desde la construcción, por la misma razón que
        // en los otros catálogos.
        registerDefaultSites()
    }

    override fun initialize(config: Map<String, Any>): Boolean {
        cacheSizeMb = config["cache_size_mb"] as? Int ?: 200
        coilMemoryCachePercent = config["coil_memory_cache_percent"] as? Int ?: 25
        registerDefaultSites()
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
                "supported_sites" to supportedSites.size,
                "favorite_posts" to favoritePosts.size,
                "cache_size_mb" to cacheSizeMb,
                "coil_memory_cache_percent" to coilMemoryCachePercent,
                "supported_formats" to listOf("JPEG", "PNG", "WebP", "GIF", "MP4", "WebM"),
                "min_android" to "8.0 (API 26)"
            )
        )
    }

    private fun registerDefaultSites() {
        // Idempotente: se llama en init y otra vez en initialize().
        if (supportedSites.isNotEmpty()) return
        supportedSites.addAll(listOf(
            "rule34.xxx", "gelbooru.com", "danbooru.donmai.us",
            "safebooru.org", "konachan.com", "yande.re"
        ))
    }

    fun addFavorite(post: BooruFavorite) {
        favoritePosts.add(post)
    }

    fun listFavorites(): List<BooruFavorite> = favoritePosts.toList()

    /**
     * No busca: cero-X no lleva ningun cliente de booru.
     *
     * Devolvía `success = true` con la lista vacía, o sea "búsqueda correcta, cero
     * resultados", que es exactamente lo que se ve cuando una búsqueda no encuentra
     * nada. La diferencia importa: aquí no se llegó a preguntar. La pantalla ya lo
     * dice con un Toast; este resultado lleva el motivo para quien llame por código.
     */
    fun searchBooru(site: String, tags: String, limit: Int = 20): BooruSearchResult {
        return BooruSearchResult(
            site = site,
            tags = tags,
            limit = limit,
            results = emptyList(),
            success = false,
            motivo = "No hay ningún cliente de booru en cero-X: no se llegó a consultar " + site
        )
    }

    /**
     * No descarga: este módulo no tiene descargador conectado.
     *
     * Devolvía `success = true` con el mensaje "Descargado" sin abrir una conexión
     * ni escribir el archivo de destino.
     */
    fun downloadMedia(url: String, destination: String): DownloadResult {
        return DownloadResult(
            url = url,
            destination = destination,
            success = false,
            message = "No se descargó nada: el módulo no tiene descargador ni MediaStore conectado"
        )
    }

    fun getSupportedSites(): List<String> = supportedSites.toList()
}

data class BooruFavorite(
    val id: String,
    val site: String,
    val postId: Int,
    val url: String,
    val tags: List<String>,
    val score: Int,
    val rating: String,
    val savedAt: Long = System.currentTimeMillis()
)

data class BooruSearchResult(
    val site: String,
    val tags: String,
    val limit: Int,
    val results: List<BooruPost>,
    val success: Boolean,
    /** Por qué no hay resultados. Con `success = true` y la lista vacía no se
     *  distinguía "no encontré nada" de "no llegué a buscar". */
    val motivo: String? = null
)

data class BooruPost(
    val id: Int,
    val url: String,
    val previewUrl: String?,
    val tags: List<String>,
    val score: Int,
    val rating: String,
    val dimensions: String,
    val source: String?,
    val createdAt: Long
)

data class DownloadResult(
    val url: String,
    val destination: String,
    val success: Boolean,
    val message: String
)