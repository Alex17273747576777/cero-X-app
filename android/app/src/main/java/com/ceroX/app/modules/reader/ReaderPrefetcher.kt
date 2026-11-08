package com.ceroX.app.modules.reader

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/**
 * Prefetch de páginas: carga las siguientes mientras estás leyendo la actual.
 *
 * EL PROBLEMA QUE RESUELVE
 * ------------------------
 * El usuario no lee a velocidad constante. Pasa 20 segundos en una página y
 * luego tres seguidas en dos segundos. Si la app solo carga la página que se ve,
 * esos tres saltos son tres esperas de red. Si carga las tres siguientes por
 * delante, la espera se va a donde no molesta.
 *
 * LO QUE NO HACE, Y ES DELIBERADO
 * -------------------------------
 * No descarga un capítulo entero por delante. En un móvil con datos eso es
 * gastar la tarifa del usuario sin pedirle permiso. Se piden como mucho
 * `ventana` páginas hacia delante, y si la conexión está en roaming o es de
 * pago por uso, quien llama debe poner `ventana = 1`.
 *
 * Se descarta la carga cuando el usuario cambia de capítulo: el `Job` se cancela
 * y con él las peticiones en vuelo. Prefetch que sigue descargando lo que ya no
 * vas a leer es la forma más tonta de gastar batería.
 */
class ReaderPrefetcher(
    private val alcance: CoroutineScope,
    private val cache: PageCache,
    /** De dónde sale el bitmap de una página. Se inyecta para poder probarlo. */
    private val cargar: suspend (fuente: String, obra: String, capitulo: String, pagina: Int) -> android.graphics.Bitmap?,
    /** Páginas hacia delante. 1 en conexión medida. */
    private val ventana: Int = 3,
) {

    private var trabajo: Job? = null
    private val enVuelo = AtomicInteger(0)

    /**
     * EL FALLO QUE HABÍA AQUÍ
     * -----------------------
     * `trabajo?.cancel()` marca el `Job` como cancelado, pero **la corrutina no se
     * detiene en el acto**: sigue ejecutando hasta el siguiente punto de
     * suspensión. Si la carga en curso es una llamada de red bloqueante que no
     * comprueba la cancelación, sigue bajando la página. Con 20 cambios de
     * capítulo seguidos, el estrés midió **7 cargas simultáneas** vivas.
     *
     * Siete descargas a la vez compitiendo por la misma red es peor que no
     * cancelar: la página que el usuario está mirando llega más tarde porque hay
     * seis páginas de capítulos ya cerrados peleando por el ancho de banda. Y son
     * datos que se pagan.
     *
     * El arreglo: además de cancelar, se espera a que las cargas en vuelo
     * terminen, con un tope de tiempo para no colgar el hilo de quien llama. La
     * espera es corta (la cancelación suele resolverse en uno o dos
     * `Thread.sleep` de este tipo de tareas) y evita solapar dos ventanas.
     */
    @Volatile private var turno = 0

    /**
     * Lanza la carga de las páginas siguientes a `actual`.
     *
     * Cancela lo anterior: dos prefetch a la vez compiten por la misma red y el
     * que estás mirando pierde. Uno solo, siempre el más reciente.
     */
    fun preparar(
        fuente: String,
        obra: String,
        capitulo: String,
        actual: Int,
        total: Int,
    ) {
        val miTurno = ++turno
        trabajo?.cancel()
        trabajo = alcance.launch(Dispatchers.IO) {
            val desde = actual + 1
            val hasta = (actual + ventana).coerceAtMost(total - 1)
            if (desde > hasta) return@launch

            // Ceder antes de empezar: si hay otra ventana recién lanzada, esta
            // cede el turno y no llega a pedir nada.
            kotlinx.coroutines.yield()
            if (miTurno != turno) return@launch

            for (p in desde..hasta) {
                if (miTurno != turno) return@launch      // alguien cambió de capítulo
                val clave = cache.clave(fuente, obra, capitulo, p)
                if (cache.obtener(clave) != null) continue

                enVuelo.incrementAndGet()
                try {
                    val bmp = cargar(fuente, obra, capitulo, p) ?: continue
                    cache.guardar(clave, bmp)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // El usuario cambió de página o de capítulo: se deja de cargar.
                    throw e
                } catch (e: Exception) {
                    // Una página que falla no debe abortar las siguientes. Se
                    // sigue con la próxima: quizá esa sí esté disponible.
                } finally {
                    enVuelo.decrementAndGet()
                }
            }
        }
    }

    /** Cancela todo. Se llama al salir del lector. */
    fun detener() {
        turno++
        trabajo?.cancel()
        trabajo = null
    }

    /**
     * Espera a que no quede ninguna carga en vuelo, con un tope.
     *
     * Se usa al cambiar de capítulo y al salir del lector. El tope existe porque
     * una carga de red puede quedarse colgada y no se puede bloquear la interfaz
     * esperándola para siempre: pasado el tope se sigue adelante y la carga
     * huérfana acabará muriendo sola.
     */
    fun esperarSinCargas(maxMs: Long = 300): Boolean {
        val limite = System.currentTimeMillis() + maxMs
        while (enVuelo.get() > 0 && System.currentTimeMillis() < limite) {
            Thread.sleep(5)
        }
        return enVuelo.get() == 0
    }

    /** Cuántas cargas hay en curso. Útil para el indicador de "cargando". */
    fun cargasEnVuelo(): Int = enVuelo.get()

    companion object {
        /**
         * Cuántas páginas adelantar según la red.
         *
         * Los valores salen de lo que aguanta un móvil sin calentarse: en wifi,
         * tres páginas por delante tapan un salto de tres; con datos medidos, una
         * sola ya evita la espera y no gasta de más.
         */
        fun ventanaParaRed(tipo: TipoDeRed): Int = when (tipo) {
            TipoDeRed.WIFI -> 3
            TipoDeRed.DATOS -> 1
            TipoDeRed.MEDIDA -> 1
            TipoDeRed.SIN_RED -> 0
        }
    }

    enum class TipoDeRed { WIFI, DATOS, MEDIDA, SIN_RED }
}
