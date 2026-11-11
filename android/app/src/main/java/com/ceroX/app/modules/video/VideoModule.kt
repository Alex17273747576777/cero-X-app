package com.ceroX.app.modules.video

import com.ceroX.app.core.engine.ModuleHealth
import com.ceroX.app.core.engine.ModuleInterface

/**
 * VideoModule — una biblioteca en memoria y un puente al reproductor del sistema.
 *
 * Lo que hace de verdad: guarda los `VideoItem` que le pase quien lo llame y, en
 * `play()`, comprueba que el archivo exista y sea legible y lanza un `ACTION_VIEW`
 * con un `FileProvider`. O sea: **no decodifica vídeo**, lo delega en otra app.
 *
 * Lo que NO hace, y antes decía hacer:
 *
 *   • **No tiene aceleración por hardware.** Había una bandera leída del config y
 *     repetida en el healthCheck que no tocaba la reproducción en ningún sitio, y no
 *     podía tocarla: si el vídeo lo abre otra app, quién acelera y cómo no es
 *     decisión de cero-X. Borrada aquí y en `ConfigManager`, con su default. Cuando
 *     el reproductor sea propio (media3), la aceleración se consulta de verdad con
 *     `MediaCodecInfo` y entonces se informa con datos medidos.
 *   • No escanea el almacenamiento: la biblioteca empieza vacía en cada proceso.
 *   • No reproduce en streaming. HLS/IPTV necesita media3, que no está en el
 *     proyecto: el `MediaPlayer` del framework no resuelve playlists .m3u8.
 *
 * `play()` necesitaba un `Context` que nadie le daba. El campo `appContext` se
 * declaraba y nunca se asignaba, así que el método devolvía `false` siempre por más
 * correcto que estuviera el resto del código: una comprobación de archivo perfecta
 * detrás de un `?: return false` inalcanzable. Ahora el gestor de módulos inyecta el
 * contexto de la aplicación en el config (ver `ModuleLifecycleManager`).
 */
class VideoModule : ModuleInterface() {

    override val name = "video"
    override val version = "0.1.0"
    override val dependencies: List<String> = emptyList()

    private val library = mutableListOf<VideoItem>()
    private var appContext: android.content.Context? = null

    override fun initialize(config: Map<String, Any>): Boolean {
        appContext = config["context"] as? android.content.Context
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

    override fun healthCheck(): ModuleHealth = ModuleHealth(
        name, version,
        if (_started) "healthy" else "stopped",
        mapOf<String, Any?>(
            "library_items" to library.size,
            "reproduccion" to "delegada al reproductor del sistema",
            "contexto_disponible" to (appContext != null),
            "streaming" to "no implementado",
        )
    )

    fun addToLibrary(item: VideoItem) {
        library.add(item)
    }

    fun listLibrary(): List<VideoItem> = library.toList()

    /**
     * Abre el vídeo con el reproductor del sistema. Devuelve `true` sólo si el
     * Intent se lanzó de verdad.
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
            android.util.Log.w(
                "ceroX.video",
                "ningún reproductor aceptó ${archivo.name}: ${e.javaClass.simpleName}",
            )
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
