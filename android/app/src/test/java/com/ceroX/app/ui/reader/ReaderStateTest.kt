package com.ceroX.app.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas del estado del lector. Sin Android, sin emulador.
 *
 * Estas son las que impiden los bugs de navegación que hacen que un lector
 * parezca roto sin estar roto: pasar de página dos veces en la última, invertir
 * las flechas en manga, y abrir el capítulo por el final en modo webtoon.
 */
class ReaderStateTest {

    @Test
    fun `un capitulo vacio no lanza y se declara vacio`() {
        val e = ReaderState(0)
        assertTrue(e.vacio)
        assertTrue(e.esPrimera)
        assertTrue(e.esUltima)
        assertEquals("sin páginas", e.etiqueta())
        assertEquals(0f, e.progreso, 0f)
    }

    @Test
    fun `avanzar desde la ultima pagina devuelve false y no se pasa`() {
        val e = ReaderState(3)
        assertTrue(e.siguiente())
        assertTrue(e.siguiente())
        assertFalse(e.siguiente())  // ya en la 3 de 3
        assertEquals(2, e.indice)   // no se fue a la 3 (no existe)
        assertTrue(e.esUltima)
    }

    @Test
    fun `retroceder desde la primera devuelve false y no baja de cero`() {
        val e = ReaderState(5)
        assertFalse(e.anterior())
        assertEquals(0, e.indice)
    }

    @Test
    fun `el indice inicial se acota en vez de lanzar`() {
        assertEquals(4, ReaderState(5, inicial = 99).indice)
        assertEquals(0, ReaderState(5, inicial = -7).indice)
    }

    @Test
    fun `irA acota`() {
        val e = ReaderState(10)
        e.irA(4); assertEquals(4, e.indice)
        e.irA(100); assertEquals(9, e.indice)
        e.irA(-1); assertEquals(0, e.indice)
    }

    @Test
    fun `el progreso de una sola pagina es uno, no cero`() {
        // Con total=1, «0 %» sería falso: ya la estás viendo.
        assertEquals(1f, ReaderState(1).progreso, 0.001f)
    }

    @Test
    fun `la etiqueta cuenta desde uno`() {
        val e = ReaderState(32, inicial = 6)
        assertEquals("7 / 32", e.etiqueta())
    }

    // ── Sentido de lectura ──────────────────────────────────────────────────

    @Test
    fun `en LTR el dedo a la izquierda avanza`() {
        val e = ReaderState(5, 0, ReaderState.Modo.LTR)
        e.porGesto(dxPositivo = false)
        assertEquals(1, e.indice)
    }

    @Test
    fun `en RTL el dedo a la derecha avanza`() {
        // Manga japonés: se pasa la página hacia la derecha para ir adelante.
        // Confundirlo es el bug más común de los lectores occidentales.
        val e = ReaderState(5, 0, ReaderState.Modo.RTL)
        e.porGesto(dxPositivo = true)
        assertEquals(1, e.indice)
    }

    @Test
    fun `en RTL el dedo a la izquierda retrocede`() {
        val e = ReaderState(5, 2, ReaderState.Modo.RTL)
        e.porGesto(dxPositivo = false)
        assertEquals(1, e.indice)
    }

    @Test
    fun `en webtoon el gesto horizontal no hace nada`() {
        // En webtoon el desplazamiento vertical es el del scroll. Interpretar
        // un gesto horizontal aquí movería de página al hacer zoom.
        val e = ReaderState(20, 5, ReaderState.Modo.WEBTOON)
        e.porGesto(dxPositivo = false)
        assertEquals(5, e.indice)
    }

    // ── Páginas dobles ──────────────────────────────────────────────────────

    @Test
    fun `la primera pagina va sola y luego van en pareja`() {
        val e = ReaderState(10, 0, ReaderState.Modo.RTL)
        assertEquals(listOf(0), e.paginasVisibles(doble = true))
        e.irA(1)
        assertEquals(listOf(1, 2), e.paginasVisibles(doble = true))
        e.irA(2)
        assertEquals(listOf(1, 2), e.paginasVisibles(doble = true))  // sigue la misma pareja
        e.irA(3)
        assertEquals(listOf(3, 4), e.paginasVisibles(doble = true))
    }

    @Test
    fun `la ultima pagina impar no inventa una pareja`() {
        val e = ReaderState(5, 4, ReaderState.Modo.RTL)
        assertEquals(listOf(3, 4), e.paginasVisibles(doble = true))
        val sola = ReaderState(5, 4, ReaderState.Modo.LTR)
        // Con total 5, la pareja (3,4) existe; pedir la 5 daría índice fuera.
        assertTrue(sola.paginasVisibles(doble = true).all { it < 5 })
    }

    @Test
    fun `en webtoon no hay paginas dobles`() {
        val e = ReaderState(10, 4, ReaderState.Modo.WEBTOON)
        assertEquals(listOf(4), e.paginasVisibles(doble = true))
    }

    @Test
    fun `sin doble siempre es una sola pagina`() {
        assertEquals(listOf(3), ReaderState(10, 3).paginasVisibles(doble = false))
    }

    // ── Cambio de capítulo ──────────────────────────────────────────────────

    @Test
    fun `paraCapitulo conserva el modo y empieza donde se le diga`() {
        val e = ReaderState(10, 4, ReaderState.Modo.RTL)
        val n = e.paraCapitulo(25, 0)
        assertEquals(ReaderState.Modo.RTL, n.modo)
        assertEquals(25, n.total)
        assertEquals(0, n.indice)
        assertEquals(4, e.indice)  // el anterior no se toca
    }

    @Test
    fun `total negativo es un error de programacion y se dice`() {
        try {
            ReaderState(-1)
            throw AssertionError("debería haber lanzado")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("negativo"))
        }
    }
}
