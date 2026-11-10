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

    private var appContext: android.content.Context? = null

    /**
     * Reproduce un vídeo.
     *
     * Antes: buscaba el vídeo, no lanzaba nada y devolvía `true`. El usuario
     * pulsaba play y la app decía que estaba reproduciendo.
     *
     * Ahora comprueba que el archivo exista y delega en el reproductor del sistema
     * con un Intent real. Si no hay contexto o el archivo no está, devuelve false.
     */
    fun play(videoId: String): Boolean {
        val item = library.firstOrNull { it.id == videoId } ?: return false
        if (item.path.isBlank()) return false
        val archivo = java.io.File(item.path)
        if (!archivo.exists() || !archivo.canRead()) return false
        val ctx = appContext ?: return false
        return try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                ctx, "${ctx.packageName}.fileprovider", archivo
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/*")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}

data class VideoItem(
    val id: String,
    val title: String,
    val path: String = "",
    val durationMs: Long = 0,
    val addedAt: Long = System.currentTimeMillis()
)