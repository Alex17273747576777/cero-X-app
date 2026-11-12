package com.ceroX.app.modules.extension_sandbox

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Instantáneas inmutables auditadas; no se sigue `repo` ni `latest` implícitamente. */
data class RepositorioAuditado(
    val id: String,
    val nombre: String,
    val commit: String,
    val indice: String,
    val baseApk: String? = null,
    val manifiestoSha256: String? = null,
    val certificadoSha256: String? = null,
) {
    fun urlApk(nombreArchivo: String): String {
        require(nombreArchivo.matches(Regex("[A-Za-z0-9._-]+\\.apk"))) { "Nombre de APK no permitido" }
        return "${requireNotNull(baseApk)}$nombreArchivo"
    }
}

object RepositoriosAuditados {
    const val FIRMA_PROJECT_NOX = "144f44e865cbfae6bde110489ef7ec142f0b1681f627a093a218650e21db5dad"
    private const val RAW = "https://raw.githubusercontent.com"

    val MANGA = RepositorioAuditado(
        "nox-manga", "Project Nox · manga", "55b9127ee947795cb76944e3e55f019066c72cba",
        "$RAW/Awerkori/manga-extensoes/55b9127ee947795cb76944e3e55f019066c72cba/index.min.json",
        "$RAW/Awerkori/manga-extensoes/55b9127ee947795cb76944e3e55f019066c72cba/apk/",
        "$RAW/Awerkori/manga-extensoes/55b9127ee947795cb76944e3e55f019066c72cba/release-assets.json",
        FIRMA_PROJECT_NOX,
    )
    val ANIME = RepositorioAuditado(
        "nox-anime", "Project Nox · anime", "d2b689f3fb1e411b390bb8275ec86a65f7de0b17",
        "$RAW/Awerkori/anime-extensoes/d2b689f3fb1e411b390bb8275ec86a65f7de0b17/index.min.json",
        "$RAW/Awerkori/anime-extensoes/d2b689f3fb1e411b390bb8275ec86a65f7de0b17/apk/",
        certificadoSha256 = FIRMA_PROJECT_NOX,
    )
    val KOTATSU = listOf(
        "Kotatsu-Redo", "Gekkoushi", "glitch-228", "hany18h", "skepsun",
    ).map { origen ->
        RepositorioAuditado(
            "kotatsu-$origen", "Kotatsu · $origen", "5a4e2bfcf61c583fed759abe289004a99991e623",
            "$RAW/Shebyyy/kotatsu-multi-parsers/5a4e2bfcf61c583fed759abe289004a99991e623/index/$origen.json",
        )
    }
}

data class FuenteIndice(
    val nombre: String,
    val idioma: String,
    val id: Long? = null,
    val baseUrl: String = "",
)

data class ExtensionIndice(
    val repositorio: String,
    val nombre: String,
    val paquete: String,
    val apk: String,
    val version: String,
    val codigo: Long,
    val idioma: String,
    val nsfw: Boolean,
    val fuentes: List<FuenteIndice>,
    val sha256: String? = null,
    val downloadUrl: String? = null,
)

data class FuenteKotatsu(
    val repositorio: String,
    val nombre: String,
    val titulo: String,
    val idioma: String,
    val tipo: String,
    val nsfw: Boolean,
    val rota: Boolean,
    val baseUrl: String,
)

