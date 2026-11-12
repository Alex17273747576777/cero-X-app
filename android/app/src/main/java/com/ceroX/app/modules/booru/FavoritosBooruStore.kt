package com.ceroX.app.modules.booru

import android.content.Context
import com.ceroX.app.core.AlmacenExterno
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable private data class DatosFavoritos(val version: Int = 1, val posts: List<BooruPost> = emptyList())

/** Favoritos disponibles sin red; no incluye credenciales ni archivos binarios. */
class FavoritosBooruStore(context: Context) {
    private val archivo: File? = AlmacenExterno.directorio(context)?.let { File(it, "booru/favoritos.json") }
    private val json = Json { ignoreUnknownKeys = true }
    private var posts: List<BooruPost> = leer()

    @Synchronized fun listar(): List<BooruPost> = posts
    @Synchronized fun ids(): Set<Int> = posts.map { it.id }.toSet()
    @Synchronized fun disponible(): Boolean = archivo != null

    @Synchronized fun alternar(post: BooruPost): Boolean {
        val existe = posts.any { it.id == post.id }
        posts = if (existe) posts.filterNot { it.id == post.id }
        else (listOf(post) + posts).take(MAX_FAVORITOS)
        guardar()
        return !existe
    }

    private fun leer(): List<BooruPost> = runCatching {
        val f = archivo ?: return emptyList()
        if (!f.isFile || f.length() > MAX_BYTES) return emptyList()
        json.decodeFromString(DatosFavoritos.serializer(), f.readText()).posts.filter {
            it.rating in setOf("safe", "s") && urlMediaSafebooru(it.url) != null
        }.take(MAX_FAVORITOS)
    }.getOrDefault(emptyList())

    private fun guardar() {
        val destino = archivo ?: return
        destino.parentFile?.mkdirs()
        val tmp = File(destino.parentFile, destino.name + ".tmp")
        runCatching {
            tmp.writeText(json.encodeToString(DatosFavoritos.serializer(), DatosFavoritos(posts = posts)))
            if (!tmp.renameTo(destino)) {
                destino.writeText(tmp.readText())
                tmp.delete()
            }
        }
    }

    companion object {
        const val MAX_FAVORITOS = 100
        private const val MAX_BYTES = 2L * 1024 * 1024
    }
}
