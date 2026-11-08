package com.ceroX.app.modules.sync

import com.ceroX.app.modules.houri.UniversalTracker

/**
 * TrackerSyncManager — Gestor de sincronización de trackers.
 * Fusiona Foxlations sync (AniList/MAL/Kitsu) con Houbi UniversalTracker.
 */
class TrackerSyncManager {

    private val universalTracker = UniversalTracker()

    fun syncToAniList(mangaId: String, chapter: Int, status: String): SyncResult {
        return syncProgress(mangaId, chapter, status, "anilist")
    }

    fun syncToMyAnimeList(mangaId: String, chapter: Int, status: String): SyncResult {
        return syncProgress(mangaId, chapter, status, "myanimelist")
    }

    fun syncToKitsu(mangaId: String, chapter: Int, status: String): SyncResult {
        return syncProgress(mangaId, chapter, status, "kitsu")
    }

    fun syncToUniversal(mangaId: String, chapter: Int, status: String, platform: String): SyncResult {
        val result = universalTracker.syncProgress(mangaId, chapter, status, platform)
        return SyncResult(result.platform, result.mangaId, result.success, result.message)
    }

    private fun syncProgress(mangaId: String, chapter: Int, status: String, platform: String): SyncResult {
        // Placeholder: llamada real a API de cada plataforma
        return SyncResult(platform, mangaId, true, "Sincronizado con $platform")
    }

    fun listSupportedPlatforms(): List<String> {
        return listOf("anilist", "myanimelist", "kitsu", "manga_baka", "manga_updates", "hikka")
    }
}

data class SyncResult(
    val platform: String,
    val mangaId: String,
    val success: Boolean,
    val message: String
)