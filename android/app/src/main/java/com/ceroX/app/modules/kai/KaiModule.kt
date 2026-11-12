package com.ceroX.app.modules.kai

import com.ceroX.app.core.engine.ModuleHealth
import com.ceroX.app.core.engine.ModuleInterface

/**
 * Entrada histórica conservada para no romper inventarios antiguos.
 * El asistente utilizable vive en `AIHubModule`; aquí no hay inferencia, memoria,
 * cifrado, MCP ni proveedores que fingir. No es una integración del proyecto Kai.
 */
class KaiModule : ModuleInterface() {
    override val name = "kai-assistant"
    override val version = "2.0.0-inventario-honesto"

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

    override fun healthCheck() = ModuleHealth(
        name, version,
        if (_started) "entrada histórica; usa ai-hub para conversar" else "stopped",
        mapOf(
            "asistente_real" to "ai-hub",
            "proveedores_propios" to 0,
            "mcp" to false,
            "tool_execution" to false,
            "encrypted_storage" to false,
            "text_to_speech" to false,
            "linux_sandbox" to false,
        ),
    )

    /** Nunca afirma ejecutar un entorno que no está empaquetado. */
    fun runLinuxCommand(command: String) = LinuxCommandResult(
        command, false,
        "No hay entorno Linux en cero-X: no se lleva proot ni rootfs, así que no se ejecutó nada",
    )
}

data class LinuxCommandResult(val command: String, val success: Boolean, val output: String)
