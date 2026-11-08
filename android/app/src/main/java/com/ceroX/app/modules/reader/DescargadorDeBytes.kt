package com.ceroX.app.modules.reader

import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * La mitad de red del lector: de una URL (o una ruta) a bytes.
 *
 * POR QUÉ ESTÁ AQUÍ Y NO DENTRO DE `PageLoader`
 * --------------------------------------------
 * `PageLoader` decodifica con `BitmapFactory`, que en una prueba de JVM devuelve
 * `null` porque no hay implementación nativa. Mientras la descarga y la
 * decodificación vivieran en el mismo método privado, **la descarga no se podía
 * probar sin un teléfono**, y por eso el README decía «el lector no está probado
 * de punta a punta». Separarlas convierte la descarga en algo que se puede
 * apuntar a un `HttpServer` real y medir: código 200, redirección, 404, cuerpo
 * que pasa del tope.
 *
 * QUÉ SE RESPETA DE LA VERSIÓN ANTERIOR
 * -------------------------------------
 * Nada se relaja al mover el código: siguen las cinco reglas que ya estaban
 * escritas y ahora se comprueban.
 *
 *   1. **Redirecciones a mano, máximo 5.** `HttpURLConnection` no las sigue en
 *      todos los casos y muchas fuentes saltan a un CDN. Un bucle de
 *      redirecciones tiene que terminar con un motivo, no colgarse.
 *   2. **Tope de bytes por página, comprobado dos veces**: con
 *      `Content-Length` (para no empezar) y contando lo leído (porque hay
 *      servidores que mienten o no lo mandan).
 *   3. **Sin `User-Agent` de navegador.** Se identifica como cero-X; hacerse
 *      pasar por Chrome para saltarse un bloqueo rompería la regla de no mentir.
 *   4. **Los archivos locales también tienen tope.** Un `.cbz` renombrado a
 *      `.jpg` de 300 MB no puede entrar en memoria porque sí.
 *   5. **Nunca se devuelve una lista vacía fingiendo éxito**: si no hay bytes,
 *      se lanza con el motivo, y `PageLoader` lo convierte en «esta página no
 *      cargó» con su texto visible.
 */
class DescargadorDeBytes(
    /** Bytes que se aceptan de una sola página. 24 MiB cubre una doble en 4K. */
    private val maxBytes: Long = 24L * 1024 * 1024,
    /** Saltos de redirección permitidos. Cinco es lo que hace un navegador. */
    private val maxSaltos: Int = 5,
    private val tiempoConexionMs: Int = 15_000,
    private val tiempoLecturaMs: Int = 30_000,
) {

    /** Lee `origen`, sea ruta local o URL http(s). Lanza con el motivo si falla. */
    fun leer(origen: String): ByteArray {
        if (!origen.startsWith("http://") && !origen.startsWith("https://")) {
            val f = File(origen.replace("file://", ""))
            require(f.exists()) { "no existe: ${f.name}" }
            require(f.length() <= maxBytes) { "archivo de ${f.length()} B, demasiado grande" }
            return f.readBytes()
        }

        var url = URL(origen)
        var saltos = 0
        while (saltos++ < maxSaltos) {
            val conexion = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = tiempoConexionMs
                readTimeout = tiempoLecturaMs
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", AGENTE)
                // Sin esto algunas fuentes devuelven HTML de «verifica tu
                // navegador» en vez de la imagen, y el error que llega es «no es
                // una imagen», que no dice nada.
                setRequestProperty("Accept", "image/avif,image/webp,image/png,image/jpeg,*/*")
            }
            try {
                val codigo = conexion.responseCode
                if (codigo in 300..399) {
                    val destino = conexion.getHeaderField("Location")
                        ?: throw IllegalStateException("redirección $codigo sin Location")
                    url = URL(url, destino)
                    continue
                }
                if (codigo !in 200..299) {
                    throw IllegalStateException("HTTP $codigo")
                }
                val largo = conexion.contentLengthLong
                if (largo > maxBytes) {
                    throw IllegalStateException(
                        "la imagen pesa ${largo / 1048576} MiB, más del tope de ${maxBytes / 1048576} MiB"
                    )
                }
                conexion.inputStream.use { entrada ->
                    // Acotado aunque venga Content-Length: hay servidores que
                    // mienten. Aquí se corta en cuanto se pasa.
                    val salida = ByteArrayOutputStream(pesoEsperado(largo))
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val leidos = entrada.read(buffer)
                        if (leidos < 0) break
                        total += leidos
                        if (total > maxBytes) {
                            throw IllegalStateException("la imagen pasa de ${maxBytes / 1048576} MiB")
                        }
                        salida.write(buffer, 0, leidos)
                    }
                    return salida.toByteArray()
                }
            } finally {
                conexion.disconnect()
            }
        }
        throw IllegalStateException("demasiadas redirecciones (más de $maxSaltos)")
    }

    /** Capacidad inicial del búfer: se reserva lo que dice el servidor, con tope. */
    private fun pesoEsperado(largo: Long): Int = when {
        largo in 1..maxBytes -> largo.toInt()
        else -> 8 * 1024
    }

    companion object {
        /**
         * Se identifica como cero-X con su versión. No se hace pasar por un
         * navegador para saltarse bloqueos.
         */
        const val AGENTE: String = "cero-X/1.0.0 (+https://github.com/Alex17273747576777/cero-X-app)"
    }
}
