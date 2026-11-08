package com.ceroX.app.modules.reader

import android.graphics.Bitmap
import java.util.concurrent.ConcurrentHashMap

/**
 * Caché de páginas en memoria, con tope en bytes y no en número de páginas.
 *
 * POR QUÉ EN BYTES
 * ----------------
 * Un caché "de 20 páginas" no significa nada: 20 páginas de un webtoon a 1080 px
 * de ancho son 8 MB, y 20 de un manga a doble página en un tablet de 2560 px son
 * 160 MB. Contar páginas es la forma más fácil de comerse la memoria de un móvil
 * sin que nadie entienda por qué.
 *
 * QUÉ SE TOMA DE REIKAI
 * ---------------------
 * La idea, no el código. Reikai es el único de los cuatro con tests de lector
 * reales —`PageSlideTest`, `TiledPictureTest`, `WebtoonResizeAfterZoomTest`,
 * `RenderedLinesParityTest`— que es lo que convierte "va rápido" en algo
 * verificable. Esto aplica la misma disciplina: una caché con política explícita
 * y un `hitRate()` que se puede medir en vez de suponer.
 *
 * POLÍTICA: LRU aproximada.
 * Se descarta la página usada hace más tiempo, pero sin lista enlazada ni
 * sincronización extra: se guarda el instante de último uso y se busca el mínimo.
 * Con 20-30 páginas el barrido cuesta microsegundos y ahorra una estructura que
 * habría que bloquear en cada lectura.
 */
class PageCache(private val topeBytes: Long = 96L * 1024 * 1024) {

    private data class Entrada(val bitmap: Bitmap, val bytes: Long, var ultimoUso: Long)

    private val entradas = ConcurrentHashMap<String, Entrada>()
    private var totalBytes = 0L
    private var aciertos = 0L
    private var fallos = 0L

    /** Cuánto ocupa hoy el caché. */
    fun bytesUsados(): Long = totalBytes

    /** De 0 a 1. Es lo que permite decir "la caché sirve" con un número. */
    fun hitRate(): Float {
        val total = aciertos + fallos
        return if (total == 0L) 0f else aciertos.toFloat() / total
    }

    fun aciertos(): Long = aciertos
    fun fallos(): Long = fallos

    /**
     * Devuelve la página si está. Si no, null — y NO la carga: quien llama decide
     * si merece la pena decodificar, porque decodificar en el hilo de dibujo es
     * la forma más rápida de perder 16 ms por fotograma.
     */
    fun obtener(clave: String): Bitmap? {
        val e = entradas[clave]
        if (e == null) {
            fallos++
            return null
        }
        e.ultimoUso = System.nanoTime()
        aciertos++
        return e.bitmap
    }

    fun guardar(clave: String, bitmap: Bitmap) {
        val bytes = bitmap.allocationByteCount.toLong()
        if (bytes > topeBytes) {
            // Una sola página más grande que todo el caché: no entra. Se dice y
            // se sigue, en vez de vaciar el caché entero por una página enorme.
            return
        }
        while (totalBytes + bytes > topeBytes && entradas.isNotEmpty()) {
            val victima = entradas.entries.minByOrNull { it.value.ultimoUso } ?: break
            totalBytes -= victima.value.bytes
            entradas.remove(victima.key)
        }
        entradas[clave] = Entrada(bitmap, bytes, System.nanoTime())
        totalBytes += bytes
    }

    fun limpiar() {
        entradas.clear()
        totalBytes = 0L
        aciertos = 0L
        fallos = 0L
    }

    /**
     * Libera hasta dejar `objetivo` bytes libres.
     *
     * Se llama desde `onTrimMemory`: cuando el sistema avisa de que va justo de
     * memoria, soltar el caché es preferible a que el sistema mate la app.
     */
    fun recortarHasta(objetivo: Long) {
        while (totalBytes > objetivo && entradas.isNotEmpty()) {
            val victima = entradas.entries.minByOrNull { it.value.ultimoUso } ?: break
            totalBytes -= victima.value.bytes
            entradas.remove(victima.key)
        }
    }

    /** Clave estable para una página: fuente + obra + capítulo + índice. */
    fun clave(fuente: String, obra: String, capitulo: String, pagina: Int): String =
        "$fuente|$obra|$capitulo|$pagina"
}
