package com.ceroX.app.modules.video

import android.content.Context
import com.ceroX.app.core.engine.ModuleHealth
import com.ceroX.app.core.engine.ModuleInterface

class VideoModule : ModuleInterface() {

    override val name = "video"
    override val version = "0.1.0"
    override val dependencies: List<String> = emptyList()

    private val library = mutableListOf<VideoItem>()
    private var hwAccel = false

    override fun initialize(config: Map<String, Any>): Boolean {
        hwAccel = config["hw_accel"] as? Boolean ?: false
        _initialized = true
        return true
    }

    override fun start(): Boolean {
        if (!_initialized) return false
        _started = true
        return true
    }

    override fun stop(): Boolean {
        _started = false
        return true
    }

    override fun healthCheck(): ModuleHealth {
        return ModuleHealth(
            name, version,
            if (_started) "healthy" else "stopped",
            mapOf(
                "library_items" to library.size,
                "hw_accel" to hwAccel
            )
        )
    }

    fun addToLibrary(item: VideoItem) {
        library.add(item)
    }

    fun listLibrary(): List<VideoItem> = library.toList()

    fun play(videoId: String): Boolean {
        val item = library.firstOrNull { it.id == videoId } ?: return false
        // Placeholder: launch video player
        return true
    }
}

data class VideoItem(
    val id: String,
    val title: String,
    val path: String = "",
    val durationMs: Long = 0,
    val addedAt: Long = System.currentTimeMillis()
)