package com.ceroX.app.modules.booru

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Las matemáticas del visor, comprobadas con números exactos en la JVM.
 *
 * Aquí está lo que de verdad se puede romper: que la imagen se salga de la
 * vista al arrastrar, que el doble toque pierda el punto que estaba bajo el
 * dedo, o que una miniatura se amplíe hasta no ser más que un bloque de color.
 * Nada de esto necesita un aparato.
 */
class VisorTransformacionTest {

    private val delta = 0.001f

    /** Más ancha que la vista: el ajuste la reduce. */
    private val grande = VisorTransformacion(
        anchoImagen = 1200, altoImagen = 800, anchoVista = 600, altoVista = 600,
    )

    /** Más pequeña que la vista: el ajuste la amplía, pero 1:1 no se prohíbe. */
    private val pequena = VisorTransformacion(
        anchoImagen = 100, altoImagen = 50, anchoVista = 800, altoVista = 600,
    )

    @Test fun el_ajuste_mete_la_imagen_entera_y_la_centra() {
        val a = grande.ajustar()
        assertEquals(0.5f, a.escala, delta)
        assertEquals(0f, a.dx, delta)
        assertEquals(100f, a.dy, delta)
        assertTrue(a.ajustada)
        assertFalse(a.ampliada)
        assertFalse("en el ajuste ya no se puede reducir", a.puedeReducir)
        assertTrue("y sí se puede ampliar", a.puedeAmpliar)
    }

    @Test fun sin_dimensiones_no_hay_ajuste_fingido() {
        val sin = VisorTransformacion(0, 0, 600, 600).ajustar()
        assertFalse(sin.util)
        assertFalse(sin.lista)
        assertEquals(VisorTransformacion.SIN_AJUSTAR, sin.escala, delta)
        assertTrue(sin.resumen(), sin.resumen().contains("Sin dimensiones"))
        assertNull(sin.puntoDeImagen(10f, 10f))
    }

    @Test fun el_doble_toque_amplia_sobre_el_punto_tocado_y_vuelve_al_ajuste() {
        val base = grande.ajustar()
        val antes = base.puntoDeImagen(150f, 200f)!!

        val ampliada = base.alternarDobleToque(150f, 200f)
        assertEquals(1.25f, ampliada.escala, delta)
        assertTrue(ampliada.ampliada)
        val despues = ampliada.puntoDeImagen(150f, 200f)!!
        assertEquals("el punto bajo el dedo se movió", antes.first, despues.first, 0.01f)
        assertEquals("el punto bajo el dedo se movió", antes.second, despues.second, 0.01f)

        val vuelta = ampliada.alternarDobleToque(150f, 200f)
        assertTrue(vuelta.ajustada)
        assertEquals(base.escala, vuelta.escala, delta)
        assertEquals(base.dx, vuelta.dx, delta)
        assertEquals(base.dy, vuelta.dy, delta)
    }

    @Test fun el_pellizco_respeta_el_tope_maximo_y_el_minimo() {
        val base = grande.ajustar()

        val maxima = base.pellizco(1000f, 300f, 300f)
        assertEquals(4f, maxima.escala, delta)
        assertEquals(base.escalaMaxima, maxima.escala, delta)
        assertFalse(maxima.puedeAmpliar)

        val minima = base.pellizco(0.0001f, 300f, 300f)
        assertEquals(0.5f, minima.escala, delta)
        assertEquals(base.escalaMinima, minima.escala, delta)
        assertFalse(minima.puedeReducir)
        assertTrue(minima.ajustada)
    }

    @Test fun un_pellizco_no_deja_la_imagen_a_mitad_de_calculo() {
        val base = grande.ajustar()
        assertEquals(base, base.pellizco(0f, 10f, 10f))
        assertEquals(base, base.pellizco(-2f, 10f, 10f))
        // Sin ajustar no hay nada que mover: se devuelve el mismo estado.
        assertEquals(grande, grande.desplazar(50f, 50f))
    }

