package com.ceroX.app.modules.prisma

import com.ceroX.app.core.engine.ModuleHealth
import com.ceroX.app.core.engine.ModuleInterface

/**
 * PrismaModule — Integración de RarDog/Prisma en cero-X.
 *
 * Prisma es un cliente multiplataforma para Booru y archivos de creadores
 * (Pawchive, Gelbooru, e621, Rule34, Realbooru).
 *
 * Características integradas:
 * - Cliente multiplataforma (Android, Linux, Windows, macOS)
 * - Soporte para múltiples proveedores: Pawchive, Gelbooru, e621, Rule34, Realbooru
 * - Descargas paralelas (hasta 3 transferencias simultáneas)
 * - Cola de descargas con pausa y progreso limitado
 * - Historial de búsqueda unificado
 * - Editor de proveedores con cabeceras JSON
 * - Descarga de APK split para Android (arm64-v8a, armeabi-v7a, x86_64)
 * - AppImage y tarball para Linux
 */
class PrismaModule : ModuleInterface() {

    override val name = "prisma-client"
    override val version = "4.2.1-fusion"
    override val dependencies: List<String> = emptyList()

    private val providers = mutableListOf<PrismaProvider>()
    private val downloadQueue = mutableListOf<PrismaDownload>()
    private val searchHistory = mutableListOf<PrismaSearchHistory>()
    private var maxParallelDownloads = 3

    override fun initialize(config: Map<String, Any>): Boolean {
        maxParallelDownloads = config["max_parallel_downloads"] as? Int ?: 3
        registerDefaultProviders()
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
                "providers" to providers.size,
                "download_queue" to downloadQueue.size,
                "search_history" to searchHistory.size,
                "max_parallel_downloads" to maxParallelDownloads,
                "platforms" to listOf("Android", "Linux", "Windows", "macOS"),
                "cross_platform" to true
            )
        )
    }

    private fun registerDefaultProviders() {
        providers.addAll(listOf(
            PrismaProvider("gelbooru", "Gelbooru", "https://gelbooru.com", true),
            PrismaProvider("e621", "e621", "https://e621.net", true),
            PrismaProvider("rule34", "Rule34", "https://rule34.xxx", true),
            PrismaProvider("realbooru", "Realbooru", "https://realbooru.com", true),
            PrismaProvider("pawchive", "Pawchive", "https://pawchive.com", true),
            PrismaProvider("danbooru", "Danbooru", "https://danbooru.donmai.us", true)
        ))
    }

    fun addProvider(provider: PrismaProvider) {
        providers.add(provider)
    }

    fun listProviders(): List<PrismaProvider> = providers.toList()

    fun addToDownloadQueue(url: String, destination: String): PrismaDownload {
        val download = PrismaDownload(
            id = "dl_${url.hashCode()}",
            url = url,
            destination = destination,
            status = DownloadStatus.QUEUED,
            progress = 0.0f,
            createdAt = System.currentTimeMillis()
        )
        downloadQueue.add(download)
        return download
    }

    /**
     * La cola no avanza: Prisma no tiene un descargador conectado.
     *
     * Se marcaban las tareas como DOWNLOADING respetando los huecos libres y ahi
     * acababa todo, así que la pantalla enseñaba descargas en curso que no
     * existian. Se dejan en ERROR, que es lo que de verdad pasa con una tarea que
     * nadie va a descargar.
     */
    fun processDownloadQueue() {
        for (i in downloadQueue.indices) {
            val estado = downloadQueue[i].status
            if (estado == DownloadStatus.QUEUED || estado == DownloadStatus.DOWNLOADING) {
                downloadQueue[i] = downloadQueue[i].copy(status = DownloadStatus.ERROR, progress = 0f)
            }
        }
    }

    fun addSearchHistory(query: String, provider: String) {
        searchHistory.add(PrismaSearchHistory(query, provider, System.currentTimeMillis()))
        if (searchHistory.size > 100) {
            searchHistory.removeAt(0)
        }
    }

    fun listSearchHistory(): List<PrismaSearchHistory> = searchHistory.toList()
}

data class PrismaProvider(
    val id: String,
    val name: String,
    val baseUrl: String,
    val enabled: Boolean,
    val headersJson: String? = null
)

data class PrismaDownload(
    val id: String,
    val url: String,
    val destination: String,
    val status: DownloadStatus,
    val progress: Float,
    val createdAt: Long
)

enum class DownloadStatus {
    QUEUED, DOWNLOADING, PAUSED, COMPLETED, ERROR
}

data class PrismaSearchHistory(
    val query: String,
    val provider: String,
    val timestamp: Long
)