/** Parsing puro y estricto: los índices no crean objetos ejecutables ni ClassLoaders. */
object ParserIndices {
    fun nox(
        repo: RepositorioAuditado,
        json: String,
        hashes: Map<String, String> = emptyMap(),
        descargas: Map<String, String> = emptyMap(),
    ): List<ExtensionIndice> {
        val raiz = JsonParser.parseString(json)
        require(raiz.isJsonArray) { "El índice no es un array" }
        val array = raiz.asJsonArray
        require(array.size() <= 10_000) { "Índice excesivo" }
        return array.map { item ->
            val o = item.asJsonObject
            val apk = texto(o, "apk")
            require(apk.matches(Regex("[A-Za-z0-9._-]+\\.apk"))) { "APK no permitido" }
            val paquete = texto(o, "pkg")
            require(paquete.matches(Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+"))) { "Paquete inválido" }
            val fuentes = o.getAsJsonArray("sources")?.take(200)?.map { fuente ->
                val f = fuente.asJsonObject
                val id = f.get("id")?.takeUnless { it.isJsonNull }?.asString?.toLongOrNull()
                val base = f.get("baseUrl")?.takeUnless { it.isJsonNull }?.asString.orEmpty()
                require(base.isEmpty() || urlHttp(base)) { "URL de fuente inválida o con credenciales" }
                FuenteIndice(texto(f, "name"), texto(f, "lang"), id, base)
            }.orEmpty()
            require(fuentes.isNotEmpty()) { "Extensión sin fuentes" }
            ExtensionIndice(
                repo.id, texto(o, "name"), paquete, apk, texto(o, "version"),
                o.get("code").asLong.also { require(it >= 0) }, texto(o, "lang"),
                o.get("nsfw")?.asInt == 1, fuentes,
                hashes[paquete]?.also { require(it.matches(Regex("[0-9a-f]{64}"))) },
                descargas[apk]?.also { require(httpsVacia(it)) },
            )
        }
    }

    fun urlsNox(json: String): Map<String, String> {
        val o = JsonParser.parseString(json).asJsonObject
        require(o.size() <= 10_000) { "Mapa de descargas excesivo" }
        return o.entrySet().associate { (apk, url) ->
            require(apk.matches(Regex("[A-Za-z0-9._-]+\\.apk")) && httpsVacia(url.asString))
            apk to url.asString
        }
    }

    fun hashesNox(json: String): Map<String, String> {
        val o = JsonParser.parseString(json).asJsonObject
        require(o.size() <= 10_000) { "Manifiesto excesivo" }
        return o.entrySet().mapNotNull { (paquete, valor) ->
            val sha = valor.asJsonObject.getAsJsonObject("apk")?.get("sha256")?.asString?.lowercase()
            if (sha?.matches(Regex("[0-9a-f]{64}")) == true) paquete to sha else null
        }.toMap()
    }

    fun kotatsu(repo: RepositorioAuditado, json: String): List<FuenteKotatsu> {
        val array = JsonParser.parseString(json).asJsonArray
        require(array.size() <= 20_000) { "Índice excesivo" }
        return array.map { item ->
            val o = item.asJsonObject
            val base = o.get("baseUrl")?.asString.orEmpty()
            require(base.isEmpty() || urlHttp(base)) { "URL de fuente inválida o con credenciales" }
            FuenteKotatsu(
                repo.id, texto(o, "name"), texto(o, "title"),
                o.get("lang")?.asString?.trim()?.ifBlank { "desconocido" } ?: "desconocido",
                o.get("contentType")?.asString.orEmpty().ifBlank { "DESCONOCIDO" },
                o.get("nsfw")?.asInt == 1, o.get("isBroken")?.asBoolean == true, base,
            )
        }
    }

    private fun texto(o: JsonObject, clave: String): String = o.get(clave)?.asString?.trim().orEmpty()
        .also { require(it.isNotEmpty() && it.length <= 500) { "Falta $clave o es excesivo" } }

    private fun httpsVacia(url: String): Boolean = urlHttp(url) && URI(url).scheme == "https"

    private fun urlHttp(url: String): Boolean = try {
        val uri = URI(url)
        uri.scheme in setOf("https", "http") && !uri.host.isNullOrBlank() && uri.userInfo == null
    } catch (_: Exception) { false }
}

fun interface DescargadorRepositorio {
    fun descargar(url: String, destino: File, maximo: Long)
}

class DescargadorHttps : DescargadorRepositorio {
    override fun descargar(url: String, destino: File, maximo: Long) {
        val uri = URI(url)
        require(
            uri.scheme == "https" && uri.userInfo == null &&
                (uri.host == "raw.githubusercontent.com" || uri.host == "github.com" || uri.host == "api.github.com"),
        ) { "Sólo se permiten instantáneas y releases HTTPS de GitHub auditadas" }
        val conexion = uri.toURL().openConnection() as HttpURLConnection
        try {
            conexion.connectTimeout = 15_000
            conexion.readTimeout = 60_000
            // GitHub Releases redirige a su CDN HTTPS. No se envían credenciales;
            // el hash y el certificado se comprueban después, no se confía en la redirección.
            conexion.instanceFollowRedirects = true
            conexion.setRequestProperty("User-Agent", "cero-X-extensiones-auditadas")
            conexion.setRequestProperty("Accept", "application/json, application/octet-stream")
            val codigo = conexion.responseCode
            if (codigo !in 200..299) throw IOException("HTTP $codigo al descargar $uri")
            val temporal = File(destino.parentFile, "${destino.name}.part")
            try {
                conexion.inputStream.use { entrada -> copiarLimitado(entrada, temporal, maximo) }
                if (destino.exists() && !destino.delete()) throw IOException("No se pudo sustituir la descarga anterior")
                if (!temporal.renameTo(destino)) throw IOException("No se pudo finalizar la descarga")
            } finally { temporal.delete() }
        } finally { conexion.disconnect() }
    }

    private fun copiarLimitado(entrada: InputStream, destino: File, maximo: Long) {
        destino.outputStream().buffered().use { salida ->
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = entrada.read(buffer)
                if (n < 0) break
                total += n
                if (total > maximo) throw IOException("La descarga supera el límite de $maximo bytes")
                salida.write(buffer, 0, n)
            }
        }
    }
}

class ClienteRepositorios(
    private val descargador: DescargadorRepositorio = DescargadorHttps(),
    private val urlsManga: Map<String, String> = emptyMap(),
    private val cacheUrlsManga: File? = null,
) {
    fun nox(repo: RepositorioAuditado): List<ExtensionIndice> {
        val indice = texto(repo.indice, 12L * 1024 * 1024)
        val hashes = repo.manifiestoSha256?.let { ParserIndices.hashesNox(texto(it, 4L * 1024 * 1024)) }.orEmpty()
        val urls = if (repo.id == RepositoriosAuditados.MANGA.id) urlsManga else emptyMap()
        return ParserIndices.nox(repo, indice, hashes, urls).also {
            if (repo.id == RepositoriosAuditados.MANGA.id) {
                require(it.all { extension -> extension.sha256 != null }) {
                    "El manifiesto auditado no contiene SHA-256 para todas las extensiones"
                }
            }
        }
    }

    /** Resuelve una release únicamente para una instalación solicitada. */
    fun resolverUrlManga(extension: ExtensionIndice): String {
        require(extension.repositorio == RepositoriosAuditados.MANGA.id) { "No es una extensión Nox Manga" }
        extension.downloadUrl?.let { return it }
        cacheUrlsManga?.let { cache ->
            if (cache.isFile && cache.length() in 1..(4L * 1024 * 1024)) {
                runCatching { ParserIndices.urlsNox(cache.readText())[extension.apk] }
                    .getOrNull()?.let { return it }
            }
        }
        val urls = descargarUrlsManga()
        val resuelta = urls[extension.apk]
            ?: throw IOException("No hay una release pública para ${extension.apk}")
        cacheUrlsManga?.let { cache ->
            val temporal = File(cache.parentFile, "${cache.name}.tmp")
            try {
                cache.parentFile?.mkdirs()
                val json = JsonObject().apply { urls.forEach { (apk, url) -> addProperty(apk, url) } }.toString()
                require(json.length <= 4 * 1024 * 1024) { "Caché de releases excesiva" }
                temporal.writeText(json)
                if (cache.exists() && !cache.delete()) throw IOException("No se pudo renovar la caché de releases")
                if (!temporal.renameTo(cache)) throw IOException("No se pudo guardar la caché de releases")
            } finally { temporal.delete() }
        }
        return resuelta
    }

    fun kotatsu(): List<FuenteKotatsu> = RepositoriosAuditados.KOTATSU.flatMap { repo ->
        ParserIndices.kotatsu(repo, texto(repo.indice, 8L * 1024 * 1024))
    }

    /** El catálogo no se empaqueta: se consulta la API pública y vive sólo en memoria. */
    private fun descargarUrlsManga(): Map<String, String> {
        val urls = mutableMapOf<String, String>()
        for (pagina in 1..20) {
            val json = texto(
                "https://api.github.com/repos/Awerkori/manga-extensoes/releases?per_page=25&page=$pagina",
                64L * 1024 * 1024,
            )
            val releases = JsonParser.parseString(json).asJsonArray
            releases.forEach { release ->
                release.asJsonObject.getAsJsonArray("assets")?.forEach { asset ->
                    val a = asset.asJsonObject
                    val nombre = a.get("name")?.asString.orEmpty()
                    val url = a.get("browser_download_url")?.asString.orEmpty()
                    if (nombre.endsWith(".apk") && url.startsWith("https://github.com/Awerkori/manga-extensoes/releases/download/")) {
                        urls[nombre] = url
                    }
                }
            }
            if (releases.size() < 25) break
            if (pagina == 20) throw IOException("El inventario de releases supera el límite auditado")
        }
        return urls
    }

    private fun texto(url: String, maximo: Long): String {
        val temporal = File.createTempFile("cerox-index-", ".json")
        return try {
            descargador.descargar(url, temporal, maximo)
            temporal.readText(StandardCharsets.UTF_8)
        } finally { temporal.delete() }
    }
}

fun sha256(archivo: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    archivo.inputStream().buffered().use { entrada ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = entrada.read(buffer)
            if (n < 0) break
            digest.update(buffer, 0, n)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
