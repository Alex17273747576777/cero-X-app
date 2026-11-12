package com.ceroX.app.modules.booru

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.TimeUnit

@Serializable private data class DapiPost(
    val id: Int = 0, val file_url: String? = null, val preview_url: String? = null,
    val sample_url: String? = null, val tags: String? = null, val score: Int? = null,
    val rating: String? = null, val width: Int? = null, val height: Int? = null,
    val source: String? = null, val change: Long? = null,
)

fun consultaSafebooru(consulta: String): String {
    val etiquetas = consulta.trim().split(Regex("\\s+")).filter { it.isNotBlank() && !it.startsWith("rating:", true) }
    require(etiquetas.size <= 1) { "La API anónima admite una etiqueta además de rating:safe" }
    return ((etiquetas + "rating:safe").joinToString(" ")).trim()
}

fun urlMediaSafebooru(url: String?): String? {
    if (url == null) return null
    return runCatching {
        val uri = URI(url)
        if (uri.scheme == "https" && uri.host == "safebooru.org" && uri.userInfo == null && uri.port in setOf(-1, 443)) uri.toString() else null
    }.getOrNull()
}

fun parseSafebooru(texto: String): List<BooruPost> {
    val datos = Json { ignoreUnknownKeys = true }.decodeFromString<List<DapiPost>>(texto)
    return datos.mapNotNull { post ->
        val original = urlMediaSafebooru(post.file_url) ?: return@mapNotNull null
        val preview = urlMediaSafebooru(post.preview_url) ?: urlMediaSafebooru(post.sample_url) ?: original
        val rating = post.rating.orEmpty()
        if (post.id <= 0 || rating !in setOf("safe", "s")) return@mapNotNull null
        BooruPost(
            id = post.id, url = original, previewUrl = preview,
            tags = post.tags.orEmpty().split(Regex("\\s+")).filter { it.isNotBlank() },
            score = post.score ?: 0, rating = "safe",
            dimensions = "${post.width ?: 0}×${post.height ?: 0}", source = post.source,
            createdAt = post.change ?: 0L,
        )
    }
}

/** Cliente de una sola API comprobada; las respuestas adultas se descartan también en cliente. */
class SafebooruClient(private val client: OkHttpClient = clienteSafebooru()) {
    suspend fun buscar(consulta: String, limite: Int = 20, pagina: Int = 0): BooruSearchResult = withContext(Dispatchers.IO) {
        val tags = runCatching { consultaSafebooru(consulta) }.getOrElse {
            return@withContext BooruSearchResult(SITIO, consulta, limite, emptyList(), false, it.message, pagina)
        }
        if (limite !in 1..50 || pagina !in 0..10_000) {
            return@withContext BooruSearchResult(SITIO, consulta, limite, emptyList(), false, "Límite o página fuera de rango", pagina)
        }
        val url = HttpUrl.Builder().scheme("https").host(SITIO).addPathSegment("index.php")
            .addQueryParameter("page", "dapi").addQueryParameter("s", "post").addQueryParameter("q", "index")
            .addQueryParameter("json", "1").addQueryParameter("limit", limite.toString())
            .addQueryParameter("pid", pagina.toString()).addQueryParameter("tags", tags).build()
        try {
            client.newCall(Request.Builder().url(url).header("Accept", "application/json")
                .header("User-Agent", "cero-X/1.0 (+Safebooru public API; rating:safe)").build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext BooruSearchResult(SITIO, consulta, limite, emptyList(), false, "HTTP ${response.code}", pagina)
                val bytes = response.body.byteStream().readNBytes(MAX_JSON + 1)
                if (bytes.size > MAX_JSON) return@withContext BooruSearchResult(SITIO, consulta, limite, emptyList(), false, "Respuesta mayor de 2 MiB", pagina)
                val posts = runCatching { parseSafebooru(bytes.toString(Charsets.UTF_8)) }.getOrElse {
                    return@withContext BooruSearchResult(SITIO, consulta, limite, emptyList(), false, "JSON no reconocido: ${it.message}", pagina)
                }
                BooruSearchResult(SITIO, consulta, limite, posts, true, null, pagina)
            }
        } catch (e: Exception) {
            BooruSearchResult(SITIO, consulta, limite, emptyList(), false, "No se consultó Safebooru: ${e.message ?: e.javaClass.simpleName}", pagina)
        }
    }

    companion object {
        const val SITIO = "safebooru.org"
        private const val MAX_JSON = 2 * 1024 * 1024
        fun clienteSafebooru(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS)
            .callTimeout(35, TimeUnit.SECONDS).build()
    }
}
