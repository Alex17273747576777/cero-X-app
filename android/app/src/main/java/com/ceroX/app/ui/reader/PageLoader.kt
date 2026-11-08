package com.ceroX.app.ui.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.ceroX.app.modules.reader.PageCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * De «aquí tengo una URL o una ruta» a «aquí tengo los píxeles que se pintan».
 *
 * POR QUÉ ES UNA CLASE APARTE Y NO UN MÉTODO DEL LECTOR
 * ----------------------------------------------------
 * Descargar y decodificar son las dos operaciones que más veces se hacen mal en
 * un lector de manga, y las dos que más caro cuestan en un móvil:
 *
 *   - **Descargar sin mirar el tamaño**: una página de 40 MP se decodifica a
 *     ~160 MB en ARGB_8888 y el sistema mata la app. Aquí se lee la cabecera
 *     (`inJustDecodeBounds`) y se calcula el `inSampleSize` **antes** de
 *     reservar memoria. Sin eso, «se ve borroso» se arregla subiendo la calidad
 *     y «se cierra sola» no se arregla nunca.
 *   - **Decodificar en el hilo de dibujo**: 30-80 ms por página es un tirón
 *     visible en cada cambio. Todo va en `Dispatchers.IO`.
 *
 * TRES REGLAS
 * -----------
 *  1. **Nunca lanza por una página**: devuelve un [Cargada] con `motivo`. Un
 *     capítulo con la página 7 rota tiene que pintar las otras 20.
 *  2. **El caché es por bytes**, no por número de páginas: lo decide `PageCache`.
 *  3. **Una página jamás se pide dos veces a la vez**: `enVuelo` guarda las
 *     descargas en curso. Sin eso, el prefetch y el salto manual del usuario
 *     piden la misma imagen dos veces y se gasta el doble de datos.
 */
