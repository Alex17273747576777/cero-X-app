package com.ceroX.app.modules.aihub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ClienteChatTest {
    @Test
    fun `construye la ruta sin duplicar v1 y exige https`() {
        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            EndpointChat.normalizar("https://api.openai.com/"),
        )
        assertEquals(
            "https://openrouter.ai/api/v1/chat/completions",
            EndpointChat.normalizar("https://openrouter.ai/api"),
        )
        assertEquals(
            "http://127.0.0.1:11434/v1/chat/completions",
            EndpointChat.normalizar("http://127.0.0.1:11434"),
        )
        for (invalida in listOf("http://api.openai.com", "https://usuario:clave@example.com", "example.com")) {
            try {
                EndpointChat.normalizar(invalida)
                fail("aceptó $invalida")
            } catch (_: IllegalArgumentException) {
                // Esperado: no se envía nada a un destino inseguro o ambiguo.
            }
        }
    }

    @Test
    fun `envia el historial y devuelve el texto y el modelo del servidor`() {
        var urlRecibida = ""
        var claveRecibida = ""
        var cuerpoEnviado = ""
        val cliente = ClienteChat { url, clave, cuerpo ->
            urlRecibida = url
            claveRecibida = clave
            cuerpoEnviado = cuerpo
            RespuestaHttpChat(200, """{"model":"modelo-real","choices":[{"message":{"role":"assistant","content":"Respuesta medida"}}]}""")
        }
        val r = cliente.conversar(
            ConfiguracionChat("https://openrouter.ai/api", "openai/test", "secreto"),
            listOf(MensajeChat("user", "Hola")),
        )
        assertEquals("https://openrouter.ai/api/v1/chat/completions", urlRecibida)
        assertEquals("secreto", claveRecibida)
        assertTrue(cuerpoEnviado.contains("\"model\":\"openai/test\""))
        assertTrue(cuerpoEnviado.contains("\"content\":\"Hola\""))
        assertTrue(cuerpoEnviado.contains("\"stream\":false"))
        assertEquals("Respuesta medida", r.texto)
        assertEquals("modelo-real", r.modelo)
        assertEquals(200, r.codigoHttp)
    }

    @Test
    fun `un error sin api key no altera cada caracter`() {
        val cliente = ClienteChat { _, _, _ -> RespuestaHttpChat(404, "no existe") }
        val error = runCatching {
            cliente.conversar(
                ConfiguracionChat("http://localhost:11434", "local"),
                listOf(MensajeChat("user", "Hola")),
            )
        }.exceptionOrNull()
        assertTrue(error is java.io.IOException)
        assertTrue(error!!.message!!.contains("no existe"))
        assertFalse(error.message!!.contains("[clave oculta]"))
    }

    @Test
    fun `un http de error no se convierte en respuesta y oculta la clave`() {
        val cliente = ClienteChat { _, _, _ ->
            RespuestaHttpChat(401, """{"error":{"message":"bad key token-secreto"}}""")
        }
        val error = try {
            cliente.conversar(
                ConfiguracionChat("https://api.openai.com", "test", "token-secreto"),
                listOf(MensajeChat("user", "Hola")),
            )
            fail("HTTP 401 se aceptó como respuesta")
            return
        } catch (e: java.io.IOException) {
            e
        }
        assertTrue(error.message!!.contains("HTTP 401"))
        assertFalse(error.message!!.contains("token-secreto"))
        assertTrue(error.message!!.contains("[clave oculta]"))
    }
}

class AIHubModuleTest {
    @Test
    fun `el modulo guarda solo respuestas realmente recibidas`() {
        val modulo = AIHubModule(ClienteChat { _, _, _ ->
            RespuestaHttpChat(200, """{"model":"local","choices":[{"message":{"content":"Sí"}}]}""")
        })
        modulo.initialize(emptyMap())
        modulo.start()
        val r = modulo.conversar(ConfiguracionChat("http://localhost:11434", "local"), "¿Funcionas?")
        assertEquals("Sí", r.texto)
        assertEquals(listOf("user", "assistant"), modulo.mensajes().map { it.rol })
        modulo.borrarConversacion()
        assertTrue(modulo.mensajes().isEmpty())
        assertFalse(modulo.healthCheck().details["mcp"] as Boolean)
    }
}
