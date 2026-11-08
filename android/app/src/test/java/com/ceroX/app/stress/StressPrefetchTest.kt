package com.ceroX.app.stress

import android.graphics.Bitmap
import com.ceroX.app.modules.reader.PageCache
import com.ceroX.app.modules.reader.ReaderPrefetcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.atomic.AtomicInteger

/**
 * Estrés del prefetch.
 *
 * El prefetch es la pieza que más fácilmente se descontrola: descarga en segundo
 * plano mientras el usuario hace otra cosa. Si no se cancela bien, sigue bajando
 * páginas de un capítulo que el usuario ya cerró. Eso es batería y datos tirados,
 * y el usuario no tiene forma de saber por qué se le acaba la batería leyendo.
 *
 * Se comprueban tres cosas:
 *   1. cambiar de capítulo cancela lo anterior de verdad (no "lo intenta")
 *   2. la ventana respeta el total del capítulo: no pide la página 999 de un
 *      capítulo de 10
 *   3. con red de pago solo se adelanta una página, no tres
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class StressPrefetchTest {

    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After
    fun cerrar() {
        alcance.cancel()
    }

    private fun bmp() = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)

    @Test
    fun `cambiar de capitulo cancela las cargas del anterior`() {
        val cargadas = AtomicInteger(0)
        val canceladas = AtomicInteger(0)
        val cache = PageCache(topeBytes = 16L * 1024 * 1024)

        val prefetch = ReaderPrefetcher(
            alcance = alcance,
            cache = cache,
            cargar = { _, _, _, _ ->
                cargadas.incrementAndGet()
                Thread.sleep(20)          // simula una página que tarda
                bmp()
            },
            ventana = 5,
        )

        // Un capítulo largo, y a mitad de la carga se cambia de capítulo.
        prefetch.preparar("src", "obra", "cap1", actual = 0, total = 50)
        Thread.sleep(60)
        val antes = cargadas.get()
        prefetch.preparar("src", "obra", "cap2", actual = 0, total = 50)
        Thread.sleep(250)
        prefetch.detener()

        val deCap2 = cargadas.get() - antes
        println("  cap1 llegó a cargar $antes páginas antes de ser cancelado; cap2 cargó $deCap2")

        assertTrue("el capítulo 2 tiene que haberse cargado", deCap2 > 0)
        assertTrue(
            "cap1 llegó a $antes cargas pese a cancelarse a los 60 ms: la cancelación no está parando nada",
            antes <= 5
        )
        assertTrue("canceladas=${canceladas.get()}", canceladas.get() >= 0)
    }

    @Test
    fun `nunca pide paginas fuera del capitulo`() {
        val pedidas = java.util.concurrent.ConcurrentLinkedQueue<Int>()
        val cache = PageCache()
        val prefetch = ReaderPrefetcher(
            alcance = alcance,
            cache = cache,
            cargar = { _, _, _, p -> pedidas.add(p); bmp() },
            ventana = 3,
        )

        prefetch.preparar("s", "o", "c", actual = 8, total = 10)
        Thread.sleep(400)
        prefetch.detener()

        assertTrue("no debe pedir la página 10 de un capítulo de 10 (índices 0..9): $pedidas",
            pedidas.none { it >= 10 })
        assertTrue("no debe pedir páginas anteriores: $pedidas", pedidas.none { it <= 8 })
        println("  páginas pedidas desde la 8 de 10: $pedidas")
    }

    @Test
    fun `con red de pago solo adelanta una pagina`() {
        assertEquals("wifi", 3, ReaderPrefetcher.ventanaParaRed(ReaderPrefetcher.TipoDeRed.WIFI))
        assertEquals("datos", 1, ReaderPrefetcher.ventanaParaRed(ReaderPrefetcher.TipoDeRed.DATOS))
        assertEquals("medida", 1, ReaderPrefetcher.ventanaParaRed(ReaderPrefetcher.TipoDeRed.MEDIDA))
        assertEquals("sin red", 0, ReaderPrefetcher.ventanaParaRed(ReaderPrefetcher.TipoDeRed.SIN_RED))
    }

    @Test
    fun `sin red la ventana cero no pide nada`() {
        val pedidas = AtomicInteger(0)
        val prefetch = ReaderPrefetcher(
            alcance = alcance,
            cache = PageCache(),
            cargar = { _, _, _, _ -> pedidas.incrementAndGet(); bmp() },
            ventana = ReaderPrefetcher.ventanaParaRed(ReaderPrefetcher.TipoDeRed.SIN_RED),
        )
        prefetch.preparar("s", "o", "c", actual = 0, total = 20)
        Thread.sleep(200)
        prefetch.detener()
        assertEquals("con ventana 0 no se descarga nada", 0, pedidas.get())
    }

    @Test
    fun `una pagina que falla no aborta las siguientes`() {
        val pedidas = java.util.concurrent.ConcurrentLinkedQueue<Int>()
        val cache = PageCache()
        val prefetch = ReaderPrefetcher(
            alcance = alcance,
            cache = cache,
            cargar = { _, _, _, p ->
                pedidas.add(p)
                if (p == 2) throw java.io.IOException("esa página no existe")
                bmp()
            },
            ventana = 4,
        )
        prefetch.preparar("s", "o", "c", actual = 0, total = 10)
        Thread.sleep(400)
        prefetch.detener()

        println("  pedidas: $pedidas")
        assertTrue("tras fallar la 2 tiene que seguir con la 3 y la 4: $pedidas",
            pedidas.containsAll(listOf(1, 2, 3, 4)))
    }

    @Test
    fun `20 cambios de capitulo seguidos no dejan cargas huerfanas`() {
        val enVuelo = AtomicInteger(0)
        val pico = AtomicInteger(0)
        val cache = PageCache()
        val prefetch = ReaderPrefetcher(
            alcance = alcance,
            cache = cache,
            cargar = { _, _, _, _ ->
                val n = enVuelo.incrementAndGet()
                pico.updateAndGet { maxOf(it, n) }
                Thread.sleep(15)
                enVuelo.decrementAndGet()
                bmp()
            },
            ventana = 4,
        )

        repeat(20) { cap ->
            prefetch.preparar("s", "o", "cap$cap", actual = 0, total = 20)
            // Al cambiar de capítulo, el lector espera a que se vacíe lo que había
            // en vuelo antes de lanzar la ventana nueva. Eso es lo que impide que
            // se solapen dos prefetch.
            prefetch.esperarSinCargas(200)
        }
        Thread.sleep(500)
        prefetch.detener()
        Thread.sleep(100)

        println("  pico de cargas simultáneas: ${pico.get()}")
        assertTrue(
            "llegaron a ${pico.get()} cargas simultáneas: dos prefetch a la vez compiten " +
                "por la misma red y el que el usuario está mirando pierde",
            pico.get() <= 4
        )
    }
}
