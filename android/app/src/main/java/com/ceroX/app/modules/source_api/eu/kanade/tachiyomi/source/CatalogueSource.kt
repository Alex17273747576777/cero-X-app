package eu.kanade.tachiyomi.source

/** Tipo de ordenación que una fuente puede ofrecer. */
data class SortFilter(val name: String, val value: Int)

/**
 * Fuente con catálogo navegable: popular, últimas actualizaciones y búsqueda.
 *
 * Es la interfaz que implementan casi todas las extensiones de Mihon.
 * Una extensión que solo implementa [Source] necesita una URL conocida;
 * una que implementa [CatalogueSource] puede ser explorada sin saber nada de
 * antemano, y por eso el 90 % del ecosistema implementa esta.
 */
interface CatalogueSource : Source {

    /** Lista de ordenaciones disponibles para la búsqueda. */
    val sortFilter: List<SortFilter>
        get() = emptyList()

    /** Series populares, paginadas. */
    suspend fun getPopular(page: Int): List<SeriePage>

    /** Últimas actualizaciones, paginadas. */
    suspend fun getLatestUpdates(page: Int): List<SeriePage>

    /** Búsqueda por texto, paginada. */
    suspend fun search(query: String, page: Int): List<SeriePage>

    /** Filtro de búsqueda, si la fuente lo soporta. */
    val supportsFilter: Boolean
        get() = false
}
