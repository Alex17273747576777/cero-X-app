package com.ceroX.app.modules.aihub

import com.ceroX.app.core.engine.ModuleHealth
import com.ceroX.app.core.engine.ModuleInterface

/**
 * Asistente conversacional real mediante endpoints OpenAI-compatibles.
 *
 * No incorpora las aplicaciones AI Hub o Kai ni afirma tener sus funciones. La
 * conversación vive en memoria hasta cerrar el proceso; endpoint y modelo los
 * configura el usuario y la clave se guarda cifrada desde la capa Android.
 */
class AIHubModule(
    private val cliente: ClienteChat = ClienteChat(),
) : ModuleInterface() {

    override val name = "ai-hub"
    override val version = "4.0.0-cliente-real"

    private val historial = mutableListOf<MensajeChat>()
    private var solicitudes = 0
    private var ultimoModelo = ""

    override fun initialize(config: Map<String, Any>): Boolean {
        _initialized = true
        return true
    }

    override fun start(): Boolean {
        if (!_initialized) return false
        _started = true
        return true
    }

    override fun stop(): Boolean {
        _started = false
        return true
    }

    /** Bloqueante por diseño; el llamador usa Dispatchers.IO. */
    fun conversar(config: ConfiguracionChat, texto: String): ResultadoChat {
        check(_started) { "El asistente no está iniciado" }
        val limpio = texto.trim()
        require(limpio.isNotEmpty()) { "Escribe un mensaje" }
        require(limpio.length <= 12_000) { "El mensaje supera 12 000 caracteres" }
        val nuevos = (historial + MensajeChat("user", limpio)).takeLast(20)
        val respuesta = cliente.conversar(config, nuevos)
        historial.clear()
        historial.addAll(nuevos)
        historial.add(MensajeChat("assistant", respuesta.texto))
        solicitudes++
        ultimoModelo = respuesta.modelo
        return respuesta
    }

    fun mensajes(): List<MensajeChat> = historial.toList()

    fun borrarConversacion() = historial.clear()

    override fun healthCheck() = ModuleHealth(
        name, version,
        if (_started) "cliente listo; proveedor no comprobado" else "stopped",
        mapOf(
            "transporte" to "POST /v1/chat/completions",
            "conversacion" to "sólo memoria",
            "mensajes_en_memoria" to historial.size,
            "respuestas_recibidas" to solicitudes,
            "ultimo_modelo" to ultimoModelo.ifBlank { "ninguno" },
            "api_key_en_este_modulo" to false,
            "mcp" to false,
            "ejecucion_de_herramientas" to false,
        ),
    )
}
