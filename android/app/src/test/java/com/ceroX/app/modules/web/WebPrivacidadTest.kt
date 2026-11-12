package com.ceroX.app.modules.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El módulo web, probado en JVM: lo que decide (motor, bloqueo, política,
 * selectores) no toca Android y por eso se puede probar aquí. Lo que toca el
 * sistema (WebView, CookieManager) vive en el fragmento y se verifica en el
 * teléfono, no en esta clase.
 */
class WebPrivacidadTest {

    @Test
    fun braveEsElMotorPorDefectoYCodificaLaConsulta() {
        assertEquals("brave", MotorBusqueda.POR_DEFECTO)
        val u = MotorBusqueda.url("brave", "hola mundo")
        assertEquals("https://search.brave.com/search?q=hola+mundo", u)
    }

    @Test
    fun googleNoEstaEnLaListaDeMotores() {
        assertFalse("ningún motor se apellida google",
            MotorBusqueda.MOTORES.any { it.plantilla.contains("google.com") })
    }

    @Test
    fun searxSinBaseNoInventaUnaUrl() {
        assertNull(MotorBusqueda.url("searx", "algo"))
        assertEquals("https://mi.searx.example/search?q=algo",
            MotorBusqueda.url("searx", "algo", "https://mi.searx.example/"))
    }

    @Test
    fun elBloqueadorCazaPorSufijoYDejaPasarLoDemas() {
        assertTrue(Bloqueador.debeBloquear("https://ads.doubleclick.net/x.js"))
        assertTrue(Bloqueador.debeBloquear("https://sub.ads.doubleclick.net/x"))
        assertFalse(Bloqueador.debeBloquear("https://search.brave.com/search?q=x"))
        assertFalse(Bloqueador.debeBloquear("nota-url"))
    }

    @Test
    fun laListaPropiaComentaYFiltra() {
        val lista = Bloqueador.analizarLista("# mío\nmalo.example\n\nno es dominio\notro.example # nota")
        assertEquals(listOf("malo.example", "otro.example"), lista)
        assertTrue(Bloqueador.debeBloquear("https://x.malo.example/a", lista))
    }

    @Test
    fun laPoliticaRotaYElOcultadorLlevaSusSelectores() {
        assertEquals(Privacidad.PoliticaCookies.BLOQUEAR_TERCEROS, Privacidad.POR_DEFECTO)
        assertEquals(Privacidad.PoliticaCookies.BLOQUEAR_TODO,
            Privacidad.siguiente(Privacidad.PoliticaCookies.PERMITIR))
        assertEquals("1", Privacidad.cabeceras()["DNT"])
        for (s in AntiMuro.SELECTORES) {
            assertTrue("el JS oculta %s".format(s), AntiMuro.JS_OCULTAR.contains(s))
        }
    }
}
