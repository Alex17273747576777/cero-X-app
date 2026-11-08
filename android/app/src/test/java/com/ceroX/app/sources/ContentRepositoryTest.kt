package com.ceroX.app.sources

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Chapter
import eu.kanade.tachiyomi.source.Page
import eu.kanade.tachiyomi.source.Serie
import eu.kanade.tachiyomi.source.SerieNavegable
import eu.kanade.tachiyomi.source.SeriePage
import eu.kanade.tachiyomi.source.Source
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas del orquestador de fuentes.
 *
 * La regla que más importa aquí es la que evita que una extensión de terceros
 * tumbe la app: **una fuente que falla no puede llevarse la búsqueda entera**.
 * Se prueba con fuentes que lanzan `Exception` y con fuentes que lanzan
 * `Error`, porque las dos cosas pasan de verdad con APKs compilados contra otra
 * versión del contrato.
 */
class ContentRepositoryTest {

    private class FuenteFalsa(
        override val id: Long,
        override val name: String,
        private val resultados: List<SeriePage> = emptyList(),
        private val lanza: Throwable? = null,
    ) : CatalogueSource, SerieNavegable {
        override val lang: String = "es"

        override suspend fun getPopular(page: Int): List<SeriePage> {
            lanza?.let { throw it }
            return resultados
        }

        override suspend fun getLatestUpdates(page: Int): List<SeriePage> {
            lanza?.let { throw it }
            return resultados
        }

        override suspend fun search(query: String, page: Int): List<SeriePage> {
            lanza?.let { throw it }
            return resultados
        }

        override suspend fun getSerie(url: String): Serie =
            Serie(url = url, title = "Obra", chapters = listOf(Chapter(url = "$url/1", name = "Cap 1", number = 1f)))

        override suspend fun getPages(urlChapter: String): List<Page> =
            listOf(Page(0, "$urlChapter/p1"), Page(1, "$urlChapter/p2"))
    }

    private class FuenteSoloCatalogo(
        override val id: Long,
        override val name: String,
    ) : Source {
        override val lang: String = "es"
    }

    @Test
    fun `sin fuentes la busqueda esta vacia y no lanza`() = runBlocking {
        val repo = ContentRepository(emptyList())
        val r = repo.buscar("luffy")
        assertTrue(r.resultados.isEmpty())
        assertTrue(r.estados.isEmpty())
        assertEquals(false, repo.hayFuentes)
        assertEquals(false, repo.hayFuentesNavegables)
    }

    @Test
    fun `una fuente que lanza Exception no tumba a las demas`() = runBlocking {
        val buena = FuenteFalsa(1, "buena", listOf(SeriePage("u1", "One Piece")))
        val mala = FuenteFalsa(2, "mala", lanza = IllegalStateException("el sitio cambió de HTML"))

        val r = ContentRepository(listOf(buena, mala)).buscar("one")

        assertEquals(1, r.resultados.size)
        assertEquals(2, r.estados.size)
        assertEquals(1, r.fuentesOk)
        assertEquals(1, r.fallos.size)
        assertTrue(r.fallos[0].error!!.contains("el sitio cambió de HTML"))
    }

    @Test
    fun `una fuente que lanza Error tampoco tumba la busqueda`() = runBlocking {
        // NoClassDefFoundError es lo que pasa de verdad cuando una extensión se
        // compiló contra otra versión del contrato. No es una Exception.
        val buena = FuenteFalsa(1, "buena", listOf(SeriePage("u1", "Berserk")))
        val rota = FuenteFalsa(2, "rota", lanza = NoClassDefFoundError("eu/kanade/tachiyomi/source/Source"))

        val r = ContentRepository(listOf(buena, rota)).buscar("berserk")

        assertEquals(1, r.resultados.size)
        assertEquals(1, r.fallos.size)
        assertTrue(r.fallos[0].error!!.contains("NoClassDefFoundError"))
    }

