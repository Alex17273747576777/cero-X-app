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

    /**
     * EL FALLO QUE HABÍA AQUÍ, PARA NO REPETIRLO
     * ------------------------------------------
     * Antes eran `private var totalBytes = 0L` y dos `Long` más de contadores,
     * junto a un `ConcurrentHashMap` que sí era concurrente. La mezcla es la peor
     * de las dos: da sensación de seguridad sin tenerla.
     *
     * Con 8 hilos guardando y leyendo, el estrés encontró tres síntomas del mismo
     * problema:
     *   - el contador llegó a 2 098 176 B con un tope de 2 097 152 B: por encima
     *   - tras `recortarHasta(0)` quedaban 15 979 520 B contados
     *   - tras 5 000 inserciones el contador marcaba el tope entero con una sola
     *     página dentro
     * Los tres son el mismo fallo: `totalBytes += x` y `totalBytes -= x` desde
     * varios hilos pierden actualizaciones (no es atómico), y el mapa y el
     * contador dejan de describir la misma realidad. Con el contador por debajo
     * de lo real, la caché **se pasa del presupuesto de memoria**: exactamente lo
     * que no puede hacer. En un móvil de gama baja eso es que el sistema mate la
     * app en mitad del capítulo.
     *
     * Un candado sobre todo (mapa + contador) es más lento que un mapa concurrente
     * y es lo correcto: lo que hay que proteger son las DOS cosas a la vez, no
     * cada una por su cuenta. El coste real es despreciable —se hace una operación
     * por cambio de página, no por fotograma— y la alternativa era seguir
     * mintiendo en el contador.
     */
    private val candado = Any()
    private val entradas = HashMap<String, Entrada>()
    private var totalBytes = 0L
    private var aciertos = 0L
    private var fallos = 0L

    /** Cuánto ocupa hoy el caché. */
    fun bytesUsados(): Long = synchronized(candado) { totalBytes }

    /** De 0 a 1. Es lo que permite decir "la caché sirve" con un número. */
    fun hitRate(): Float = synchronized(candado) {
        val total = aciertos + fallos
        if (total == 0L) 0f else aciertos.toFloat() / total
    }

    fun aciertos(): Long = synchronized(candado) { aciertos }
    fun fallos(): Long = synchronized(candado) { fallos }

    /**
     * Devuelve la página si está. Si no, null — y NO la carga: quien llama decide
     * si merece la pena decodificar, porque decodificar en el hilo de dibujo es
     * la forma más rápida de perder 16 ms por fotograma.
     */
    fun obtener(clave: String): Bitmap? = synchronized(candado) {
        val e = entradas[clave]
        if (e == null) {
            fallos++
            null
        } else {
            e.ultimoUso = System.nanoTime()
            aciertos++
            e.bitmap
        }
    }

    fun guardar(clave: String, bitmap: Bitmap) = synchronized(candado) {
        val bytes = bitmap.allocationByteCount.toLong()
        if (bytes > topeBytes) {
            // Una sola página más grande que todo el caché: no entra. Se sale sin
            // tocar nada, en vez de vaciar el caché entero por una página enorme.
            return
        }
        // Si la clave ya estaba, primero se descuenta: si no, se contaría dos veces.
        entradas.remove(clave)?.let { totalBytes -= it.bytes }

        while (totalBytes + bytes > topeBytes && entradas.isNotEmpty()) {
            val victima = entradas.entries.minByOrNull { it.value.ultimoUso } ?: break
            totalBytes -= victima.value.bytes
            entradas.remove(victima.key)
        }
        entradas[clave] = Entrada(bitmap, bytes, System.nanoTime())
        totalBytes += bytes
    }

    fun limpiar() = synchronized(candado) {
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
    fun recortarHasta(objetivo: Long) = synchronized(candado) {
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
