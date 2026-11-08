package com.ceroX.app.modules.server

import com.ceroX.app.core.engine.ModuleHealth
import com.ceroX.app.core.engine.ModuleInterface

/**
 * ServerModule — Gestor de servidores propios.
 * Integra: Jellyfin, Plex, Seanime, Komga, Kavita, LANraragi, Suwayomi.
 */
class ServerModule : ModuleInterface() {

    override val name = "media-server"
    override val version = "1.0.0-beta"
    override val dependencies = emptyList()

    private val servers = mutableListOf<ServerInfo>()

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

    override fun healthCheck(): ModuleHealth {
        return ModuleHealth(
            name, version,
            if (_started) "healthy" else "stopped",
            mapOf(
                "servers_configured" to servers.size,
                "supported_types" to listOf("anime", "manga", "audiobook", "others"),
                "jellyfin" to true,
                "plex" to true,
                "seanime" to true,
                "komga" to true,
                "kavita" to true,
                "lanraragi" to true,
                "suwayomi" to true
            )
        )
    }

    fun addServer(server: ServerInfo) {
        servers.add(server)
    }

    fun listServers(): List<ServerInfo> = servers.toList()

    fun getSupportedServerTypes(): List<String> {
        return listOf("jellyfin", "plex", "seanime", "komga", "kavita", "lanraragi", "suwayomi")
    }

    fun connectToServer(host: String, port: Int, type: String): ConnectionResult {
        // Placeholder: conexión real al servidor
        return ConnectionResult(host, port, type, true, "Conectado")
    }
}

data class ServerInfo(
    val id: String,
    val name: String,
    val type: String, // jellyfin | plex | seinime | komga | kavita | lanraragi | suwayomi
    val host: String,
    val port: Int,
    val apiKey: String? = null,
    val connected: Boolean = false
)

data class ConnectionResult(
    val host: String,
    val port: Int,
    val type: String,
    val success: Boolean,
    val message: String
)