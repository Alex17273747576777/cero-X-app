package com.ceroX.app.modules.feed

/**
 * Modelos del feed multi-entrada.
 *
 * ORIGEN DE LAS IDEAS, Y QUÉ SE COPIÓ DE CADA UNA
 * -----------------------------------------------
 * AniZen aporta la idea de que el inicio no es una lista sino varias listas a la
 * vez (`ui/home/FeedScreen.kt`, `FeedManageScreen.kt`, 35 archivos de feed).
 * ShinKu aporta que cada feed tenga su propio cursor de paginación por fuente
 * (`ui/browse/feed/SourcePageCursor.kt`) en vez de un único contador global, que
 * es lo que permite mezclar fuentes de ritmo distinto sin que unas bloqueen a
 * otras. Kisara aporta el sistema de sugerencias calculadas en el teléfono
 * (`data/suggestions/SuggestionsWorker.kt`), sin enviar el historial a ningún
 * servidor.
 *
 * Lo que NO se copió: nada de código. AniZen y ShinKu son Apache-2.0 y Kisara
 * también, así que se podría; pero están en el paquete `eu.kanade` y dependen de
 * su base de datos. Se reescribió la idea con las piezas que cero-X ya tiene.
 */

/** Los tipos de entrada que puede tener el inicio. */
enum class FeedKind(val etiqueta: String, val descripcion: String) {
    /** Lo que el usuario sigue en su biblioteca, ordenado por lo último que salió. */
    BIBLIOTECA("Mi biblioteca", "Novedades de lo que ya sigues"),

    /** Lo que un proveedor remoto (AniList, MAL) cree que te va a gustar. */
    DESCUBRIR("Descubrir", "Lo que las listas de fuera recomiendan para ti"),

    /** Calculado en el teléfono con lo que ya lees. Nada sale del aparato. */
    SUGERENCIAS("Para ti", "Calculado en el teléfono, sin enviar tu historial"),

    /** Los que leíste y no terminaste, para retomarlos. */
    CONTINUAR("Continuar leyendo", "Lo que dejaste a medias"),

    /** Los que actualizaron hace poco y aún no has abierto. */
    NUEVOS("Nuevos capítulos", "Actualizaciones que todavía no has visto"),

    /** Una fuente concreta, para quien quiera el inicio solo de una. */
    FUENTE("Una fuente", "Solo las novedades de una fuente elegida"),
}

/** Un elemento del feed, ya resuelto y listo para pintarse. */
data class FeedItem(
    val id: String,
    val titulo: String,
    val portadaUrl: String?,
    val fuente: String,
    val url: String,
    val capituloNuevo: String?,
    val actualizadoEn: Long,
    /** Por qué está aquí. Se muestra al usuario: un feed que no se explica es ruido. */
    val motivo: String,
    /** De 0 a 1. Solo lo tienen los feeds calculados; el resto va a 0. */
    val afinidad: Float = 0f,
)

/**
 * Estado de una entrada del feed.
 *
 * `cursor` es por entrada, no global: dos fuentes que paginan a ritmos distintos
 * no se pisan. Es la idea que ShinKu resuelve con `SourcePageCursor`.
 */
data class FeedEntry(
    val id: String,
    val kind: FeedKind,
    val titulo: String,
    val fuente: String? = null,
    val cursor: String? = null,
    val agotado: Boolean = false,
    val erroresSeguidos: Int = 0,
    val ultimoError: String? = null,
    /** Las entradas desactivadas no se cargan pero no se borran. */
    val activa: Boolean = true,
)

/** Lo que devuelve pedir una página de una entrada. */
data class FeedPage(
    val items: List<FeedItem>,
    val siguienteCursor: String?,
    val agotado: Boolean,
    val aviso: String? = null,
)

/**
 * Límites del feed.
 *
 * Un feed multi-entrada es una máquina de pedir cosas en paralelo. Sin topes,
 * seis entradas × paginación sin fin = el teléfono caliente y la batería en el
 * suelo. Estos números salen de lo que tarda la app en pintar sin que se note:
 * 40 elementos por entrada es más de lo que cabe en dos pantallas.
 */
object FeedLimits {
    const val ENTRADAS_MAXIMAS = 12
    const val ITEMS_POR_PAGINA = 40
    /** Tras este número de fallos seguidos, la entrada se aparca sola. */
    const val FALLOS_ANTES_DE_APARCAR = 3
    /** Cuánto se espera antes de reintentar una entrada que falló. */
    const val ESPERA_REINTENTO_MS = 30_000L * 5
    /** Afinidad mínima para que una sugerencia llegue al usuario. */
    const val AFINIDAD_MINIMA = 0.35f
}
