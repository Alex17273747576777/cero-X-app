package com.ceroX.app.sources

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import java.io.File

/** Fuente local implementando el mismo ABI 1.6 que una extensión Nox. */
class LocalFolderSource(
    override val id: Long = ID_POR_DEFECTO,
    private val raiz: File,
) : Source {
    override val name: String = "Carpeta local"
    override val lang: String = "es"
    override val supportsLatest: Boolean = false
    val baseUrl: String get() = raiz.absolutePath
    val extensionesSoportadas = listOf("jpg", "jpeg", "png", "webp", "gif", "avif", "bmp")

    override fun getFilterList(): FilterList = FilterList()

    override suspend fun getPopularManga(page: Int): MangasPage =
        MangasPage(if (page == 1) series() else emptyList(), hasNextPage = false)

    override suspend fun getLatestUpdates(page: Int): MangasPage =
        throw UnsupportedOperationException("La carpeta local no tiene feed de actualizaciones")

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage =
        MangasPage(
            if (page == 1) series().filter { it.title.contains(query.trim(), ignoreCase = true) } else emptyList(),
            hasNextPage = false,
        )

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val detalle = if (fetchDetails) getSerie(manga.url) else manga
        val lista = if (fetchChapters) getChapters(manga.url) else chapters
        return SMangaUpdate(detalle, lista)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = getPages(chapter.url)

    fun getCoverUrl(serieUrl: String): String? = primeraImagen(File(serieUrl))?.absolutePath

    fun getSerie(url: String): SManga {
        val carpeta = File(url)
        return SManga.create().apply {
            this.url = url
            title = carpeta.name.ifBlank { "Sin título" }
            thumbnail_url = getCoverUrl(url)
            initialized = true
        }
    }

    fun getChapters(url: String): List<SChapter> {
        val carpeta = File(url)
        if (!carpeta.isDirectory) return emptyList()
        val subcarpetas = carpeta.listFiles()
            ?.filter { it.isDirectory && primeraImagen(it) != null }
            ?.sortedWith(porNombre).orEmpty()
        if (subcarpetas.isEmpty()) {
            return if (primeraImagen(carpeta) != null) {
                listOf(capitulo(carpeta.absolutePath, "Capítulo único", 1f))
            } else emptyList()
        }
        return subcarpetas.map { capitulo(it.absolutePath, it.name, numeroDe(it.name)) }
    }

    private fun capitulo(url: String, nombre: String, numero: Float) = SChapter.create().apply {
        this.url = url
        name = nombre
        chapter_number = numero
    }

    fun getPages(urlChapter: String): List<Page> {
        val carpeta = File(urlChapter)
        val imagenes = when {
            carpeta.isDirectory -> carpeta.listFiles()?.filter { esImagen(it) }.orEmpty()
            esImagen(carpeta) -> listOf(carpeta)
            else -> emptyList()
        }
        return imagenes.sortedWith(porNombre).mapIndexed { i, f ->
            Page(index = i, url = f.absolutePath, imageUrl = f.absolutePath)
        }
    }

    fun series(): List<SManga> = raiz.listFiles()
        ?.filter { it.isDirectory && primeraImagen(it) != null }
        ?.sortedWith(porNombre)
        ?.map { getSerie(it.absolutePath) }
        .orEmpty()

    private fun esImagen(f: File): Boolean =
        f.isFile && f.length() > 0 && f.extension.lowercase() in extensionesSoportadas

    private fun primeraImagen(carpeta: File): File? {
        if (!carpeta.isDirectory) return null
        val directa = carpeta.listFiles()?.filter { esImagen(it) }.orEmpty()
        if (directa.isNotEmpty()) return directa.minWithOrNull(porNombre)
        return carpeta.listFiles()?.filter { it.isDirectory }?.sortedWith(porNombre)
            ?.firstNotNullOfOrNull { primeraImagen(it) }
    }

    companion object {
        const val ID_POR_DEFECTO: Long = 1L
        private val porNombre: Comparator<File> = Comparator { a, b ->
            val c = natural(a.name, b.name)
            if (c != 0) c else a.name.compareTo(b.name)
        }

        fun natural(a: String, b: String): Int {
            var i = 0
            var j = 0
            while (i < a.length && j < b.length) {
                val ca = a[i]
                val cb = b[j]
                if (ca.isDigit() && cb.isDigit()) {
                    var ia = i
                    var jb = j
                    while (ia < a.length && a[ia].isDigit()) ia++
                    while (jb < b.length && b[jb].isDigit()) jb++
                    val na = a.substring(i, ia).trimStart('0').ifEmpty { "0" }
                    val nb = b.substring(j, jb).trimStart('0').ifEmpty { "0" }
                    val cmp = if (na.length != nb.length) na.length - nb.length else na.compareTo(nb)
                    if (cmp != 0) return cmp
                    i = ia
                    j = jb
                } else {
                    val cmp = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                    if (cmp != 0) return cmp
                    i++
                    j++
                }
            }
            return (a.length - i) - (b.length - j)
        }

        fun numeroDe(nombre: String): Float {
            val m = Regex("""(\d+(?:[.,]\d+)?)""").find(nombre) ?: return -1f
            return m.groupValues[1].replace(',', '.').toFloatOrNull() ?: -1f
        }
    }
}
