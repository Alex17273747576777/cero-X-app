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

    /**
     * Sube el progreso a una plataforma.
     *
     * ANTES ESTO MENTÍA Y AHORA NO
     * ----------------------------
     * La versión anterior hacía `success = true` y devolvía «Sincronizado con
     * anilist» **sin hacer ninguna petición de red**. Era el mismo patrón que
     * las 14 afirmaciones que se corrigieron: un `true` que no significa nada.
     *
     * Ahora, sin un cliente HTTP y un token, devuelve `success = false` y dice
     * por qué. Los trackers que sí funcionan de verdad (AniList y MyAnimeList,
     * con token del usuario) están en [com.ceroX.app.modules.houri.tracker],
     * con lectura del progreso remoto y la regla de no retroceder.
     *
     * La firma se mantiene porque `TrackerSyncManager` la usa; cambiar la firma
     * aquí habría roto el módulo de sincronización entero para arreglar un
     * mensaje.
     */
    fun syncProgress(mangaId: String, chapter: Int, status: String, platform: String): TrackerSyncResult {
        if (!isEnabled(platform)) {
            return TrackerSyncResult(platform.toTrackerPlatform(), mangaId, false, "Plataforma no habilitada")
        }
        return TrackerSyncResult(
            platform.toTrackerPlatform(),
            mangaId,
            false,
            "sin cliente para $platform: usa el tracker de la pestaña Sincronización " +
                "(AniList o MyAnimeList con tu token); no se envió nada",
        )
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
