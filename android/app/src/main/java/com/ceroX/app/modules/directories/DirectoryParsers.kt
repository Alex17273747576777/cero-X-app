package com.ceroX.app.modules.directories

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

private val json = Json { ignoreUnknownKeys = true }

@Serializable private data class MiyomiRespuesta(val success: Boolean = false, val data: List<MiyomiItem> = emptyList())
@Serializable private data class MiyomiItem(
    val id: String = "", val slug: String = "", val name: String = "", val title: String = "",
    @SerialName("shortDescription") val descripcionCorta: String? = null,
    val description: String? = null, val author: String? = null, val category: String? = null,
    val language: String? = null, val version: String? = null,
    val platforms: List<String>? = null, val types: List<String>? = null,
    val contentTypes: List<String>? = null, val compatibleWith: List<String>? = null,
    val tags: List<String>? = null, val repoUrl: String? = null, val websiteUrl: String? = null,
    val sourceUrl: String? = null, val downloadUrl: String? = null, val manualUrl: String? = null,
)

fun parseMiyomi(texto: String, tipo: String): List<EntradaDirectorio> {
    val r = json.decodeFromString<MiyomiRespuesta>(texto)
    require(r.success) { "Miyomi respondió success=false" }
    val ruta = if (tipo == "guides") "guides" else if (tipo == "apps") "software" else "extensions"
    return r.data.mapNotNull { item ->
        val nombre = item.name.ifBlank { item.title }.trim()
        val slug = item.slug.trim()
        if (nombre.isBlank() || slug.isBlank()) return@mapNotNull null
        val candidato = listOf(item.repoUrl, item.websiteUrl, item.sourceUrl, item.downloadUrl, item.manualUrl)
            .mapNotNull { it }.firstNotNullOfOrNull { enlaceSeguro(it) }
            ?: "https://miyomi.app/$ruta/$slug"
        val etiquetas = (item.tags.orEmpty() + item.types.orEmpty() + item.contentTypes.orEmpty() + item.compatibleWith.orEmpty() + listOfNotNull(item.language)).toSet()
        val textoCategoria = (listOfNotNull(item.category) + item.types.orEmpty() + item.contentTypes.orEmpty() + item.tags.orEmpty()).joinToString(" ")
        EntradaDirectorio(
            origen = "miyomi", id = item.id.ifBlank { slug }, nombre = nombre,
            descripcion = item.descripcionCorta ?: item.description?.take(500) ?: "",
            url = candidato, detalle = "https://miyomi.app/$ruta/$slug",
            categoria = if (tipo == "guides") "Guías" else categorizar(textoCategoria),
            etiquetas = etiquetas, plataformas = item.platforms.orEmpty().toSet(),
            esAdulto = etiquetas.any { it.lowercase() in setOf("nsfw", "hentai", "18+", "adult") },
        )
    }
}

fun parseJustIndex(html: String): List<EntradaDirectorio> {
    val doc = Jsoup.parse(html, "https://justindex.cc/")
    val salida = mutableListOf<EntradaDirectorio>()
    doc.select("section, main section, div").forEach { bloque ->
        val titulo = bloque.children().firstOrNull { it.tagName() == "header" }
            ?.selectFirst("h2")?.text()?.trim().orEmpty()
        val seccion = bloque.selectFirst("> div.site-grid") ?: return@forEach
        if (titulo.isBlank()) return@forEach
        seccion.select("> .site-card").forEach { card ->
            val a = card.selectFirst("a[href^=/site/]") ?: return@forEach
            val slug = a.attr("href").removePrefix("/site/").substringBefore('/')
            val nombre = card.selectFirst(".site-name")?.text()?.trim().orEmpty()
            val host = card.selectFirst(".site-host")?.text()?.trim().orEmpty()
            if (slug.isBlank() || nombre.isBlank()) return@forEach
            salida += EntradaDirectorio(
                origen = "justindex", id = slug, nombre = nombre, descripcion = host,
                url = "https://$host", detalle = "https://justindex.cc/site/$slug",
                categoria = categorizar(titulo), etiquetas = setOf(titulo),
                esAdulto = titulo.contains("18+", true) || titulo.contains("hentai", true),
            )
        }
    }
    return salida.distinctBy { it.id }
}

