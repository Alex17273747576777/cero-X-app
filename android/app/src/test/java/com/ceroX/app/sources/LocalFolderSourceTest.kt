package com.ceroX.app.sources

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Pruebas de la única fuente de contenido que no depende de nadie.
 *
 * Lo que se prueba aquí es lo que decide si un lector sirve o no: el orden de
 * las páginas, que un archivo que no es imagen no cuente, y que una carpeta
 * vacía no se ofrezca como capítulo. Todo eso se comprueba con archivos reales
 * en un directorio temporal, no con dobles.
 */
class LocalFolderSourceTest {

    private lateinit var raiz: File

    @Before
    fun preparar() {
        raiz = Files.createTempDirectory("cerox-lector").toFile()
    }

    @After
    fun limpiar() {
        raiz.deleteRecursively()
    }

    private fun archivo(ruta: String, contenido: String = "x") {
        val f = File(raiz, ruta)
        f.parentFile?.mkdirs()
        f.writeText(contenido)
    }

    // ── El orden, que es el bug clásico ─────────────────────────────────────

    @Test
    fun `el 2 va antes que el 10`() {
        // Esto es lo que hace que un capítulo se lea en desorden en todas las
        // apps que ordenan con el orden de cadenas por defecto.
        val nombres = listOf("10.jpg", "2.jpg", "1.jpg", "20.jpg", "3.jpg")
        val ordenados = nombres.sortedWith(Comparator { a, b -> LocalFolderSource.natural(a, b).let { if (it != 0) it else a.compareTo(b) } })
        assertEquals(listOf("1.jpg", "2.jpg", "3.jpg", "10.jpg", "20.jpg"), ordenados)
    }

    @Test
    fun `el comparador natural ordena bien con ceros delante`() {
        val nombres = listOf("003.jpg", "01.jpg", "002.jpg", "0001.jpg")
        val ordenados = nombres.sortedWith(Comparator { a, b -> LocalFolderSource.natural(a, b).let { if (it != 0) it else a.compareTo(b) } })
        assertEquals(listOf("0001.jpg", "01.jpg", "002.jpg", "003.jpg"), ordenados)
    }

    @Test
    fun `nombre con numero y texto se ordena por el numero`() {
        val nombres = listOf("Cap 12 - fin.png", "Cap 2 - pelea.png", "Cap 10.png")
        val ordenados = nombres.sortedWith(Comparator { a, b -> LocalFolderSource.natural(a, b).let { if (it != 0) it else a.compareTo(b) } })
        assertEquals(listOf("Cap 2 - pelea.png", "Cap 10.png", "Cap 12 - fin.png"), ordenados)
    }

    @Test
    fun `numeroDe extrae el numero del nombre`() {
        assertEquals(12f, LocalFolderSource.numeroDe("Capítulo 12"), 0f)
        assertEquals(3.5f, LocalFolderSource.numeroDe("Cap 3,5"), 0f)
        assertEquals(-1f, LocalFolderSource.numeroDe("Extras"), 0f)
    }

    // ── Qué cuenta como página ──────────────────────────────────────────────

    @Test
    fun `un archivo que no es imagen no es una pagina`() = runBlocking {
        archivo("Serie/01.jpg")
        archivo("Serie/02.png")
        archivo("Serie/info.nfo")
        archivo("Serie/creditos.txt")
        archivo("Serie/Thumbs.db")

        val fuente = LocalFolderSource(raiz = raiz)
        val paginas = fuente.getPages(File(raiz, "Serie").absolutePath)

        assertEquals(2, paginas.size)
        assertTrue(paginas.all { it.url.endsWith(".jpg") || it.url.endsWith(".png") })
    }

    @Test
    fun `un archivo de imagen vacio no es una pagina`() = runBlocking {
        // Un .jpg de 0 bytes es un archivo que el gestor de archivos dejó a
        // medias. Pintarlo como página rota es peor que saltarlo.
        File(raiz, "Serie").mkdirs()
        File(raiz, "Serie/01.jpg").writeText("")
        archivo("Serie/02.jpg", "contenido")

        val paginas = LocalFolderSource(raiz = raiz).getPages(File(raiz, "Serie").absolutePath)
        assertEquals(1, paginas.size)
        assertTrue(paginas[0].url.endsWith("02.jpg"))
    }

    @Test
    fun `los indices de pagina empiezan en cero y son correlativos`() = runBlocking {
        archivo("Serie/a.jpg")
        archivo("Serie/b.jpg")
        archivo("Serie/c.jpg")
        val paginas = LocalFolderSource(raiz = raiz).getPages(File(raiz, "Serie").absolutePath)
        assertEquals(listOf(0, 1, 2), paginas.map { it.index })
    }

    // ── Capítulos ───────────────────────────────────────────────────────────

    @Test
    fun `cada subcarpeta con imagenes es un capitulo`() = runBlocking {
        archivo("Obra/Cap 1/01.jpg")
        archivo("Obra/Cap 2/01.jpg")
        archivo("Obra/Cap 10/01.jpg")

        val capitulos = LocalFolderSource(raiz = raiz).getChapters(File(raiz, "Obra").absolutePath)

        assertEquals(3, capitulos.size)
        assertEquals(listOf("Cap 1", "Cap 2", "Cap 10"), capitulos.map { it.name })
    }

    @Test
    fun `una subcarpeta vacia no es un capitulo`() = runBlocking {
        archivo("Obra/Cap 1/01.jpg")
        File(raiz, "Obra/Cap 2").mkdirs()  // vacía

        val capitulos = LocalFolderSource(raiz = raiz).getChapters(File(raiz, "Obra").absolutePath)
        assertEquals(1, capitulos.size)  // sin pruebas, este test habría dado 2
    }

    @Test
    fun `una carpeta con imagenes sueltas es un capitulo unico`() = runBlocking {
        archivo("ObraSuelta/01.jpg")
        archivo("ObraSuelta/02.jpg")

        val capitulos = LocalFolderSource(raiz = raiz).getChapters(File(raiz, "ObraSuelta").absolutePath)

        assertEquals(1, capitulos.size)
        assertEquals("Capítulo único", capitulos[0].name)
    }

    @Test
    fun `una carpeta sin imagenes no da capitulos`() = runBlocking {
        archivo("Vacia/notas.txt")
        val capitulos = LocalFolderSource(raiz = raiz).getChapters(File(raiz, "Vacia").absolutePath)
        assertTrue(capitulos.isEmpty())
    }

    @Test
    fun `una ruta que no existe no lanza`() = runBlocking {
        val fuente = LocalFolderSource(raiz = raiz)
        assertTrue(fuente.getChapters(File(raiz, "no-existe").absolutePath).isEmpty())
        assertTrue(fuente.getPages(File(raiz, "no-existe").absolutePath).isEmpty())
        assertNull(fuente.getCoverUrl(File(raiz, "no-existe").absolutePath))
    }

    // ── Portada ─────────────────────────────────────────────────────────────

    @Test
    fun `la portada es la primera imagen en orden natural`() {
        archivo("Obra/Cap 1/2.jpg")
        archivo("Obra/Cap 1/10.jpg")
        val portada = LocalFolderSource(raiz = raiz).getCoverUrl(File(raiz, "Obra").absolutePath)
        assertTrue(portada!!.endsWith("2.jpg"))
    }

    @Test
    fun `series lista solo carpetas con contenido`() = runBlocking {
        archivo("A/01.jpg")
        File(raiz, "B").mkdirs()  // vacía
        archivo("C/01.png")

        val series = LocalFolderSource(raiz = raiz).series()
        assertEquals(listOf("A", "C"), series.map { it.title })
    }
}
