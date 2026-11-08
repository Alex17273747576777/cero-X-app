package com.ceroX.app.modules.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas del feed multi-entrada.
 *
 * Los casos que se prueban son los que rompen un feed de verdad en producción:
 * una entrada que falla, el mismo elemento en dos entradas, la entrada que se
 * aparca sola tras fallar repetido, y que desactivar no borre.
 */
class FeedManagerTest {

    /** Proveedor de mentira: devuelve lo que se le diga y falla cuando se le diga. */
    private class ProveedorFalso(
        private val datos: MutableMap<FeedKind, MutableList<FeedItem>> = mutableMapOf(),
        private val falla: MutableSet<FeedKind> = mutableSetOf(),
    ) : FeedManager.ProveedorDeFeeds {
        var llamadas = 0

        fun poner(kind: FeedKind, vararg items: FeedItem) {
            datos[kind] = items.toMutableList()
        }

        fun romper(kind: FeedKind) {
            falla.add(kind)
        }

        fun arreglar(kind: FeedKind) {
            falla.remove(kind)
        }

        override fun pagina(entrada: FeedEntry): FeedPage {
            llamadas++
            if (entrada.kind in falla) throw IllegalStateException("fuente caída")
            val items = datos[entrada.kind] ?: mutableListOf()
            return FeedPage(items = items.toList(), siguienteCursor = null, agotado = true)
        }
    }

    private fun item(id: String, en: Long = 0L, motivo: String = "prueba") = FeedItem(
        id = id, titulo = "Título $id", portadaUrl = null,
        fuente = "fuente", url = "https://ejemplo.org/$id",
        capituloNuevo = null, actualizadoEn = en, motivo = motivo,
    )

    @Test
    fun `fusiona por id y no repite`() {
        val p = ProveedorFalso()
        p.poner(FeedKind.BIBLIOTECA, item("uno"), item("dos"))
        p.poner(FeedKind.NUEVOS, item("dos"), item("tres"))

        val m = FeedManager(p)
        m.agregar(FeedKind.BIBLIOTECA)
        m.agregar(FeedKind.NUEVOS)

        val r = m.refrescar()
        assertEquals("tres elementos únicos, no cuatro", 3, r.items.size)
        assertEquals(3, r.items.map { it.id }.toSet().size)
    }

    @Test
    fun `una entrada caida no tumba a las demas`() {
        val p = ProveedorFalso()
        p.poner(FeedKind.BIBLIOTECA, item("sobrevive"))
        p.poner(FeedKind.DESCUBRIR)
        p.romper(FeedKind.DESCUBRIR)

        val m = FeedManager(p)
        m.agregar(FeedKind.BIBLIOTECA)
        m.agregar(FeedKind.DESCUBRIR)

        val r = m.refrescar()
        assertEquals("la que funciona debe seguir dando datos", 1, r.items.size)
        assertTrue("debe haber un aviso del fallo", r.avisos.any { it.contains("fallo") })
    }

    @Test
    fun `tras tres fallos la entrada se aparca sola`() {
        val p = ProveedorFalso()
        p.poner(FeedKind.BIBLIOTECA, item("x"))
        p.romper(FeedKind.BIBLIOTECA)

        val m = FeedManager(p)
        m.agregar(FeedKind.BIBLIOTECA)

        repeat(3) { m.refrescar() }
        val llamadasAntes = p.llamadas

        val r = m.refrescar()

        assertEquals("aparcada: no debe volver a llamar al proveedor", llamadasAntes, p.llamadas)
        assertTrue("debe avisar de que está aparcada", r.avisos.any { it.contains("aparcada") })
    }

    @Test
    fun `desactivar no borra`() {
        val p = ProveedorFalso()
        p.poner(FeedKind.SUGERENCIAS, item("s"))
        val m = FeedManager(p)
        val id = m.agregar(FeedKind.SUGERENCIAS)!!

        assertTrue(m.activar(id, false))
        assertEquals("sigue en la lista", 1, m.lista().size)
        assertTrue("pero inactiva", !m.lista()[0].activa)

        val r = m.refrescar()
        assertTrue("una inactiva no se consulta", r.items.isEmpty())
        assertTrue("y no cuenta como activa", r.entradasActivas == 0)
    }

    @Test
    fun `respeta el maximo de entradas`() {
        val m = FeedManager(ProveedorFalso())
        repeat(FeedLimits.ENTRADAS_MAXIMAS) {
            assertNotNull(m.agregar(FeedKind.BIBLIOTECA))
        }
        assertNull("la siguiente debe rechazarse, no reventar",
            m.agregar(FeedKind.BIBLIOTECA))
        assertEquals(FeedLimits.ENTRADAS_MAXIMAS, m.lista().size)
    }

    @Test
    fun `ordena por fecha de actualizacion`() {
        val p = ProveedorFalso()
        p.poner(FeedKind.BIBLIOTECA, item("viejo", en = 100L), item("nuevo", en = 200L))
        val m = FeedManager(p)
        m.agregar(FeedKind.BIBLIOTECA)

        val r = m.refrescar()
        assertEquals("el más reciente va primero", "nuevo", r.items[0].id)
    }
}
