package com.ceroX.app.stress

import com.ceroX.app.modules.feed.FeedEntry
import com.ceroX.app.modules.feed.FeedItem
import com.ceroX.app.modules.feed.FeedKind
import com.ceroX.app.modules.feed.FeedManager
import com.ceroX.app.modules.feed.FeedPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

/**
 * Estrés de concurrencia del feed.
 *
 * POR QUÉ ESTA PRUEBA EXISTE
 * --------------------------
 * `FeedManager.refrescar()` recorre `entradas.values.toList()` y, dentro del
 * bucle, hace `entradas[entrada.id] = entrada.copy(...)`. La lista de la
 * iteración es una copia, así que no revienta ahí — pero `entradas` es un
 * `LinkedHashMap` normal y **no es seguro entre hilos**. En la app real,
 * `refrescar()` corre en un hilo de fondo (va a red) mientras la pantalla llama a
 * `lista()` desde el hilo de interfaz para pintar. Dos hilos, un mapa sin
 * sincronizar, escrituras por medio: es cuestión de tiempo que salga un
 * `ConcurrentModificationException` o, peor, una entrada que desaparece sin
 * error.
 *
 * Los fallos de concurrencia no salen en una prueba normal porque hace falta que
 * dos hilos coincidan justo. Aquí se fuerza la coincidencia: 8 hilos, 400
 * iteraciones cada uno, todos a la vez.
 */
class StressFeedConcurrencyTest {

    private class ProveedorRapido(private val retardoMs: Long = 0) : FeedManager.ProveedorDeFeeds {
        override fun pagina(entrada: FeedEntry): FeedPage {
            if (retardoMs > 0) Thread.sleep(retardoMs)
            val items = (0 until 5).map { i ->
                FeedItem(
                    id = "obra-$i",
                    titulo = "Obra $i",
                    portadaUrl = null,
                    fuente = "prueba",
                    url = "https://ejemplo/$i",
                    capituloNuevo = null,
                    actualizadoEn = System.currentTimeMillis(),
                    motivo = entrada.titulo,
                )
            }
            return FeedPage(items = items, siguienteCursor = "c-" + System.nanoTime(), agotado = false)
        }
    }

    @Test
    fun `ocho hilos refrescando y leyendo a la vez no rompen el feed`() {
        val gestor = FeedManager(ProveedorRapido())
        repeat(6) { gestor.agregar(FeedKind.BIBLIOTECA, titulo = "entrada $it") }

        val errores = ConcurrentLinkedQueue<Throwable>()
        val operaciones = AtomicInteger(0)
        val HILOS = 8
        val ITERACIONES = 400
        val salida = CountDownLatch(HILOS)
        val empieza = CountDownLatch(1)

        repeat(HILOS) { h ->
            Thread {
                try {
                    empieza.await()
                    repeat(ITERACIONES) { i ->
                        try {
                            when ((h + i) % 4) {
                                0 -> gestor.refrescar()
                                1 -> gestor.lista().size          // lo que hace la interfaz
                                2 -> gestor.refrescar()
                                else -> {
                                    gestor.agregar(FeedKind.DESCUBRIR, titulo = "t$h-$i")
                                    gestor.lista().forEach { gestor.activar(it.id, true) }
                                }
                            }
                            operaciones.incrementAndGet()
                        } catch (e: Throwable) {
                            errores.add(e)
                        }
                    }
                } finally {
                    salida.countDown()
                }
            }.also { it.isDaemon = true }.start()
        }

        empieza.countDown()
        salida.await()

        assertTrue(
            "saltaron ${errores.size} errores de concurrencia. Primero: " +
                errores.firstOrNull()?.let { "${it::class.simpleName}: ${it.message}" },
            errores.isEmpty()
        )
        assertEquals("deben completarse las ${HILOS * ITERACIONES} operaciones",
            HILOS * ITERACIONES, operaciones.get())
    }

    @Test
    fun `los ids no se repiten aunque se creen en paralelo`() {
        val gestor = FeedManager(ProveedorRapido())
        val ids = ConcurrentLinkedQueue<String>()
        val salida = CountDownLatch(8)

        repeat(8) {
            Thread {
                try {
                    repeat(200) {
                        gestor.agregar(FeedKind.DESCUBRIR, titulo = "x")?.let { id -> ids.add(id) }
                    }
                } finally {
                    salida.countDown()
                }
            }.also { it.isDaemon = true }.start()
        }
        salida.await()

        val lista = ids.toList()
        assertEquals("no puede haber ids duplicados", lista.size, lista.toSet().size)
    }

