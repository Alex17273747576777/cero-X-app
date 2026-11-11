package com.ceroX.app.modules.sync

import com.ceroX.app.modules.houri.UniversalTracker

/**
 * TrackerSyncManager — Gestor de sincronización de trackers.
 * Fusiona Foxlations sync (AniList/MAL/Kitsu) con Houbi UniversalTracker.
 */
class TrackerSyncManager {

    private val universalTracker = UniversalTracker()

    fun syncToAniList(mangaId: String, chapter: Int, status: String): TrackerSyncOutcome {
        return syncProgress(mangaId, chapter, status, "anilist")
    }

    fun syncToMyAnimeList(mangaId: String, chapter: Int, status: String): TrackerSyncOutcome {
        return syncProgress(mangaId, chapter, status, "myanimelist")
    }

    fun syncToKitsu(mangaId: String, chapter: Int, status: String): TrackerSyncOutcome {
        return syncProgress(mangaId, chapter, status, "kitsu")
    }

    fun syncToUniversal(mangaId: String, chapter: Int, status: String, platform: String): TrackerSyncOutcome {
        val result = universalTracker.syncProgress(mangaId, chapter, status, platform)
        return TrackerSyncOutcome(result.platform.name.lowercase(), result.mangaId, result.success, result.message)
    }

    /**
     * Pasa por el tracker, que es el que sabe si puede enviar algo.
     *
     * Devolvía `success = true` con "Sincronizado con $platform" sin hacer una
     * sola peticion. `syncToUniversal()` ya iba por el camino bueno; las demas
     * plataformas, no. `UniversalTracker.syncProgress` dice la verdad: si no hay
     * cliente para la plataforma, devuelve fallo y explica donde se configura.
     */
    private fun syncProgress(mangaId: String, chapter: Int, status: String, platform: String): TrackerSyncOutcome {
        val r = universalTracker.syncProgress(mangaId, chapter, status, platform)
        return TrackerSyncOutcome(platform, mangaId, r.success, r.message)
    }

    fun listSupportedPlatforms(): List<String> {
        return listOf("anilist", "myanimelist", "kitsu", "manga_baka", "manga_updates", "hikka")
    }
}

/**
 * Resultado de una sincronización con un tracker.
 *
 * Se llamaba SyncResult, igual que la clase de SyncModule.kt del mismo paquete:
 * dos clases con el mismo nombre en el mismo paquete es una redeclaración y no
 * compila. Se renombra esta, que es la que lleva el mensaje legible para el
 * usuario y el id de la ficha.
 */
data class TrackerSyncOutcome(
    val platform: String,
    val mangaId: String,
    val success: Boolean,
    val message: String
)