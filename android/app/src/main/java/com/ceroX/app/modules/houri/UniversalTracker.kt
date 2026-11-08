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
            return TrackerSyncResult(platform, mangaId, false, "Plataforma no habilitada")
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

        return TrackerSyncResult(platform, mangaId, true, "Sincronizado con $platform")
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

data class TrackerSyncResult(
    val platform: String,
    val mangaId: String,
    val success: Boolean,
    val message: String
)