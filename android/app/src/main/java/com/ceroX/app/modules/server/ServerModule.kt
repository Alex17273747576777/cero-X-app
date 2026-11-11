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
    override val dependencies: List<String> = emptyList()

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

    /**
     * Abre TCP contra el servidor y dice lo que paso.
     *
     * Devolvía `success = true` con "Conectado" sin tocar la red. Aqui se abre el
     * socket de verdad, con cinco segundos de espera: si el puerto responde, se
     * dice con la direccion que contesto; si no, se dice la excepcion.
     *
     * Ojo: esto bloquea y hace red, así que hay que llamarlo fuera del hilo
     * principal (en Android, red en el hilo principal lanza
     * NetworkOnMainThreadException).
     */
    fun connectToServer(host: String, port: Int, type: String): ConnectionResult {
        if (host.isBlank()) {
            return ConnectionResult(host, port, type, false, "No se dio ningun host al que conectarse")
        }
        if (port !in 1..65535) {
            return ConnectionResult(host, port, type, false, "El puerto $port no es valido")
        }
        return try {
            java.net.Socket().use { s ->
                s.connect(java.net.InetSocketAddress(host, port), 5000)
                val ip = s.inetAddress?.hostAddress ?: host
                ConnectionResult(host, port, type, true, "TCP abierto con $ip:$port (tipo $type sin autenticar)")
            }
        } catch (e: Exception) {
            ConnectionResult(host, port, type, false,
                "No se pudo abrir TCP con $host:$port — " + (e.message ?: e.javaClass.simpleName))
        }
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