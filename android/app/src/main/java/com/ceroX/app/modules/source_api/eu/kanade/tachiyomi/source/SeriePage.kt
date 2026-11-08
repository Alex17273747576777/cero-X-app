package eu.kanade.tachiyomi.source

/**
 * Un resultado de catálogo, mínimo: lo que se puede pintar en una lista sin
 * pedir la ficha completa. Los campos opcionales son opcionales de verdad: hay
 * fuentes que no publican portada, y obligarlas a inventar una rompe la lista.
 */
data class SeriePage(
    val url: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val artist: String? = null,
    val author: String? = null,
    val description: String? = null,
    val genre: String? = null,
    val status: Int = 0,
    val initialized: Boolean = false,
)

/** Ficha completa de una serie. */
data class Serie(
    val url: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val artist: String? = null,
    val author: String? = null,
    val description: String? = null,
    val genre: List<String> = emptyList(),
    val status: Int = 0,
    val chapters: List<Chapter> = emptyList(),
)

/** Un capítulo de una serie. */
data class Chapter(
    val url: String,
    val name: String,
    val number: Float = -1f,
    val uploadedDate: Long = 0L,
    val scanlator: String? = null,
)

/** Una página dentro de un capítulo. */
data class Page(
    val index: Int,
    val url: String,
    val imageUrl: String? = null,
)
