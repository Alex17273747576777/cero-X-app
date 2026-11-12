package com.ceroX.app.modules.directories

import android.content.Context
import com.ceroX.app.core.AlmacenExterno
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

private val jsonCache = Json { ignoreUnknownKeys = true; prettyPrint = false }

@Serializable private data class CacheDirectorio(
    val version: Int = 1, val origen: String, val recurso: String,
    val guardadoEn: Long, val entradas: List<EntradaDirectorio>,
)

data class ResultadoDirectorio(
    val origen: String, val entradas: List<EntradaDirectorio>,
    val desdeCache: Boolean, val guardadoEn: Long?, val aviso: String? = null,
)

data class PeticionDirectorio(val url: String, val recurso: String)

private data class Recurso(val peticion: PeticionDirectorio, val parsear: (String) -> List<EntradaDirectorio>)

fun recursosDirectorio(origen: String): List<PeticionDirectorio> = when (origen) {
    "miyomi" -> listOf("apps", "extensions", "guides").map { PeticionDirectorio("https://miyomi.app/api/$it", it) }
    "justindex" -> listOf(PeticionDirectorio("https://justindex.cc/", "inicio"))
    "everythingmoe" -> listOf("anime", "manga", "novel", "apps", "database", "utils").map {
        PeticionDirectorio("https://everythingmoe.com/section/$it", it)
    }
    "anipirate" -> listOf(PeticionDirectorio("https://anipirate.xyz/directory", "directorio"))
    "wotaku" -> listOf("websites" to "Sitios", "software" to "Apps", "faq" to "Guías").map { (archivo, categoria) ->
        PeticionDirectorio("https://raw.githubusercontent.com/wotakumoe/wotaku/main/docs/$archivo.md", categoria)
    }
    "fmhy" -> listOf("gaming" to "Juegos", "reading" to "Manga", "video" to "Anime", "mobile" to "Apps").map { (archivo, categoria) ->
        PeticionDirectorio("https://raw.githubusercontent.com/fmhy/edit/main/docs/$archivo.md", categoria)
    }
    else -> emptyList()
}

/** HTTP limitado, caché externa y datos diferenciados de enlaces de auditoría. */
class DirectoryRepository(context: Context, private val client: OkHttpClient = clienteDirectorio()) {
    private val directorio: File? = AlmacenExterno.directorio(context)?.let { File(it, "directorios").apply { mkdirs() } }
    var ultimoAviso: String? = null
        private set

    suspend fun cargar(origen: String, forzarRed: Boolean = false): ResultadoDirectorio = withContext(Dispatchers.IO) {
        ultimoAviso = null
        if (origen in setOf("brave", "appteka", "fmhy_android", "komi")) {
            return@withContext ResultadoDirectorio(origen, entradasAuditoria().filter { it.origen == origen }, false, null)
        }
        val recursos = recursosDirectorio(origen)
        if (recursos.isEmpty()) return@withContext ResultadoDirectorio(origen, emptyList(), false, null, "Origen desconocido")
        if (!forzarRed) {
            val cache = leerTodo(origen, recursos)
            if (cache != null) return@withContext cache
        }
        val salida = mutableListOf<EntradaDirectorio>()
        val errores = mutableListOf<String>()
        for (p in recursos) {
            try {
                val texto = descargar(p.url)
                val entradas = parsear(p, texto)
                if (entradas.isEmpty()) throw IllegalStateException("0 entradas reconocidas")
                guardar(origen, p.recurso, entradas)
                salida += entradas
            } catch (e: Exception) {
                val cache = leer(origen, p.recurso)
                if (cache != null) salida += cache.entradas
                errores += "${p.recurso}: ${e.message ?: e.javaClass.simpleName}"
            }
        }
        val aviso = buildString {
            if (directorio == null) append("Sin almacenamiento externo; esta sincronización no se guardará. ")
            if (errores.isNotEmpty()) append("Fallo en ${errores.joinToString(" | ")}. ")
            if (salida.isNotEmpty() && errores.isNotEmpty()) append("Se muestran datos completos o caché disponible.")
        }.ifBlank { null }
        ultimoAviso = aviso
        ResultadoDirectorio(origen, salida.distinctBy { "${it.origen}:${it.id}" }, false,
            if (errores.isEmpty()) System.currentTimeMillis() else null, aviso)
    }

