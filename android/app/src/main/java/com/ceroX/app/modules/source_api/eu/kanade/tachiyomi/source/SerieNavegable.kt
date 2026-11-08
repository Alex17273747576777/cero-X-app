package eu.kanade.tachiyomi.source

/**
 * La segunda mitad del contrato: lo que una fuente devuelve.
 *
 * POR QUÉ NO ESTÁ DENTRO DE CatalogueSource
 * -----------------------------------------
 * Se importa así porque el contrato real se lee así: una extensión de Mihon que
 * quiera ser navegable implementa `CatalogueSource` **y** `Source`, y el
 * anfitrión llama a `getSerie` / `getPages` sobre `Source`. Si aquí se metieran
 * dentro de `CatalogueSource`, una extensión que solo implementa `Source` no
 * podría dar capítulos, y hay fuentes del ecosistema que no son navegables y sí
 * tienen capítulos.
 *
 * POR QUÉ ES UNA INTERFAZ APARTE
 * ------------------------------
 * `Source` es ya el contrato mínimo que una extensión **tiene** que implementar
 * para cargar. Añadirle métodos abstractos rompería todas las extensiones que
 * hoy solo usan el catálogo. Al declararlos en una segunda interfaz, una
 * extensión puede dar catálogo sin dar capítulos, o al revés, y ambas cargan.
 *
 * SIN VALORES POR DEFECTO
 * -----------------------
 * `getSerie` y `getPages` no tienen implementación por defecto a propósito: una
 * fuente que no sabe hacerlo debe decirlo no implementando la interfaz, y no
 * devolviendo una lista vacía que el lector interpretaría como «capítulo sin
 * páginas».
 */
interface SerieNavegable {

    /** Ficha completa de una serie, a partir de su URL. */
    suspend fun getSerie(url: String): Serie {
        throw UnsupportedOperationException("esta fuente no implementa getSerie")
    }

    /** Capítulos de una serie. */
    suspend fun getChapters(url: String): List<Chapter> = emptyList()

    /** Páginas de un capítulo, en orden. */
    suspend fun getPages(urlChapter: String): List<Page> {
        throw UnsupportedOperationException("esta fuente no implementa getPages")
    }
}
