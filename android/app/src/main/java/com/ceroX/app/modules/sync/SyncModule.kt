package com.ceroX.app.modules.sync

import com.ceroX.app.core.engine.ModuleHealth
import com.ceroX.app.core.engine.ModuleInterface

/**
 * SyncModule — Fusión de Foxlations.
 *
 * Características fusionadas:
 * - Sincronización de progreso con AniList (Foxlations)
 * - Sincronización con MyAnimeList (Foxlations)
 * - Sincronización con Kitsu (Foxlations)
 * - Todo calculado en dispositivo, sin cuentas obligatorias
 */
class SyncModule : ModuleInterface() {

    override val name = "sync-trackers"
    override val version = "0.1.0-fusion"
    override val dependencies: List<String> = emptyList()

    private val anilistEnabled = true
    private val malEnabled = true
    private val kitsuEnabled = true
    private val syncHistory = mutableListOf<SyncRecord>()

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
                "anilist" to anilistEnabled,
                "myanimelist" to malEnabled,
                "kitsu" to kitsuEnabled,
                "sync_records" to syncHistory.size,
                "local_only" to true
            )
        )
    }

    /**
     * Sincroniza el progreso con una plataforma externa.
     *
     * Antes marcaba `success = true` con el comentario "Placeholder" al lado, sin
     * hacer ninguna petición. El historial de sincronización se llenaba de éxitos
     * que nunca ocurrieron, y el usuario creía tener su progreso a salvo en
     * AniList o MAL.
     *
     * Ahora, sin credenciales y sin cliente HTTP, devuelve `false`. El registro se
     * guarda igual con `success = false` y el motivo, porque saber qué NO se
     * sincronizó es información útil.
     */
    fun syncProgress(seriesId: String, chapter: Int, status: String, platform: SyncPlatform): SyncResult {
        val record = SyncRecord(
            seriesId = seriesId,
            chapter = chapter,
            status = status,
            platform = platform,
            syncedAt = System.currentTimeMillis(),
            success = false,
            errorMessage = "Sin credenciales de $platform y sin cliente HTTP: el progreso " +
                "NO se ha enviado. Se guarda en local."
        )
        syncHistory.add(record)
        return SyncResult(platform, record.syncedAt, false)
    }

    fun listSyncHistory(): List<SyncRecord> = syncHistory.toList()

    fun getLastSync(platform: SyncPlatform): SyncRecord? {
        return syncHistory.filter { it.platform == platform }.lastOrNull()
    }
}

enum class SyncPlatform {
    ANILIST, MYANIMELIST, KITSU, NONE
}

data class SyncRecord(
    val seriesId: String,
    val chapter: Int,
    val status: String,
    val platform: SyncPlatform,
    val syncedAt: Long,
    val success: Boolean,
    val errorMessage: String? = null
)

data class SyncResult(
    val platform: SyncPlatform,
    val syncedAt: Long,
    val success: Boolean
)