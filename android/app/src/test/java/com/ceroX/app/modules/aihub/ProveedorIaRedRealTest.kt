package com.ceroX.app.modules.aihub

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in con credencial externa; nunca imprime clave, URL con credenciales ni respuesta. */
class ProveedorIaRedRealTest {
    @Test fun proveedor_autenticado_devuelve_http_modelo_y_contenido() {
        val base = System.getenv("CEROX_AI_BASE_URL").orEmpty()
        val modelo = System.getenv("CEROX_AI_MODEL").orEmpty()
        val clave = System.getenv("CEROX_AI_API_KEY").orEmpty()
        assumeTrue("Configurar CEROX_AI_BASE_URL, CEROX_AI_MODEL y CEROX_AI_API_KEY",
            base.startsWith("https://") && modelo.isNotBlank() && clave.isNotBlank())
        val modulo = AIHubModule()
        assertTrue(modulo.initialize(emptyMap()))
        assertTrue(modulo.start())
        val respuesta = modulo.conversar(ConfiguracionChat(base, modelo, clave),
            "Prueba técnica de conexión. Responde con una frase breve.")
        println("IA_REAL http=${respuesta.codigoHttp} modelo=${respuesta.modelo} ms=${respuesta.milisegundos} caracteres=${respuesta.texto.length}")
        assertTrue("HTTP no satisfactorio: ${respuesta.codigoHttp}", respuesta.codigoHttp in 200..299)
        assertTrue("El proveedor no identificó modelo", respuesta.modelo.isNotBlank())
        assertTrue("Respuesta vacía", respuesta.texto.isNotBlank())
        assertTrue(modulo.mensajes().size == 2)
    }
}
