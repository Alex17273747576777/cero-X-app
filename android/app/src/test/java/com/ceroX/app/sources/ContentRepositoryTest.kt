package com.ceroX.app.sources

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentRepositoryTest {
    private class FuenteFalsa(
        override val id: Long,
        override val name: String,
        private val resultados: List<SManga> = emptyList(),
        private val lanza: Throwable? = null,
        override val supportsLatest: Boolean = true,
    ) : Source {
        override val lang: String = "es"
        override fun getFilterList(): FilterList = FilterList()

        override suspend fun getPopularManga(page: Int): MangasPage {
            lanza?.let { throw it }
            return MangasPage(resultados, false)
        }

        override suspend fun getLatestUpdates(page: Int): MangasPage = getPopularManga(page)

        override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage =
            getPopularManga(page)

        override suspend fun getMangaUpdate(
            manga: SManga,
            chapters: List<SChapter>,
            fetchDetails: Boolean,
            fetchChapters: Boolean,
        ): SMangaUpdate {
            val detalle = SManga.create().apply {
                url = manga.url
                title = "Obra"
                genre = "Acción, Drama"
            }
            val capitulo = SChapter.create().apply {
                url = "${manga.url}/1"
                name = "Cap 1"
                chapter_number = 1f
            }
            return SMangaUpdate(detalle, listOf(capitulo))
        }

        override suspend fun getPageList(chapter: SChapter): List<Page> =
            listOf(Page(0, "${chapter.url}/p1"), Page(1, "${chapter.url}/p2"))
    }

    private fun obra(url: String, titulo: String) = SManga.create().apply {
        this.url = url
        title = titulo
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
        val buena = FuenteFalsa(1, "buena", listOf(obra("u1", "One Piece")))
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
        val buena = FuenteFalsa(1, "buena", listOf(obra("u1", "Berserk")))
        val rota = FuenteFalsa(2, "rota", lanza = NoClassDefFoundError("eu/kanade/tachiyomi/source/Source"))
        val r = ContentRepository(listOf(buena, rota)).buscar("berserk")
        assertEquals(1, r.resultados.size)
        assertEquals(1, r.fallos.size)
        assertTrue(r.fallos[0].error!!.contains("NoClassDefFoundError"))
    }

    @Test
    fun `el resultado dice de que fuente viene`() = runBlocking {
        val f = FuenteFalsa(7, "MangaDex", listOf(obra("u", "Obra")))
        val r = ContentRepository(listOf(f)).buscar("x")
        assertEquals("MangaDex", r.resultados[0].fuente)
        assertEquals(7L, r.resultados[0].fuenteId)
    }

    @Test
    fun `se respeta el tope de resultados por pagina`() = runBlocking {
        val muchos = (1..100).map { obra("u$it", "Obra $it") }
        val r = ContentRepository(listOf(FuenteFalsa(1, "f", muchos)), porPagina = 30).buscar("x")
        assertEquals(30, r.resultados.size)
    }

    @Test
    fun `toda fuente 1_6 es navegable y related queda en CatalogueSource`() = runBlocking {
        val repo = ContentRepository(listOf(FuenteFalsa(9, "contrato-1.6")))
        val r = repo.buscar("x")
        assertEquals(1, r.estados.size)
        assertEquals(1, repo.listaFuentes().size)
        assertTrue(repo.fuentesConRelacionados().isEmpty())
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
        assertEquals(listOf("Acción", "Drama"), d.generos)
    }

    @Test
    fun `las paginas conservan Page y Source para resolver imagenes`() = runBlocking {
        val repo = ContentRepository(listOf(FuenteFalsa(1, "f")))
        val paginas = repo.paginas(1, "cap/1")
        assertEquals(2, paginas.size)
        assertEquals(0, paginas[0].indice)
        assertTrue(paginas[1].url.endsWith("p2"))
        assertEquals(paginas[1].pagina.imageUrl, paginas[1].imagenUrl)
        assertEquals(1L, paginas[0].fuente.id)
    }

    @Test
    fun `un fallo de paginas tiene motivo y no se confunde con capitulo vacio`() = runBlocking {
        val repo = ContentRepository(emptyList())
        val r = repo.paginasDetallado(1, "cap")
        assertTrue(r.paginas.isEmpty())
        assertTrue(r.error!!.contains("no está registrada"))
    }

    @Test
    fun `ultimas actualizaciones omite fuentes que no lo soportan`() = runBlocking {
        val buena = FuenteFalsa(1, "buena", listOf(obra("u", "A")))
        val mala = FuenteFalsa(2, "mala", lanza = RuntimeException("timeout"))
        val sinFeed = FuenteFalsa(3, "local", supportsLatest = false)
        val r = ContentRepository(listOf(buena, mala, sinFeed)).ultimasActualizaciones()
        assertEquals(1, r.resultados.size)
        assertEquals(1, r.fallos.size)
        assertEquals(2, r.estados.size)
    }

    @Test
    fun `la etiqueta del capitulo respeta el numero de la fuente`() {
        assertEquals("Capítulo 12", ContentRepository.Capitulo("c", "u", 12f, 0L, null).etiqueta)
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
        val c = ContentRepository.Capitulo("Cap 1", "u", 1f, 0L, null)
        assertEquals(0L, c.fechaMs)
    }
}
