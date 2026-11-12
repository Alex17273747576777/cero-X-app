package com.ceroX.app.modules.web

import com.ceroX.app.core.engine.ModuleHealth
import com.ceroX.app.core.engine.ModuleInterface

/**
 * El módulo web privado, registrado para que el panel lo liste.
 *
 * No inicia nada pesado: no hay motor que cargar ni lista que bajar. Su
 * healthCheck dice lo que hay —el motor por defecto y las reglas incluidas— y
 * nada más.
 */
class WebModule : ModuleInterface() {
    override val name = "web"
    override val version = "0.1.0"
    override val dependencies: List<String> = emptyList()

    /** El motor con el que abre la pantalla. Brave por defecto; Google no está. */
    var motor: String = MotorBusqueda.POR_DEFECTO

    override fun initialize(config: Map<String, Any>): Boolean {
        _initialized = true
        return true
    }

    override fun start(): Boolean {
        _started = true
        return true
    }

    override fun stop(): Boolean {
        _started = false
        return true
    }

    override fun healthCheck(): ModuleHealth = ModuleHealth(
        name, version,
        if (_started) "healthy" else "stopped",
        mapOf<String, Any?>(
            "motor" to motor,
            "reglas_incluidas" to Bloqueador.INCLUIDAS.size,
            "politica_cookies" to Privacidad.POR_DEFECTO.name,
            "anti_muro" to "opcional, apagado por defecto",
        ),
    )
}
