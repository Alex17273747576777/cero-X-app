package com.ceroX.app.modules.youtube_support

/**
 * YouTubeSupport — Soporte para YouTube.
 * Basado en Namida YouTube support.
 */
class YouTubeSupport {

    private var isEnabled = false
    private var apiKey: String? = null

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
    }

    fun setApiKey(apiKey: String) {
        this.apiKey = apiKey
    }

    fun searchVideos(query: String): List<YouTubeVideo> {
        if (!isEnabled) return emptyList()
        // Placeholder: llamada real a YouTube Data API
        return emptyList()
    }

    fun getVideoInfo(videoId: String): YouTubeVideo? {
        if (!isEnabled) return null
        return YouTubeVideo(videoId, "Título del video", "Canal", 0, "https://youtube.com/watch?v=$videoId")
    }

    fun isAvailable(): Boolean = isEnabled
}

data class YouTubeVideo(
    val id: String,
    val title: String,
    val channel: String,
    val durationMs: Long,
    val url: String
)