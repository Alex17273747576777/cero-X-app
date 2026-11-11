package com.ceroX.app.modules.audio

import android.media.MediaPlayer
import com.ceroX.app.core.engine.ModuleHealth
import com.ceroX.app.core.engine.ModuleInterface

/**
 * AudioModule — una lista en memoria y un `android.media.MediaPlayer`.
 *
 * Lo que hace de verdad: guarda las pistas que le pase quien lo llame y reproduce
 * una por su URL o ruta con el reproductor del framework. Nada más.
 *
 * Lo que NO hace, y antes fingía hacer:
 *
 *   • **No busca en ninguna parte.** Tenía un método de búsqueda que devolvía una
 *     pista fabricada a partir del hashCode de la consulta, con el nombre de un
 *     índice de la comunidad como fuente, sin hacer una sola petición de red. Ese
 *     índice no es una API de audio y no hay cliente suyo en este repositorio. El
 *     método está borrado: una búsqueda que inventa sus resultados es peor que no
 *     tener búsqueda, porque el usuario escribe y ve aparecer algo. Los detalles
 *     forenses, con los nombres exactos, están en `docs/LIMPIEZA.md`.
 *   • No escanea el almacenamiento ni lee etiquetas ID3.
 *   • No tiene ecualizador. El EQ viene en su propio módulo con
 *     `android.media.audiofx.Equalizer`; cuando exista se dirá aquí y no antes.
 *   • No sobrevive al proceso: la lista es un `mutableListOf`, no una base de datos.
 *
 * `sampleRate` y `bufferSize` se leen del config y se informan en el healthCheck,
 * pero `MediaPlayer` no acepta ninguno de los dos: no hay manera de pedírselos. Se
 * conservan como datos declarativos y se dice aquí para que nadie los lea como una
 * configuración que afecte a la reproducción. El EQ sí los usará, y ahí van con
 * `AudioTrack`, que es donde sí se pueden aplicar.
 */
class AudioModule : ModuleInterface() {

    override val name = "audio"
    override val version = "0.1.0"
    override val dependencies: List<String> = emptyList()

    private var mediaPlayer: MediaPlayer? = null
    private val playlist = mutableListOf<AudioTrack>()
    private var currentTrack: AudioTrack? = null
    private var sampleRate = 44100
    private var bufferSize = 4096

    override fun initialize(config: Map<String, Any>): Boolean {
        sampleRate = config["sample_rate"] as? Int ?: 44100
        bufferSize = config["buffer_size"] as? Int ?: 4096
        mediaPlayer = MediaPlayer()
        _initialized = true
        return true
    }

    override fun start(): Boolean {
        if (!_initialized) return false
        _started = true
        return true
    }

    /**
     * Libera el reproductor.
     *
     * Antes llamaba a `stop()` antes de `release()`, y `MediaPlayer.stop()` lanza
     * `IllegalStateException` si el reproductor está en el estado Idle —que es justo
     * donde está si se inicializó y nunca llegó a reproducir—. O sea: cerrar un
     * módulo de audio que no había sonado reventaba. `release()` es válido desde
     * cualquier estado y hace lo mismo.
     */
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
            "sample_rate_declarado" to sampleRate,
            "busqueda" to "no implementada",
        )
    )

    fun addToPlaylist(track: AudioTrack) {
        playlist.add(track)
    }

    fun listPlaylist(): List<AudioTrack> = playlist.toList()

    /**
     * Reproduce una pista de la lista y devuelve si sonó.
     *
     * Devolvía `true` en dos casos en los que no sonaba nada. Uno: si `mediaPlayer`
     * era null —después de un `stop()`— las llamadas seguras (`?.`) no hacían nada y
     * el método acababa en `return true`, así que la interfaz podía dar por
     * reproducida una pista que no existía. Dos: sólo se capturaba `IOException`, y
     * `setDataSource` con una URL vacía o un estado indebido lanza
     * `IllegalArgumentException` o `IllegalStateException`, que salían hacia arriba.
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
                "ceroX.audio",
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

    /**
     * Id de la sesión de audio del reproductor, o `null` si no existe.
     *
     * Es lo que necesita el módulo `audio-eq` para adjuntar los efectos a la
     * reproducción de verdad, en vez de a la mezcla de salida del dispositivo.
     * `MediaPlayer` crea la sesión al construirse, pero `getAudioSessionId()`
     * lanza `IllegalStateException` si el reproductor quedó en un estado inválido,
     * así que se captura y se devuelve `null`: quien llama decide si prefiere la
     * mezcla de salida.
     */
    fun audioSessionId(): Int? = try {
        mediaPlayer?.audioSessionId
    } catch (e: IllegalStateException) {
        null
    }
}

data class AudioTrack(
    val id: String,
    val title: String,
    val source: String,
    val type: String,
    val url: String = ""
)
