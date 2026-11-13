package com.ceroX.app.modules.booru

import android.content.Context
import com.ceroX.app.core.engine.ModuleHealth
import com.ceroX.app.core.engine.ModuleInterface
import kotlinx.serialization.Serializable

/** Cliente Safebooru real y limitado a rating:safe; no reimplementa otras apps Booru. */
class BooruModule : ModuleInterface() {
    override val name = "booru-viewer"
    override val version = "1.2.0-visor-zoom"
    override val dependencies: List<String> = emptyList()

    private val supportedSites = mutableListOf<String>()
    private val client = SafebooruClient()
    private var favoritos: FavoritosBooruStore? = null
    private var descargas: DescargasBooru? = null
    var ultimoResultado: BooruSearchResult? = null
        private set

    init { registerDefaultSites() }

    override fun initialize(config: Map<String, Any>): Boolean {
        val contexto = config["context"] as? Context ?: return false
        favoritos = FavoritosBooruStore(contexto.applicationContext)
        descargas = DescargasBooru.real(contexto)
        registerDefaultSites()
        _initialized = true
        return true
    }

    override fun start(): Boolean {
        if (!_initialized) return false
        _started = true
        return true
    }

    override fun stop(): Boolean { _started = false; return true }

    override fun healthCheck(): ModuleHealth = ModuleHealth(
        name, version, if (_started) "healthy" else "stopped",
        mapOf(
            "supported_sites" to supportedSites.size,
            "favorite_posts" to (favoritos?.listar()?.size ?: 0),
            "safe_only" to true,
            "external_favorites" to (favoritos?.disponible() == true),
            "downloads_to_external_storage" to (descargas != null),
            "download_limit_bytes" to DescargasBooru.MAX_BYTES,
            "min_android" to "8.0 (API 26)",
        ),
    )

    private fun registerDefaultSites() {
        if (supportedSites.isNotEmpty()) return
        supportedSites += SafebooruClient.SITIO
    }

    suspend fun searchBooru(site: String = SafebooruClient.SITIO, tags: String, limit: Int = 20, pagina: Int = 0): BooruSearchResult {
        val resultado = if (site != SafebooruClient.SITIO) {
            BooruSearchResult(site, tags, limit, emptyList(), false, "Sitio no integrado; únicamente Safebooru.org está comprobado", pagina)
        } else client.buscar(tags, limit, pagina)
        ultimoResultado = resultado
        return resultado
    }

    fun listFavorites(): List<BooruFavorite> = favoritos?.listar()?.map { it.aFavorito() }.orEmpty()
    fun postsFavoritos(): List<BooruPost> = favoritos?.listar().orEmpty()
    fun idsFavoritos(): Set<Int> = favoritos?.ids().orEmpty()
    fun alternarFavorito(post: BooruPost): Boolean = favoritos?.alternar(post) == true
    fun getSupportedSites(): List<String> = supportedSites.toList()

    /**
     * Descarga la imagen en el almacenamiento externo. Bloquea el hilo: la
     * pantalla la llama en `Dispatchers.IO`.
     *
     * Sustituye a un `downloadMedia` que devolvía siempre «esta fase no incluye
     * descargas». Devolver un fallo fijo no es lo mismo que no tener la función:
     * ahora el fallo lleva el motivo real (host ajeno, HTTP, tamaño, falta de
     * almacenamiento externo).
     */
    fun descargar(post: BooruPost, url: String): ResultadoDescarga {
        val descargador = descargas
            ?: return ResultadoDescarga.Fallo(
                "El módulo Booru no está inicializado: no se puede descargar nada",
            )
        return descargador.descargar(post, url)
    }
}

@Serializable
data class BooruPost(
    val id: Int, val url: String, val previewUrl: String?, val tags: List<String>,
    val score: Int, val rating: String, val dimensions: String, val source: String?, val createdAt: Long,
) {
    fun pagina(): String = "https://safebooru.org/index.php?page=post&s=view&id=$id"
    fun aFavorito(): BooruFavorite = BooruFavorite("${SafebooruClient.SITIO}:$id", SafebooruClient.SITIO, id, url, tags, score, rating)
}

data class BooruFavorite(
    val id: String, val site: String, val postId: Int, val url: String, val tags: List<String>,
    val score: Int, val rating: String, val savedAt: Long = System.currentTimeMillis(),
)

data class BooruSearchResult(
    val site: String, val tags: String, val limit: Int, val results: List<BooruPost>, val success: Boolean,
    val motivo: String? = null, val pagina: Int = 0,
)
