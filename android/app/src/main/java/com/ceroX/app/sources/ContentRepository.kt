package com.ceroX.app.sources

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource

/** Adapta extensions-lib 1.6 al catálogo y al lector; no crea un segundo contrato. */
class ContentRepository(
    private val fuentes: List<Source>,
    private val porPagina: Int = 30,
) {
    data class EstadoFuente(
        val nombre: String,
        val exito: Boolean,
        val resultados: Int,
        val ms: Long,
        val error: String? = null,
    )

    data class Resultado(
        val titulo: String,
        val url: String,
        val portada: String?,
        val autor: String?,
        val fuente: String,
        val fuenteId: Long,
    )

    data class Busqueda(val resultados: List<Resultado>, val estados: List<EstadoFuente>) {
        val fuentesOk: Int get() = estados.count { it.exito }
        val fallos: List<EstadoFuente> get() = estados.filterNot { it.exito }
    }

    data class Detalle(
        val titulo: String,
        val url: String,
        val portada: String?,
        val autor: String?,
        val descripcion: String?,
        val generos: List<String>,
        val estado: Int,
        val capitulos: List<Capitulo>,
        val fuente: String,
        val fuenteId: Long,
    )

    data class Capitulo(
        val nombre: String,
        val url: String,
        val numero: Float,
        val fechaMs: Long,
        val scanlator: String?,
    ) {
        val etiqueta: String
            get() = if (numero >= 0f) "Capítulo ${formatearNumero(numero)}" else nombre
    }

    /** Conserva Page y Source: sin ellos se perdían getImageUrl/client/headers. */
    data class PaginaLista(
        val indice: Int,
        val url: String,
        val imagenUrl: String?,
        val pagina: Page,
        val fuente: Source,
    )

    data class PaginasCapitulo(
        val paginas: List<PaginaLista>,
        val error: String? = null,
    )

    /** En 1.6 el contrato de catálogo está en Source; CatalogueSource añade relacionados. */
    fun fuentesNavegables(): List<Source> = fuentes
    fun fuentesConRelacionados(): List<CatalogueSource> = fuentes.filterIsInstance<CatalogueSource>()
    fun listaFuentes(): List<Source> = fuentes
    val hayFuentes: Boolean get() = fuentes.isNotEmpty()
    val hayFuentesNavegables: Boolean get() = fuentes.isNotEmpty()

    suspend fun buscar(consulta: String, pagina: Int = 1): Busqueda {
        val resultados = mutableListOf<Resultado>()
        val estados = mutableListOf<EstadoFuente>()
        for (fuente in fuentes) {
            val t0 = System.currentTimeMillis()
            try {
                val crudo = if (consulta.isBlank()) {
                    fuente.getPopularManga(pagina)
                } else {
                    fuente.getSearchManga(pagina, consulta, fuente.getFilterList())
                }
                val convertidos = crudo.mangas.take(porPagina).map { it.aResultado(fuente) }
                resultados += convertidos
                estados += EstadoFuente(fuente.name, true, convertidos.size, System.currentTimeMillis() - t0)
            } catch (e: Throwable) {
                estados += EstadoFuente(
                    fuente.name, false, 0, System.currentTimeMillis() - t0, describir(e),
                )
            }
        }
        return Busqueda(resultados, estados)
    }

    suspend fun ultimasActualizaciones(pagina: Int = 1): Busqueda {
        val resultados = mutableListOf<Resultado>()
        val estados = mutableListOf<EstadoFuente>()
        for (fuente in fuentes.filter { it.supportsLatest }) {
            val t0 = System.currentTimeMillis()
            try {
                val convertidos = fuente.getLatestUpdates(pagina).mangas.take(porPagina)
                    .map { it.aResultado(fuente) }
                resultados += convertidos
                estados += EstadoFuente(fuente.name, true, convertidos.size, System.currentTimeMillis() - t0)
            } catch (e: Throwable) {
                estados += EstadoFuente(fuente.name, false, 0, System.currentTimeMillis() - t0, describir(e))
            }
        }
        return Busqueda(resultados, estados)
    }

    /** Obtiene detalle y capítulos mediante la única operación atómica del ABI 1.6. */
    suspend fun detalle(fuenteId: Long, urlSerie: String): Detalle? {
        val fuente = fuentes.firstOrNull { it.id == fuenteId } ?: return null
        val consulta = SManga.create().apply {
            url = urlSerie
            title = urlSerie
        }
        val actualizada = try {
            fuente.getMangaUpdate(
                manga = consulta,
                chapters = emptyList(),
                fetchDetails = true,
                fetchChapters = true,
            )
        } catch (_: Throwable) {
            return null
        }
        val serie = actualizada.manga
        return Detalle(
            titulo = serie.title,
            url = serie.url,
            portada = serie.thumbnail_url,
            autor = serie.author ?: serie.artist,
            descripcion = serie.description,
            generos = serie.getGenres().orEmpty(),
            estado = serie.status,
            capitulos = actualizada.chapters.map {
                Capitulo(it.name, it.url, it.chapter_number, it.date_upload, it.scanlator)
            },
            fuente = fuente.name,
            fuenteId = fuente.id,
        )
    }

    suspend fun paginasDetallado(fuenteId: Long, urlCapitulo: String): PaginasCapitulo {
        val fuente = fuentes.firstOrNull { it.id == fuenteId }
            ?: return PaginasCapitulo(emptyList(), "La fuente $fuenteId ya no está registrada")
        val capitulo = SChapter.create().apply {
            url = urlCapitulo
            name = urlCapitulo
        }
        return try {
            val crudo = fuente.getPageList(capitulo)
            PaginasCapitulo(crudo.map { PaginaLista(it.index, it.url, it.imageUrl, it, fuente) })
        } catch (e: Throwable) {
            PaginasCapitulo(emptyList(), describir(e))
        }
    }

    /** Compatibilidad interna; el lector usa paginasDetallado para mostrar el error real. */
    suspend fun paginas(fuenteId: Long, urlCapitulo: String): List<PaginaLista> =
        paginasDetallado(fuenteId, urlCapitulo).paginas

    /** null significa que PageLoader debe tratar el origen como archivo/URL local genérica. */
    suspend fun bytesDeImagen(pagina: PaginaLista, maximo: Long): ByteArray? =
        (pagina.fuente as? HttpSource)?.let { SourceImageLoader.descargar(it, pagina.pagina, maximo) }

    private fun SManga.aResultado(fuente: Source) = Resultado(
        titulo = title,
        url = url,
        portada = thumbnail_url,
        autor = author ?: artist,
        fuente = fuente.name,
        fuenteId = fuente.id,
    )

    private fun describir(e: Throwable): String =
        e.message?.takeIf { it.isNotBlank() }?.let { "${e.javaClass.simpleName}: $it" }
            ?: e.javaClass.simpleName

    companion object {
        fun formatearNumero(n: Float): String {
            if (n < 0f) return ""
            val entero = n.toInt()
            return if (n == entero.toFloat()) entero.toString()
            else String.format(java.util.Locale.US, "%.1f", n)
        }
    }
}