    @Test
    fun `el resultado dice de que fuente viene`() = runBlocking {
        val f = FuenteFalsa(7, "MangaDex", listOf(SeriePage("u", "Obra")))
        val r = ContentRepository(listOf(f)).buscar("x")
        assertEquals("MangaDex", r.resultados[0].fuente)
        assertEquals(7L, r.resultados[0].fuenteId)
    }

    @Test
    fun `se respeta el tope de resultados por pagina`() = runBlocking {
        val muchos = (1..100).map { SeriePage("u$it", "Obra $it") }
        val r = ContentRepository(listOf(FuenteFalsa(1, "f", muchos)), porPagina = 30).buscar("x")
        assertEquals(30, r.resultados.size)
    }

    @Test
    fun `una fuente solo de catalogo no se marca como fallo al buscar`() = runBlocking {
        // No implementa CatalogueSource: no se le pide nada, así que no puede
        // salir como fallo. Contarla como error haría que el resumen mintiera.
        val repo = ContentRepository(listOf(FuenteSoloCatalogo(9, "solo-catalogo")))
        val r = repo.buscar("x")
        assertTrue(r.estados.isEmpty())
        assertEquals(1, repo.listaFuentes().size)
    }

    @Test
    fun `el detalle de una fuente que no existe devuelve null, no lanza`() = runBlocking {
        val repo = ContentRepository(listOf(FuenteFalsa(1, "f")))
        assertNull(repo.detalle(99, "url"))
    }

    @Test
    fun `el detalle trae los capitulos de la fuente`() = runBlocking {
        val repo = ContentRepository(listOf(FuenteFalsa(1, "f")))
        val d = repo.detalle(1, "serie/1")
        assertNotNull(d)
        assertEquals("Obra", d!!.titulo)
        assertEquals(1, d.capitulos.size)
    }

    @Test
    fun `las paginas llegan con su indice y su url`() = runBlocking {
        val repo = ContentRepository(listOf(FuenteFalsa(1, "f")))
        val paginas = repo.paginas(1, "cap/1")
        assertEquals(2, paginas.size)
        assertEquals(0, paginas[0].indice)
        assertTrue(paginas[1].url.endsWith("p2"))
    }

    @Test
    fun `una fuente que falla al dar paginas devuelve lista vacia, no excepcion`() = runBlocking {
        val repo = ContentRepository(emptyList())
        assertTrue(repo.paginas(1, "cap").isEmpty())
    }

    @Test
    fun `ultimas actualizaciones tambien aisla los fallos`() = runBlocking {
        val buena = FuenteFalsa(1, "buena", listOf(SeriePage("u", "A")))
        val mala = FuenteFalsa(2, "mala", lanza = RuntimeException("timeout"))
        val r = ContentRepository(listOf(buena, mala)).ultimasActualizaciones()
        assertEquals(1, r.resultados.size)
        assertEquals(1, r.fallos.size)
    }

    @Test
    fun `la etiqueta del capitulo respeta el numero de la fuente`() {
        assertEquals("Capítulo 12", ContentRepository.Capitulo("c", "u", 12f, 0L, null).etiqueta)
        // Sin número, se usa el nombre tal cual: inventar «Capítulo 0» sería peor.
        assertEquals("Extras", ContentRepository.Capitulo("Extras", "u", -1f, 0L, null).etiqueta)
    }

    @Test
    fun `formatearNumero no pone decimales que no existen`() {
        assertEquals("3", ContentRepository.formatearNumero(3f))
        assertEquals("3.5", ContentRepository.formatearNumero(3.5f))
        assertEquals("", ContentRepository.formatearNumero(-1f))
    }

    @Test
    fun `la fecha cero se conserva como desconocida`() {
        // Si la fuente no da fecha, no se rellena con hoy: eso falsearía el
        // orden de la lista de capítulos.
        val c = ContentRepository.Capitulo("Cap 1", "u", 1f, 0L, null)
        assertEquals(0L, c.fechaMs)
    }
}