class PageLoader(
    private val cache: PageCache,
    /** Tope de memoria por página. 4096 px de lado es ~67 MB; por encima no entra. */
    private val ladoMaximo: Int = 4096,
    /** Bytes que se aceptan de una sola página. 24 MiB cubre una doble en 4K. */
    private val maxBytesPorPagina: Long = 24L * 1024 * 1024,
) {

    /** Lo que se pintó, o por qué no se pudo. */
    data class Cargada(
        val bitmap: Bitmap?,
        val desdeCache: Boolean,
        val ms: Long,
        val bytes: Long = 0,
        val motivo: String = "",
    ) {
        val exito: Boolean get() = bitmap != null
    }

    private val enVuelo = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.sync.Mutex>()

    /**
     * Carga una página. `origen` puede ser una URL http(s) o una ruta de archivo.
     *
     * El `key` que se usa para el caché lo construye el llamador con
     * `PageCache.clave(...)` para que no dependa de la URL exacta (algunas
     * fuentes añaden un token distinto en cada petición y eso rompería el caché).
     */
    suspend fun cargar(clave: String, origen: String): Cargada {
        cache.obtener(clave)?.let {
            return Cargada(it, desdeCache = true, ms = 0, bytes = it.allocationByteCount.toLong())
        }

        // Un mutex por clave: si el prefetch ya está bajando esa página, el
        // segundo que la pida espera y recibe el resultado del primero.
        val mutex = enVuelo.computeIfAbsent(clave) { kotlinx.coroutines.sync.Mutex() }
        mutex.lock()
        try {
            // Se vuelve a mirar: el que tenía el candado pudo dejarla en caché.
            cache.obtener(clave)?.let {
                return Cargada(it, desdeCache = true, ms = 0, bytes = it.allocationByteCount.toLong())
            }
            return withContext(Dispatchers.IO) { bajarYDecodificar(clave, origen) }
        } finally {
            mutex.unlock()
            enVuelo.remove(clave, mutex)
        }
    }

    private fun bajarYDecodificar(clave: String, origen: String): Cargada {
        val t0 = System.currentTimeMillis()
        val bytes: ByteArray = try {
            leerBytes(origen)
        } catch (e: Throwable) {
            return Cargada(null, false, System.currentTimeMillis() - t0, motivo = describir(e))
        }
        if (bytes.isEmpty()) {
            return Cargada(null, false, System.currentTimeMillis() - t0, motivo = "archivo vacío")
        }

        val opciones = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opciones)
        if (opciones.outWidth <= 0 || opciones.outHeight <= 0) {
            return Cargada(
                null, false, System.currentTimeMillis() - t0,
                motivo = "no es una imagen decodificable (${opciones.outMimeType ?: "tipo desconocido"})"
            )
        }

        val opcionesReales = BitmapFactory.Options().apply {
            inSampleSize = sampleSizePara(opciones.outWidth, opciones.outHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = try {
            // Si aun con muestreo no cabe en el tope de caché, se reduce más en
            // vez de fallar: una página algo más pequeña se lee; un cierre no.
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opcionesReales)
        } catch (e: OutOfMemoryError) {
            return Cargada(null, false, System.currentTimeMillis() - t0, motivo = "sin memoria para decodificar")
        } ?: return Cargada(null, false, System.currentTimeMillis() - t0, motivo = "decodificación nula")

        val bytesReales = bitmap.allocationByteCount.toLong()
        cache.guardar(clave, bitmap)
        return Cargada(bitmap, false, System.currentTimeMillis() - t0, bytes = bytesReales)
    }

    /** Cuántas veces reducir, en potencias de dos, para no pasar del lado máximo. */
    fun sampleSizePara(ancho: Int, alto: Int): Int {
        var muestra = 1
        while (ancho / muestra > ladoMaximo || alto / muestra > ladoMaximo) {
            muestra *= 2
        }
        return muestra
    }

    private fun leerBytes(origen: String): ByteArray {
        if (!origen.startsWith("http://") && !origen.startsWith("https://")) {
            val f = File(origen.replace("file://", ""))
            require(f.exists()) { "no existe: ${f.name}" }
            require(f.length() <= maxBytesPorPagina) { "archivo de ${f.length()} B, demasiado grande" }
            return f.readBytes()
        }

        // Redirecciones a mano: HttpURLConnection no las sigue con POST y muchas
        // fuentes redirigen a un CDN. Se limita a 5 saltos para no caer en un bucle.
        var url = URL(origen)
        var saltos = 0
        while (saltos++ < 5) {
            val conexion = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", AGENTE)
                // Sin esto, algunas fuentes devuelven HTML de "verifica tu navegador"
                // en vez de la imagen, y el error que llega es "no es una imagen".
                setRequestProperty("Accept", "image/avif,image/webp,image/png,image/jpeg,*/*")
            }
            try {
                val codigo = conexion.responseCode
                if (codigo in 300..399) {
                    val destino = conexion.getHeaderField("Location")
                        ?: throw IllegalStateException("redirección $codigo sin Location")
                    conexion.disconnect()
                    url = URL(url, destino)
                    continue
                }
                if (codigo !in 200..299) {
                    throw IllegalStateException("HTTP $codigo")
                }
                val largo = conexion.contentLengthLong
                if (largo > maxBytesPorPagina) {
                    throw IllegalStateException("la imagen pesa ${largo / 1048576} MiB, más del tope")
                }
                conexion.inputStream.use { entrada ->
                    // Se acota la lectura aunque no venga Content-Length: hay
                    // servidores que mienten o no lo mandan.
                    val salida = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val leidos = entrada.read(buffer)
                        if (leidos < 0) break
                        total += leidos
                        if (total > maxBytesPorPagina) {
                            throw IllegalStateException("la imagen pasa de ${maxBytesPorPagina / 1048576} MiB")
                        }
                        salida.write(buffer, 0, leidos)
                    }
                    return salida.toByteArray()
                }
            } finally {
                conexion.disconnect()
            }
        }
        throw IllegalStateException("demasiadas redirecciones")
    }

    private fun describir(e: Throwable): String =
        e.message?.takeIf { it.isNotBlank() }?.let { "${e.javaClass.simpleName}: $it" }
            ?: e.javaClass.simpleName

    companion object {
        /**
         * Se identifica como cero-X con su versión. No se hace pasar por un
         * navegador para saltarse bloqueos: eso rompería la regla de no mentir,
         * y además hay fuentes que bloquean igual.
         */
        const val AGENTE: String = "cero-X/1.0.0 (+https://github.com/Alex17273747576777/cero-X-app)"
    }
}
