package com.ceroX.app.modules.media

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri

/**
 * MediaPlayerManager — envoltorio fino sobre `android.media.MediaPlayer`.
 *
 * La cabecera decía «Basado en Namida reproductor música+video+YouTube». No hay una
 * línea de Namida aquí (su licencia es una EULA propietaria que prohíbe usar su
 * código, ver `NOTICE`) y este gestor no toca YouTube: reproduce un `Uri` local o una
 * URL que le den. Es código propio y hace lo que se lee abajo.
 *
 * Límites reales del `MediaPlayer` del framework, para que nadie los descubra en
 * producción: no reproduce HLS (.m3u8) con fiabilidad, no informa de bitrate ni de
 * pistas de subtítulos, y `prepare()` bloquea el hilo que lo llame —si es el
 * principal, eso es un ANR—. El reproductor de IPTV que viene usa media3/ExoPlayer
 * por esas tres razones; este envoltorio se queda para archivos locales.
 */
class MediaPlayerManager(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false
    private var currentUri: Uri? = null

    fun play(uri: Uri): Boolean {
        stop()
        try {
            mediaPlayer = MediaPlayer()
            mediaPlayer?.setDataSource(context, uri)
            mediaPlayer?.prepare()
            mediaPlayer?.start()
            currentUri = uri
            isPlaying = true
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun playUrl(url: String): Boolean {
        return play(Uri.parse(url))
    }

    fun pause() {
        mediaPlayer?.pause()
        isPlaying = false
    }

    fun resume() {
        mediaPlayer?.start()
        isPlaying = true
    }

    fun stop() {
        // stop() lanza IllegalStateException si el reproductor quedó en Idle o en
        // Initialized (un play() que falló a medias lo deja ahí). release() vale
        // desde cualquier estado, y es lo que de verdad libera el recurso.
        mediaPlayer?.release()
        mediaPlayer = null
        isPlaying = false
        currentUri = null
    }

    fun seekTo(milliseconds: Int) {
        mediaPlayer?.seekTo(milliseconds)
    }

    fun getCurrentPosition(): Int {
        return mediaPlayer?.currentPosition ?: 0
    }

    fun getDuration(): Int {
        return mediaPlayer?.duration ?: 0
    }

    fun isPlaying(): Boolean = isPlaying

    fun setVolume(left: Float, right: Float) {
        mediaPlayer?.setVolume(left, right)
    }

    fun release() {
        stop()
    }
}

data class MediaTrackInfo(
    val id: String,
    val title: String,
    val artist: String? = null,
    val durationMs: Long = 0,
    val uri: String? = null,
    val source: String = "local" // local | youtube | streaming
)