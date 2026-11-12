package com.ceroX.app.modules.web

/**
 * El bloqueador de anuncios y rastreadores.
 *
 * Decide por URL, sin abrir red propia: sufijo de dominio contra las reglas.
 * Las reglas vienen de dos sitios: las incluidas, que son dominios de anuncio y
 * rastreo conocidos, y una lista propia del usuario que se lee del almacenamiento
 * externo aprobado —nunca del interno, que es lo que el proyecto prohíbe— con un
 * formato de un dominio por línea y `#` para comentarios.
 */
object Bloqueador {

    val INCLUIDAS: List<String> = listOf(
        "doubleclick.net", "googleadservices.com", "googlesyndication.com",
        "google-analytics.com", "googletagmanager.com", "facebook.net",
        "adsystem.com", "hotjar.com",
    )

    /** Un dominio por línea; `#` comenta; líneas vacías fuera. */
    fun analizarLista(texto: String): List<String> =
        texto.lineAsSequenceSafe()

    private fun String.lineAsSequenceSafe(): List<String> =
        split('\n').map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() && it.contains('.') && !it.contains(' ') }

    fun debeBloquear(url: String, extra: List<String> = emptyList()): Boolean {
        val host = hostDe(url) ?: return false
        return (INCLUIDAS + extra).any { regla ->
            host == regla || host.endsWith(".$regla")
        }
    }

    /** El host de una URL, sin depender de android.net.Uri: esto corre en JVM. */
    fun hostDe(url: String): String? {
        val sin = url.substringAfter("://", "")
        if (sin.isEmpty() || sin == url) return null
        return sin.substringBefore('/').substringBefore('?').substringBefore(':')
            .lowercase().takeIf { it.isNotEmpty() }
    }
}
