package com.ceroX.app.modules.media

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri

/**
 * MediaPlayerManager — Reproductor multimedia integrado.
 * Basado en Namida reproductor música+video+YouTube.
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
        mediaPlayer?.stop()
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