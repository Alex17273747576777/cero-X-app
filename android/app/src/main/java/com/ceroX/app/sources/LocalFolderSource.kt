package com.ceroX.app.sources

import eu.kanade.tachiyomi.source.Page
import eu.kanade.tachiyomi.source.SerieNavegable
import eu.kanade.tachiyomi.source.SeriePage
import eu.kanade.tachiyomi.source.Source

/**
 * LA FUENTE QUE NO MIENTE: lee lo que el usuario ya tiene descargado.
 *
 * POR QUÉ HACE FALTA ESTA Y NO SOLO LAS EXTENSIONES
 * ------------------------------------------------
 * Con solo extensiones, la app depende de terceros para **todo**: sin red, sin
 * extensión instalada o sin un repo vivo, el lector no tiene una sola página que
 * pintar. Eso hace que «el lector funciona» sea una frase que solo se puede
 * comprobar con internet y con una extensión de alguien más.
 *
 * Esta fuente lee una carpeta del teléfono. Siempre está, funciona en modo
 * avión, y es lo que hace que el lector se pueda probar de verdad:
 *
 *   - una imagen suelta  → una serie de un capítulo de una página
 *   - una carpeta        → una serie, y cada subcarpeta un capítulo
 *
 * QUÉ NO HACE
 * -----------
 * No ordena «naturalmente» números romanos, ni temporadas, ni nada que no esté
 * demostrado. Orden natural de dígitos sí, porque es lo que evita que el
 * capítulo 10 salga antes que el 2, y eso sí es un fallo visible.
 */
class LocalFolderSource(
    override val id: Long = ID_POR_DEFECTO,
    private val raiz: java.io.File,
) : Source, SerieNavegable {

    override val name: String = "Carpeta local"
    override val lang: String = "es"
    override val baseUrl: String get() = raiz.absolutePath

    /** Lo que esta fuente puede leer. Se declara para no prometer otros formatos. */
    val extensionesSoportadas: List<String> = listOf("jpg", "jpeg", "png", "webp", "gif", "avif", "bmp")

    override fun getCoverUrl(serieUrl: String): String? {
        val carpeta = java.io.File(serieUrl)
        return primeraImagen(carpeta)?.absolutePath
    }

    override suspend fun getSerie(url: String): eu.kanade.tachiyomi.source.Serie {
        val carpeta = java.io.File(url)
        val capitulos = getChapters(url)
        return eu.kanade.tachiyomi.source.Serie(
            url = url,
            title = carpeta.name.ifBlank { "Sin título" },
            thumbnailUrl = getCoverUrl(url),
            chapters = capitulos,
        )
    }

    /**
     * Capítulos de una carpeta.
     *
     * Una carpeta sin subcarpetas es un capítulo único. Una carpeta con
     * subcarpetas tiene tantos capítulos como subcarpetas **con imágenes**: una
     * subcarpeta vacía no es un capítulo vacío, es basura del gestor de archivos.
     */
    override suspend fun getChapters(url: String): List<eu.kanade.tachiyomi.source.Chapter> {
        val carpeta = java.io.File(url)
        if (!carpeta.isDirectory) return emptyList()

        val subcarpetas = carpeta.listFiles()
            ?.filter { it.isDirectory && primeraImagen(it) != null }
            ?.sortedWith(porNombre)
            .orEmpty()

        if (subcarpetas.isEmpty()) {
            return if (primeraImagen(carpeta) != null) {
                listOf(
                    eu.kanade.tachiyomi.source.Chapter(
                        url = carpeta.absolutePath,
                        name = "Capítulo único",
                        number = 1f,
                    )
                )
            } else emptyList()
        }

        return subcarpetas.map { sub ->
            eu.kanade.tachiyomi.source.Chapter(
                url = sub.absolutePath,
                name = sub.name,
                number = numeroDe(sub.name),
            )
        }
    }

    /**
     * Páginas de un capítulo, en orden natural y sin duplicar.
     *
     * Se ordena por nombre con el comparador natural y **se ignora** cualquier
     * archivo que no sea una imagen: un `.nfo`, un `.txt` de créditos o un
     * `Thumbs.db` no son páginas y pintarlos como rotos es peor que saltarlos.
     */
    override suspend fun getPages(urlChapter: String): List<Page> {
        val carpeta = java.io.File(urlChapter)
        val imagenes: List<java.io.File> = when {
            carpeta.isDirectory -> carpeta.listFiles()?.filter { esImagen(it) }.orEmpty()
            esImagen(carpeta) -> listOf(carpeta)
            else -> emptyList()
        }
        return imagenes
            .sortedWith(porNombre)
            .mapIndexed { i, f -> Page(index = i, url = f.absolutePath, imageUrl = f.absolutePath) }
    }

    /** Series que esta fuente puede ofrecer: las carpetas de primer nivel. */
    suspend fun series(): List<SeriePage> =
        raiz.listFiles()
            ?.filter { it.isDirectory && primeraImagen(it) != null }
            ?.sortedWith(porNombre)
            ?.map { SeriePage(url = it.absolutePath, title = it.name, thumbnailUrl = getCoverUrl(it.absolutePath)) }
            .orEmpty()

    private fun esImagen(f: java.io.File): Boolean =
        f.isFile && f.length() > 0 && f.extension.lowercase() in extensionesSoportadas

    private fun primeraImagen(carpeta: java.io.File): java.io.File? {
        if (!carpeta.isDirectory) return null
        val directa = carpeta.listFiles()?.filter { esImagen(it) }.orEmpty()
        if (directa.isNotEmpty()) return directa.minWithOrNull(porNombre)
        // Un nivel más abajo: es el caso normal de «Serie/Capítulo 001/01.jpg»
        return carpeta.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedWith(porNombre)
            ?.firstNotNullOfOrNull { primeraImagen(it) }
    }

    companion object {
        const val ID_POR_DEFECTO: Long = 1L

        /**
         * Comparador de archivos por nombre natural.
         *
         * Se declara aquí y no se repite en cada `sortedWith` porque hay cuatro
         * sitios que ordenan y los cuatro tienen que ordenar **igual**: si la
         * lista de capítulos ordena de una forma y las páginas de otra, el
         * capítulo 10 abre por la página 2.
         */
        private val porNombre: Comparator<java.io.File> = Comparator { a, b ->
            val c = natural(a.name, b.name)
            if (c != 0) c else a.name.compareTo(b.name)
        }

        /**
         * Comparador natural: «2» antes que «10».
         *
         * Es el único trozo de esta clase que merece una prueba propia, porque
         * el orden por defecto de las cadenas pone el 10 antes que el 2 en todas
         * las listas de archivos del planeta. Se compara tramo a tramo: si los
         * dos son dígitos, se comparan como número; si no, como texto.
         */
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

        /** Extrae un número del nombre del capítulo; -1 si no hay ninguno. */
        fun numeroDe(nombre: String): Float {
            val m = Regex("""(\d+(?:[.,]\d+)?)""").find(nombre) ?: return -1f
            return m.groupValues[1].replace(',', '.').toFloatOrNull() ?: -1f
        }
    }
}
