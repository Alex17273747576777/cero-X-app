package com.ceroX.app.modules.media

import android.media.MediaPlayer
import com.ceroX.app.core.engine.ModuleHealth
import com.ceroX.app.core.engine.ModuleInterface

/**
 * MediaModule — lista de pistas en memoria más un `android.media.MediaPlayer`.
 *
 * La cabecera se atribuía la fusión de dos proyectos ajenos y enumeraba cuatro
 * capacidades: reproductor de música, vídeo y YouTube; interfaz Compose
 * multiplataforma; reproductor multimedia integrado; y builds beta/nightly. De las
 * cuatro, aquí sólo estaba la tercera. No hay código de Namida —su licencia es una
 * EULA propietaria que prohíbe usarlo, ver `NOTICE`—, no hay YouTube, este módulo no
 * pinta ninguna interfaz y no gestiona canales nightly. Se quita la cabecera y se
 * escribe lo que hay.
 *
 * Lo que hace de verdad: guarda `MediaTrack` y reproduce una por URL con el
 * reproductor del framework, con pausa y reanudación.
 *
 * Lo que NO hace:
 *
 *   • **No busca en YouTube.** Tenía un método de búsqueda que devolvía una pista
 *     fabricada cuyo enlace no llevaba a ningún vídeo: un id derivado del hashCode de
 *     la consulta y una URL de relleno. Está borrado.
 *   • No tiene interfaz propia, y antes se leía del config una bandera que lo afirmaba
 *     y se repetía en el healthCheck como si este módulo pintara algo.
 *   • No hace streaming adaptativo. Para HLS/IPTV hace falta media3.
 *
 * Se solapa con `modules/audio/AudioModule` (los dos son lista + MediaPlayer). Se
 * mantienen los dos mientras se decide cuál sobrevive: el candidato natural es este,
 * porque el reproductor de IPTV que viene necesita pistas de vídeo y audio por igual.
 */
class MediaModule : ModuleInterface() {

    override val name = "media-player"
    override val version = "0.1.0-fusion"
    override val dependencies: List<String> = emptyList()

    private var mediaPlayer: MediaPlayer? = null
    private val playlist = mutableListOf<MediaTrack>()
    private var currentTrack: MediaTrack? = null

    override fun initialize(config: Map<String, Any>): Boolean {
        mediaPlayer = MediaPlayer()
        _initialized = true
        return true
    }

    override fun start(): Boolean {
        if (!_initialized) return false
        _started = true
        return true
    }

    /** `release()` sin `stop()` antes: ver la nota en AudioModule.stop(). */
    override fun stop(): Boolean {
        mediaPlayer?.release()
        mediaPlayer = null
        currentTrack = null
        _started = false
        return true
    }

    override fun healthCheck(): ModuleHealth = ModuleHealth(
        name, version,
        if (_started) "healthy" else "stopped",
        mapOf<String, Any?>(
            "playlist_size" to playlist.size,
            "current_track" to (currentTrack?.title ?: "ninguna"),
            "reproductor_creado" to (mediaPlayer != null),
            "youtube" to "no implementado",
            "interfaz_propia" to "ninguna",
        )
    )

    fun addToPlaylist(track: MediaTrack) {
        playlist.add(track)
    }

    fun listPlaylist(): List<MediaTrack> = playlist.toList()

    /**
     * Reproduce una pista. Devuelve `false` si no sonó.
     *
     * Tenía los mismos dos fallos que AudioModule: con `mediaPlayer` null las
     * llamadas seguras no hacían nada y acababa en `return true`, y capturaba
     * `Exception` imprimiendo la traza por `printStackTrace` sin dejar constancia de
     * qué pista falló.
     */
    fun play(trackId: String): Boolean {
        val track = playlist.firstOrNull { it.id == trackId } ?: return false
        if (track.url.isBlank()) return false
        val reproductor = mediaPlayer ?: return false
        return try {
            reproductor.reset()
            reproductor.setDataSource(track.url)
            reproductor.prepare()
            reproductor.start()
            currentTrack = track
            true
        } catch (e: Exception) {
            android.util.Log.w(
                "ceroX.media",
                "no se pudo reproducir ${track.id}: ${e.javaClass.simpleName}: ${e.message}",
            )
            false
        }
    }

    fun pause() {
        mediaPlayer?.pause()
    }

    fun resume() {
        mediaPlayer?.start()
    }
}

data class MediaTrack(
    val id: String,
    val title: String,
    val source: String,
    /** audio | video */
    val type: String,
    val url: String = "",
    val durationMs: Long = 0
)
