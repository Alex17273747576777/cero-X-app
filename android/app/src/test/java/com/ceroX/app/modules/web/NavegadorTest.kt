package com.ceroX.app.modules.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavegadorTest {

    @Test
    fun `acepta https y dominios pero no http ni credenciales`() {
        assertEquals("https://example.com/x", Navegador.destino("https://example.com/x", "brave"))
        assertEquals("https://example.com/x", Navegador.destino("example.com/x", "brave"))
        assertNull(Navegador.destino("http://example.com", "brave"))
        assertNull(Navegador.destino("https://usuario:clave@example.com", "brave"))
    }

    @Test
    fun `lo que no es dominio se busca en brave`() {
        assertEquals(
            "https://search.brave.com/search?q=hola+mundo",
            Navegador.destino("hola mundo", "brave"),
        )
        assertNull(Navegador.destino("   ", "brave"))
    }

    @Test
    fun `searx exige una base https y la rotacion lo omite si falta`() {
        assertNull(MotorBusqueda.inicio("searx", "http://mi-searx.local"))
        assertEquals("brave", MotorBusqueda.siguiente("duck", ""))
        assertEquals("searx", MotorBusqueda.siguiente("duck", "https://mi-searx.example/"))
        assertEquals(
            "https://mi-searx.example/search?q=prueba",
            MotorBusqueda.url("searx", "prueba", "https://mi-searx.example/"),
        )
    }

    @Test
    fun `la portada no inventa una consulta`() {
        assertEquals("https://search.brave.com/", MotorBusqueda.inicio("brave"))
        assertEquals("https://duckduckgo.com/", MotorBusqueda.inicio("duck"))
    }
}