    @Test
    fun `una entrada que siempre falla se aparca y deja de frenar al resto`() {
        // El proveedor falla para "mala" y responde para el resto.
        val proveedor = object : FeedManager.ProveedorDeFeeds {
            override fun pagina(entrada: FeedEntry): FeedPage {
                if (entrada.titulo == "mala") throw java.io.IOException("fuente caída")
                return FeedPage(
                    items = listOf(
                        FeedItem(
                            id = "ok-${entrada.id}", titulo = "Obra", portadaUrl = null,
                            fuente = "prueba", url = "https://ejemplo", capituloNuevo = null,
                            actualizadoEn = System.currentTimeMillis(), motivo = entrada.titulo
                        )
                    ),
                    siguienteCursor = "x",
                    agotado = false
                )
            }
        }
        val gestor = FeedManager(proveedor)
        val mala = gestor.agregar(FeedKind.BIBLIOTECA, titulo = "mala")!!
        gestor.agregar(FeedKind.DESCUBRIR, titulo = "buena")

        var conItems = 0
        repeat(10) {
            val r = gestor.refrescar()
            if (r.items.isNotEmpty()) conItems++
        }

        assertTrue("la buena debe seguir dando resultados pese a la mala", conItems == 10)
        val entradaMala = gestor.lista().first { it.id == mala }
        assertTrue(
            "tras 10 intentos la entrada mala debe estar aparcada, errores=${entradaMala.erroresSeguidos}",
            entradaMala.erroresSeguidos >= com.ceroX.app.modules.feed.FeedLimits.FALLOS_ANTES_DE_APARCAR
        )
    }

    @Test
    fun `borrar una entrada durante un refresco no la resucita`() {
        // Este era el fallo latente de verdad. `refrescar()` leía la lista, salía a
        // red y hacía `entradas[id] = ...`. Si el usuario borraba la entrada
        // mientras tanto, la escritura la volvía a crear: la entrada reaparecía
        // sola y el usuario no entendía nada.
        val retardo = CountDownLatch(1)
        val proveedor = object : FeedManager.ProveedorDeFeeds {
            override fun pagina(entrada: FeedEntry): FeedPage {
                retardo.await()   // se queda aquí hasta que la prueba lo suelte
                return FeedPage(
                    items = listOf(
                        FeedItem(
                            id = "x-${entrada.id}", titulo = "Obra", portadaUrl = null,
                            fuente = "p", url = "https://e", capituloNuevo = null,
                            actualizadoEn = 0L, motivo = entrada.titulo
                        )
                    ),
                    siguienteCursor = "cur", agotado = false
                )
            }
        }
        val gestor = FeedManager(proveedor)
        val id = gestor.agregar(FeedKind.BIBLIOTECA, titulo = "la que borro")!!

        val hilo = Thread { gestor.refrescar() }.also { it.isDaemon = true; it.start() }
        Thread.sleep(100)              // que el refresco esté dentro del proveedor
        assertTrue("borrada mientras se refresca", gestor.quitar(id))
        retardo.countDown()            // ahora el refresco aplica su resultado
        hilo.join(3000)

        assertTrue(
            "la entrada borrada NO debe volver al feed",
            gestor.lista().none { it.id == id }
        )
    }

    @Test
    fun `un proveedor lento no bloquea la lectura de la lista`() {
        // Si el candado rodeara la llamada de red, `lista()` esperaría segundos.
        // Se comprueba que la interfaz sigue pudiendo leer mientras se refresca.
        val lento = object : FeedManager.ProveedorDeFeeds {
            override fun pagina(entrada: FeedEntry): FeedPage {
                Thread.sleep(800)
                return FeedPage(emptyList(), null, false)
            }
        }
        val gestor = FeedManager(lento)
        gestor.agregar(FeedKind.BIBLIOTECA, titulo = "lenta")

        val hilo = Thread { gestor.refrescar() }.also { it.isDaemon = true; it.start() }
        Thread.sleep(150)              // el refresco ya está dentro del sleep de 800 ms

        val t0 = System.nanoTime()
        val n = gestor.lista().size
        val ms = (System.nanoTime() - t0) / 1_000_000

        assertTrue("debe haber 1 entrada", n == 1)
        assertTrue(
            "lista() tardó ${ms} ms con un refresco de 800 ms en curso: el candado " +
                "está rodeando la llamada de red y bloquea la interfaz",
            ms < 100
        )
        hilo.join(3000)
    }
}
