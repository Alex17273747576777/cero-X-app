package com.ceroX.app.stress

import android.graphics.Bitmap
import com.ceroX.app.modules.reader.PageCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * Estrés de la caché de páginas.
 *
 * Una caché de lector tiene un trabajo y solo uno: **no pasarse nunca del
 * presupuesto de memoria**. Si se pasa, en un móvil de gama baja el sistema mata
 * la app en mitad del capítulo 40 y el usuario pierde el sitio donde iba.
 *
 * Los tres escenarios que rompen una caché mal hecha:
 *   1. Muchas páginas pequeñas seguidas: ¿respeta el tope en bytes?
 *   2. Una página enorme: ¿se lleva por delante las pequeñas?
 *   3. Varios hilos guardando y leyendo a la vez: ¿corrompe el contador?
 *
 * El punto 3 es el que más se escapa. `PageCache` usa un `ConcurrentHashMap` y
 * un contador `totalBytes` que es un `Long` normal: si dos hilos suman a la vez,
 * el contador se desincroniza del contenido real y la caché acaba creyendo que
 * ocupa menos de lo que ocupa — o sea, comiéndose la memoria sin saberlo.
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class StressPageCacheTest {

    private fun pagina(lado: Int): Bitmap =
        Bitmap.createBitmap(lado, lado, Bitmap.Config.ARGB_8888)

    @Test
    fun `5000 claves distintas llenan la cache pero nunca la desbordan`() {
        val tope = 8L * 1024 * 1024
        val cache = PageCache(topeBytes = tope)
        val bmp = pagina(32)                        // 32*32*4 = 4096 B
        var peor = 0L

        repeat(5000) { i ->
            cache.guardar("p$i", bmp)
            val usado = cache.bytesUsados()
            if (usado > peor) peor = usado
            if (usado > tope) {
                throw AssertionError("en la página $i la caché ocupa $usado B, tope $tope B")
            }
        }
        // 5000 claves distintas de 4 KB son 20 MB: no caben en 8 MB y la caché
        // tiene que haber desalojado. Lo correcto es estar lleno hasta el tope,
        // no quedarse en una sola página (eso fue un fallo real: decía estar
        // llena cuando tenía una entrada).
        assertEquals("debe estar llena hasta el tope", tope, cache.bytesUsados())
        assertTrue("la última insertada tiene que seguir dentro", cache.obtener("p4999") != null)
        assertTrue("y la primera tiene que haber salido", cache.obtener("p0") == null)
        assertTrue("el peor momento fue $peor B de $tope", peor <= tope)
    }

    @Test
    fun `5000 inserciones de la misma clave no acumulan`() {
        // Caso que sí podía romper el contador: reguardar la misma clave. Si no se
        // descuenta la entrada anterior antes de sustituirla, `totalBytes` sube
        // 4 KB por inserción y la caché acaba creyendo que está llena con una
        // sola página dentro.
        val cache = PageCache(topeBytes = 8L * 1024 * 1024)
        val bmp = pagina(32)                        // 4096 B
        repeat(5000) { cache.guardar("misma", bmp) }

        assertEquals("una sola página de 4 KB, no 5000", 4096L, cache.bytesUsados())
        assertTrue("y tiene que estar accesible", cache.obtener("misma") != null)
    }

    @Test
    fun `una pagina enorme no vacia la cache`() {
        val tope = 1024L * 1024                       // 1 MiB
        val cache = PageCache(topeBytes = tope)
        val pequena = pagina(32)                      // 4 KB
        val enorme = pagina(2048)                     // 2048*2048*4 = 16 MiB

        repeat(10) { k -> cache.guardar("p$k", pequena) }
        cache.guardar("enorme", enorme)

        assertTrue("la caché debe seguir por debajo del tope", cache.bytesUsados() <= tope)
        assertTrue(
            "una página que no cabe no puede haber vaciado las que sí caben",
            (0 until 10).any { cache.obtener("p$it") != null }
        )
    }

    @Test
    fun `8 hilos guardando y leyendo no desincronizan el contador`() {
        val tope = 2L * 1024 * 1024
        val cache = PageCache(topeBytes = tope)
        val tamanos = listOf(16, 32, 64, 128)
        val errores = ConcurrentLinkedQueue<Throwable>()
        val operaciones = AtomicInteger(0)
        val salida = CountDownLatch(8)
        val empieza = CountDownLatch(1)

        repeat(8) { h ->
            Thread {
                try {
                    empieza.await()
                    repeat(500) { i ->
                        val t = tamanos[(h + i) % tamanos.size]
                        val clave = "p-${i % 200}"
                        if (i % 2 == 0) {
                            cache.guardar(clave, pagina(t))
                        } else {
                            cache.obtener(clave)
                        }
                        // Invariante que debe cumplirse en TODO momento, incluso
                        // a mitad de otra operación.
                        if (cache.bytesUsados() > tope) {
                            throw AssertionError(
                                "contador desincronizado: ${cache.bytesUsados()} B > tope $tope B"
                            )
                        }
                        operaciones.incrementAndGet()
                    }
                } catch (e: Throwable) {
                    errores.add(e)
                } finally {
                    salida.countDown()
                }
            }.also { it.isDaemon = true }.start()
        }

        empieza.countDown()
        salida.await()

        assertTrue(
            "errores de concurrencia: ${errores.joinToString { "${it::class.simpleName}: ${it.message}" }}",
            errores.isEmpty()
        )
        assertEquals(8 * 500, operaciones.get())
        println("  contador final: ${cache.bytesUsados()} B de $tope · hit rate ${cache.hitRate()}")
    }

    @Test
    fun `el contador coincide con lo que hay de verdad tras mucho trafico`() {
        // Se hace tráfico y después se comprueba que el contador no se ha
        // desviado: se recorta a 0 y debe quedar exactamente 0.
        val cache = PageCache(topeBytes = 32L * 1024 * 1024)
        repeat(3000) { i ->
            cache.guardar("k${i % 400}", pagina(if (i % 3 == 0) 64 else 16))
            if (i % 7 == 0) cache.obtener("k${i % 400}")
        }
        val antes = cache.bytesUsados()
        cache.recortarHasta(0)
        assertEquals("tras recortar a 0 no puede quedar nada contado", 0L, cache.bytesUsados())
        assertTrue("antes del recorte había algo", antes > 0)
        println("  tras 3000 operaciones: $antes B en caché")
    }

    @Test
    fun `hit rate con un patron realista de lectura`() {
        // Un lector real relee: va hacia atrás y hacia delante. Un patrón así debe
        // dar un hit rate alto; si sale cerca de 0 es que la política de descarte
        // está tirando justo lo que se va a volver a mirar.
        val cache = PageCache(topeBytes = 12L * 1024 * 1024)
        val bmp = pagina(48)
        val ventana = 12
        var paginaActual = 0
        repeat(600) { paso ->
            // Lectura con rebobinado: avanza 1, y cada 20 pasos vuelve 5.
            paginaActual = if (paso % 20 == 19) (paginaActual - 5).coerceAtLeast(0)
                           else paginaActual + 1
            val clave = "cap1-$paginaActual"
            if (cache.obtener(clave) == null) {
                cache.guardar(clave, bmp)
            }
            // prefetch de la ventana siguiente
            for (p in paginaActual + 1..paginaActual + ventana) {
                val k = "cap1-$p"
                if (cache.obtener(k) == null) cache.guardar(k, bmp)
            }
        }
        println("  hit rate con rebobinado: ${"%.1f".format(cache.hitRate() * 100)} % · " +
            "${cache.aciertos()} aciertos / ${cache.fallos()} fallos")
        assertTrue(
            "el hit rate fue ${cache.hitRate()}: con rebobinado debe haber bastantes aciertos",
            cache.hitRate() > 0.2f
        )
    }
}