    @Test fun no_se_puede_arrastrar_fuera_de_la_vista() {
        val amplia = grande.conEscala(1f, 300f, 300f)
        assertEquals(-300f, amplia.dx, delta)
        assertEquals(-100f, amplia.dy, delta)

        val muyFuera = amplia.desplazar(-5000f, -5000f)
        assertEquals(-600f, muyFuera.dx, delta)
        assertEquals(-200f, muyFuera.dy, delta)

        val vuelta = muyFuera.desplazar(5000f, 5000f)
        assertEquals(0f, vuelta.dx, delta)
        assertEquals(0f, vuelta.dy, delta)
    }

    @Test fun si_la_imagen_cabe_entera_va_centrada_por_mucho_que_arrastres() {
        val ajustada = grande.ajustar()
        val arrastrada = ajustada.desplazar(500f, 500f)
        assertEquals(ajustada.dx, arrastrada.dx, delta)
        assertEquals(ajustada.dy, arrastrada.dy, delta)
    }

    @Test fun una_imagen_pequena_no_pierde_el_uno_a_uno() {
        val ajustada = pequena.ajustar()
        assertEquals(8f, ajustada.escala, delta)
        assertEquals(1f, pequena.escalaMinima, delta)

        val real = ajustada.unoAUno()
        assertEquals(1f, real.escala, delta)
        assertEquals(350f, real.dx, delta)
        assertEquals(275f, real.dy, delta)
        assertTrue(real.puedeAmpliar)
        assertFalse("por debajo de píxeles reales no se reduce", real.puedeReducir)
    }

    @Test fun al_cambiar_la_vista_se_recoloca_sin_perder_la_escala() {
        val ampliada = grande.ajustar().conEscala(1f, 300f, 300f)
        val rotada = ampliada.conVista(1200, 400)
        assertEquals(1f, rotada.escala, delta)
        assertEquals(1200, rotada.anchoVista)
        assertEquals(400, rotada.altoVista)
        assertEquals("cabe justa: va centrada", 0f, rotada.dx, delta)
        assertEquals(-100f, rotada.dy, delta)
    }

    @Test fun una_vista_sin_tamano_no_inventa_un_ajuste() {
        val sinVista = grande.copy(anchoVista = 0, altoVista = 0)
        assertFalse(sinVista.util)
        assertEquals(sinVista, sinVista.ajustar())
        assertEquals(1f, sinVista.escalaAjuste, delta)
    }

    @Test fun el_texto_de_escala_no_depende_del_idioma_del_aparato() {
        assertEquals("0,00×", grande.textoEscala)
        assertEquals("0,50×", grande.ajustar().textoEscala)
        assertEquals("1,00×", grande.unoAUno().textoEscala)
        assertTrue(grande.ajustar().resumen().contains("ajustada a la pantalla"))
        assertTrue(grande.unoAUno().resumen().contains("ampliada"))
        assertTrue(grande.resumen().contains("esperando el primer ajuste"))
    }

    @Test fun las_dimensiones_se_leen_del_texto_que_trae_la_api() {
        assertEquals(1200 to 800, VisorTransformacion.dimensiones("1200×800"))
        assertEquals(1200 to 800, VisorTransformacion.dimensiones("1200x800"))
        assertEquals(1 to 1, VisorTransformacion.dimensiones("1×1"))
        assertNull(VisorTransformacion.dimensiones("0×0"))
        assertNull(VisorTransformacion.dimensiones("1200×"))
        assertNull(VisorTransformacion.dimensiones(""))
        assertNull(VisorTransformacion.dimensiones("ancho×alto"))
        assertNull(VisorTransformacion.dimensiones("-5×7"))
    }

    @Test fun la_extension_guardada_se_limita_a_imagenes() {
        assertEquals("png", VisorTransformacion.extensionDe("https://safebooru.org/images/42/a.png"))
        assertEquals("jpeg", VisorTransformacion.extensionDe("https://safebooru.org/images/42/a.JPEG"))
        assertEquals("webp", VisorTransformacion.extensionDe("https://safebooru.org/images/42/a.webp?v=2"))
        assertEquals("jpg", VisorTransformacion.extensionDe("https://safebooru.org/images/42/a.exe"))
        assertEquals("jpg", VisorTransformacion.extensionDe("https://safebooru.org/images/42/sinextension"))
    }
}
