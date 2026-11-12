package com.ceroX.app.sources

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import java.io.IOException

/** Resuelve y descarga por el cliente de la fuente, no por un OkHttp genérico. */
object SourceImageLoader {
    suspend fun descargar(fuente: HttpSource, pagina: Page, maximo: Long): ByteArray {
        require(maximo > 0) { "Límite de imagen inválido" }
        if (pagina.imageUrl.isNullOrBlank()) {
            pagina.imageUrl = fuente.getImageUrl(pagina)
        }
        require(!pagina.imageUrl.isNullOrBlank()) { "La fuente devolvió una imagen vacía" }
        // getImage conserva la dispatch virtual de imageRequest y el client
        // concreto de la extensión (cookies, interceptores y cabeceras).
        fuente.getImage(pagina).use { respuesta ->
            val entrada = respuesta.body.byteStream()
            val salida = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = entrada.read(buffer)
                if (n < 0) break
                total += n
                if (total > maximo) throw IOException("La imagen supera el límite de $maximo bytes")
                salida.write(buffer, 0, n)
            }
            if (total == 0L) throw IOException("La fuente devolvió una imagen vacía")
            return salida.toByteArray()
        }
    }
}