fun parseAniPirate(html: String): List<EntradaDirectorio> {
    val doc = Jsoup.parse(html, "https://anipirate.xyz/directory")
    return doc.select("a[href^=/entry/]").mapNotNull { a ->
        val slug = a.attr("href").removePrefix("/entry/").substringBefore('/')
        val h2 = a.selectFirst("h2") ?: return@mapNotNull null
        val nombre = h2.ownText().ifBlank { h2.text() }.trim()
        if (slug.isBlank() || nombre.isBlank()) return@mapNotNull null
        val descripcion = h2.nextElementSibling()?.text()?.trim().orEmpty()
        val visita = a.parent()?.select("a[href]")?.map { it.attr("href") }
            ?.firstOrNull { enlaceSeguro(it) != null && !it.contains("anipirate.xyz") }
            ?: "https://anipirate.xyz/entry/$slug"
        val categoria = a.select("span, p").map { it.text() }.joinToString(" ")
        EntradaDirectorio(
            origen = "anipirate", id = slug, nombre = nombre, descripcion = descripcion,
            url = enlaceSeguro(visita) ?: "https://anipirate.xyz/entry/$slug",
            detalle = "https://anipirate.xyz/entry/$slug", categoria = categorizar("$categoria $descripcion"),
            esAdulto = "$categoria $descripcion".contains(Regex("hentai|nsfw|18\\+", RegexOption.IGNORE_CASE)),
        )
    }.distinctBy { it.id }
}

fun parseEverythingMoe(html: String, seccion: String): List<EntradaDirectorio> {
    val doc = Jsoup.parse(html, "https://everythingmoe.com/section/$seccion")
    return doc.select("a[href^=/s/], a[href^=https://everythingmoe.com/s/]").mapNotNull { a ->
        val slug = a.attr("href").substringAfter("/s/").substringBefore('/')
        val nombre = a.ownText().trim().ifBlank { a.text().trim() }
        if (slug.isBlank() || nombre.isBlank()) return@mapNotNull null
        val contexto = a.parent()?.text().orEmpty()
        EntradaDirectorio(
            origen = "everythingmoe", id = "$seccion/$slug", nombre = nombre, descripcion = contexto.take(500),
            url = "https://everythingmoe.com/s/$slug", detalle = "https://everythingmoe.com/s/$slug",
            categoria = if (seccion == "database") "Datos" else categorizar(seccion),
            etiquetas = setOf(seccion), esAdulto = seccion in setOf("hentai", "adult"),
        )
    }.distinctBy { it.id }
}

fun parseMarkdownDirectorio(texto: String, origen: String, urlDocumento: String, categoria: String): List<EntradaDirectorio> {
    val enlaces = Regex("\\[([^\\[\\]]+)]\\((https://[^)\\s]+)\\)")
    val base = urlDocumento.toHttpUrlOrNull() ?: "https://fmhy.net/".toHttpUrlOrNull()!!
    return texto.lineSequence().mapIndexedNotNull { indice, linea ->
        if (linea.contains("hentai", true) || linea.contains("nsfw", true)) return@mapIndexedNotNull null
        val m = enlaces.find(linea) ?: return@mapIndexedNotNull null
        val nombre = m.groupValues[1].replace(Regex("[*_⭐🌐⁠]"), "").trim()
        val url = enlaceSeguro(m.groupValues[2]) ?: return@mapIndexedNotNull null
        if (nombre.isBlank()) return@mapIndexedNotNull null
        EntradaDirectorio(
            origen = origen, id = "${base.host}:${base.encodedPath}:$indice", nombre = nombre,
            descripcion = linea.replace(Regex("\\s+"), " ").take(500), url = url, detalle = urlDocumento,
            categoria = categoria,
        )
    }.distinctBy { it.url }.toList()
}