    fun cachesLocales(): List<EntradaDirectorio> = DIRECTORIOS.flatMap { origen ->
        recursosDirectorio(origen.id).flatMap { leer(origen.id, it.recurso)?.entradas.orEmpty() }
    }

    private fun parsear(p: PeticionDirectorio, texto: String): List<EntradaDirectorio> = when {
        p.url.startsWith("https://miyomi.app/") -> parseMiyomi(texto, p.recurso)
        p.url.startsWith("https://justindex.cc/") -> parseJustIndex(texto)
        p.url.startsWith("https://everythingmoe.com/") -> parseEverythingMoe(texto, p.recurso)
        p.url.startsWith("https://anipirate.xyz/") -> parseAniPirate(texto)
        p.url.contains("/wotaku/") -> parseMarkdownDirectorio(texto, "wotaku", "https://wotaku.wiki/${p.recurso}", p.recurso)
        p.url.contains("/fmhy/") -> parseMarkdownDirectorio(texto, "fmhy", "https://fmhy.net/${p.recurso}", p.recurso)
        else -> emptyList()
    }

    private fun descargar(url: String): String {
        val request = Request.Builder().url(enlaceSeguro(url) ?: throw IllegalArgumentException("URL no segura"))
            .header("Accept", "application/json, text/html, text/plain;q=0.9")
            .header("User-Agent", "cero-X/1.0 (+directory reader; manual refresh)").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
            val bytes = response.body.byteStream().readNBytes(MAX_RESPUESTA + 1)
            if (bytes.size > MAX_RESPUESTA) throw IllegalStateException("respuesta supera 4 MiB")
            return bytes.toString(Charsets.UTF_8)
        }
    }

    private fun archivo(origen: String, recurso: String): File? = directorio?.let {
        val limpio = Regex("[^a-zA-Z0-9_-]").replace(recurso, "_")
        File(it, "${origen}_$limpio.json")
    }

    private fun guardar(origen: String, recurso: String, entradas: List<EntradaDirectorio>) {
        val destino = archivo(origen, recurso) ?: return
        val tmp = File(destino.parentFile, destino.name + ".tmp")
        val datos = CacheDirectorio(origen = origen, recurso = recurso, guardadoEn = System.currentTimeMillis(), entradas = entradas)
        tmp.writeText(jsonCache.encodeToString(CacheDirectorio.serializer(), datos))
        if (!tmp.renameTo(destino)) {
            destino.writeText(tmp.readText())
            tmp.delete()
        }
    }

    private fun leer(origen: String, recurso: String): CacheDirectorio? = runCatching {
        val f = archivo(origen, recurso) ?: return null
        if (!f.isFile || f.length() > MAX_CACHE) return null
        jsonCache.decodeFromString(CacheDirectorio.serializer(), f.readText()).takeIf { it.origen == origen && it.recurso == recurso }
    }.getOrNull()

    private fun leerTodo(origen: String, recursos: List<PeticionDirectorio>): ResultadoDirectorio? {
        val caches = recursos.map { leer(origen, it.recurso) ?: return null }
        return ResultadoDirectorio(origen, caches.flatMap { it.entradas }, true, caches.minOf { it.guardadoEn })
    }

    companion object {
        private const val MAX_RESPUESTA = 4 * 1024 * 1024
        private const val MAX_CACHE = 8L * 1024 * 1024
        fun clienteDirectorio(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS)
            .callTimeout(35, TimeUnit.SECONDS).build()
    }
}

fun filtrarDirectorios(entradas: List<EntradaDirectorio>, consulta: String, origen: String, categoria: String, adulto: Boolean): List<EntradaDirectorio> {
    val q = consulta.trim().lowercase()
    return entradas.filter {
        (origen == "Todos" || it.origen == origen) &&
            (categoria == "Todas" || it.categoria == categoria) &&
            (adulto || !it.esAdulto) &&
            (q.isBlank() || listOf(it.nombre, it.descripcion, it.categoria, it.url, it.etiquetas.joinToString(" "), it.plataformas.joinToString(" "))
                .joinToString(" ").lowercase().contains(q))
    }
}
