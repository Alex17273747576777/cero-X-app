package com.ceroX.app.modules.directories

import kotlinx.serialization.Serializable
import java.net.URI

/** Descubrimiento de enlaces, nunca ejecución ni respaldo de seguridad de terceros. */
@Serializable
data class OrigenDirectorio(
    val id: String,
    val nombre: String,
    val portada: String,
    val revision: String,
)

@Serializable
data class EntradaDirectorio(
    val origen: String,
    val id: String,
    val nombre: String,
    val descripcion: String = "",
    val url: String,
    val detalle: String,
    val categoria: String,
    val etiquetas: Set<String> = emptySet(),
    val plataformas: Set<String> = emptySet(),
    val esAdulto: Boolean = false,
)

val DIRECTORIOS = listOf(
    OrigenDirectorio("brave", "Brave Search", "https://search.brave.com/", "Motor de búsqueda; una consulta no prueba seguridad"),
    OrigenDirectorio("fmhy", "FMHY", "https://fmhy.net/", "Documentación Git y páginas HTML; licencia del repositorio, no de los destinos"),
    OrigenDirectorio("justindex", "JustIndex", "https://justindex.cc/", "HTML curado por categorías; sección 18+ separada"),
    OrigenDirectorio("miyomi", "Miyomi", "https://miyomi.app/", "API JSON apps/extensions/guides; AGPL del sitio no equivale a licencia de apps"),
    OrigenDirectorio("wotaku", "Wotaku", "https://wotaku.wiki/", "Documentación VitePress; aviso explícito de contenido adulto"),
    OrigenDirectorio("everythingmoe", "EverythingMoe", "https://everythingmoe.com/", "Secciones HTML con reseñas propias"),
    OrigenDirectorio("anipirate", "AniPirate", "https://anipirate.xyz/directory", "Directorio SSR; puede responder 403 y se muestra el fallo"),
    OrigenDirectorio("appteka", "Appteka Rebuild", "https://appteka.store/app/1d6r334032", "Ficha independiente; APK no auditado ni descargado"),
    OrigenDirectorio("fmhy_android", "FMHY Android", "https://github.com/omeg4-dev/fmhy-android", "Proyecto GPL-3.0; abre repositorio, no instala"),
    OrigenDirectorio("komi", "Komi Store", "https://github.com/komi-store/komi-store", "Proyecto Apache-2.0; abre repositorio, no instala"),
)

val CATEGORIAS_DIRECTORIO = listOf("Todas", "Anime", "Manga", "Novelas", "Apps", "Herramientas", "Datos", "Guías", "Búsqueda")

fun enlaceSeguro(url: String): String? = runCatching {
    val uri = URI(url)
    val host = uri.host?.lowercase()
    if (uri.scheme != "https" || host.isNullOrBlank()) return null
    if (uri.userInfo != null || host == "localhost" || host == "127.0.0.1" || host == "::1") return null
    if (uri.port !in setOf(-1, 443)) return null
    uri.toString()
}.getOrNull()

fun categorizar(texto: String): String {
    val t = texto.lowercase()
    return when {
        listOf("extension", "software", "app", "apk", "client", "store").any { it in t } -> "Apps"
        listOf("manga", "manhwa", "manhua", "comic", "novel", "book").any { it in t } -> "Manga"
        listOf("anime", "donghua", "video", "stream", "movie", "drama").any { it in t } -> "Anime"
        listOf("guide", "tutorial", "setup", "configuration", "tips").any { it in t } -> "Guías"
        listOf("database", "tracker", "tracking", "anilist", "metadata").any { it in t } -> "Datos"
        listOf("tool", "utility", "download").any { it in t } -> "Herramientas"
        else -> "Otros"
    }
}

fun entradasAuditoria(): List<EntradaDirectorio> = DIRECTORIOS.map {
    val esApp = it.id in setOf("appteka", "fmhy_android", "komi")
    EntradaDirectorio(
        origen = it.id, id = "portada", nombre = it.nombre, descripcion = it.revision,
        url = it.portada, detalle = it.portada, categoria = if (esApp) "Apps" else "Búsqueda",
        etiquetas = setOf("directorio", "enlace-externo"),
    )
}
