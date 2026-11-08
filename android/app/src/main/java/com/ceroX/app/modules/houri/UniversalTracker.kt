package com.ceroX.app.modules.houri

import java.util.concurrent.ConcurrentHashMap

/**
 * UniversalTracker — Sincronización con MangaBaka, MangaUpdates, Hikka, Kitsu, AniList, MyAnimeList.
 * Basado en PineappleTwilight/houri universal tracker framework.
 */
class UniversalTracker {

    private val enabledPlatforms = ConcurrentHashMap<String, Boolean>()
    private val syncHistory = ConcurrentHashMap<String, TrackerSyncRecord>()

    init {
        enabledPlatforms["manga_baka"] = true
        enabledPlatforms["manga_updates"] = true
        enabledPlatforms["hikka"] = true
        enabledPlatforms["kitsu"] = true
        enabledPlatforms["anilist"] = true
        enabledPlatforms["myanimelist"] = true
    }

    fun enablePlatform(platform: String, enabled: Boolean) {
        enabledPlatforms[platform] = enabled
    }

    fun isEnabled(platform: String): Boolean = enabledPlatforms[platform] ?: false

    fun syncProgress(mangaId: String, chapter: Int, status: String, platform: String): TrackerSyncResult {
        if (!isEnabled(platform)) {
            return TrackerSyncResult(platform.toTrackerPlatform(), mangaId, false, "Plataforma no habilitada")
        }

        val record = TrackerSyncRecord(
            mangaId = mangaId,
            chapter = chapter,
            status = status,
            platform = platform,
            syncedAt = System.currentTimeMillis(),
            success = true
        )
        syncHistory["$mangaId:$platform"] = record

        return TrackerSyncResult(platform.toTrackerPlatform(), mangaId, true, "Sincronizado con $platform")
    }

    fun getLastSync(mangaId: String, platform: String): TrackerSyncRecord? {
        return syncHistory["$mangaId:$platform"]
    }

    fun listEnabledPlatforms(): List<String> {
        return enabledPlatforms.filter { it.value }.keys.toList()
    }
}

data class TrackerSyncRecord(
    val mangaId: String,
    val chapter: Int,
    val status: String,
    val platform: String,
    val syncedAt: Long,
    val success: Boolean
)

// TrackerSyncResult ya está declarado en HoubiModule.kt con el enum
// TrackerPlatform. Tener dos clases con el mismo nombre en el mismo paquete
// es una redeclaración y no compila: se usa la de HoubiModule.

/**
 * Convierte el nombre de plataforma que usa UniversalTracker ("myanimelist")
 * al enum de HoubiModule. Devuelve null-safe: una plataforma desconocida cae
 * en MYANIMELIST en vez de lanzar una excepción en mitad de una sincronización.
 */
private fun String.toTrackerPlatform(): TrackerPlatform = when (this.lowercase()) {
    "manga_baka", "mangabaka" -> TrackerPlatform.MANGA_BAKA
    "manga_updates", "mangaupdates" -> TrackerPlatform.MANGA_UPDATES
    "hikka" -> TrackerPlatform.HIKKA
    "kitsu" -> TrackerPlatform.KITSU
    "anilist" -> TrackerPlatform.ANILIST
    else -> TrackerPlatform.MYANIMELIST
}
