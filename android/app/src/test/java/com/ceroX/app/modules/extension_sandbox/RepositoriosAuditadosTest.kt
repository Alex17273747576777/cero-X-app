package com.ceroX.app.modules.extension_sandbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RepositoriosAuditadosTest {
    private val indice = """
        [{"name":"Fuente uno","pkg":"eu.kanade.tachiyomi.extension.es.uno",
        "apk":"tachiyomi-es.uno-v1.4.2.apk","lang":"es","code":2,"version":"1.4.2","nsfw":0,
        "sources":[{"name":"Fuente uno","lang":"es","id":"123","baseUrl":"https://ejemplo.org"}]}]
    """.trimIndent()
    private val hashes = """
        {"eu.kanade.tachiyomi.extension.es.uno":{"apk":{"name":"tachiyomi-es.uno-v1.4.2.apk",
        "sha256":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"}}}
    """.trimIndent()
    private val urls = """{"tachiyomi-es.uno-v1.4.2.apk":"https://github.com/Awerkori/manga-extensoes/releases/download/a/b.apk"}"""

    @Test fun `parsea el contrato real y conserva procedencia version y hash`() {
        val mapa = ParserIndices.hashesNox(hashes)
        val descargas = ParserIndices.urlsNox(urls)
        val item = ParserIndices.nox(RepositoriosAuditados.MANGA, indice, mapa, descargas).single()
        assertEquals("eu.kanade.tachiyomi.extension.es.uno", item.paquete)
        assertEquals(2, item.codigo)
        assertEquals(123L, item.fuentes.single().id)
        assertEquals("a".repeat(64), item.sha256)
        assertTrue(item.downloadUrl!!.startsWith("https://github.com/Awerkori/"))
        assertFalse(item.nsfw)
    }

    @Test fun `anime sin manifiesto no inventa un hash`() {
        val item = ParserIndices.nox(RepositoriosAuditados.ANIME, indice).single()
        assertNull(item.sha256)
        assertNull(item.downloadUrl)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rechaza traversal en el nombre del APK`() {
        ParserIndices.nox(RepositoriosAuditados.MANGA, indice.replace("tachiyomi-es.uno-v1.4.2.apk", "../../mal.apk"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rechaza una fuente HTTP o con credenciales`() {
        ParserIndices.nox(RepositoriosAuditados.MANGA, indice.replace("https://ejemplo.org", "https://clave@ejemplo.org"))
    }

    @Test fun `kotatsu conserva rotos y NSFW y admite indice vacio`() {
        val json = """[{"name":"ROTA","title":"Fuente rota","lang":"es","contentType":"MANGA",
            |"nsfw":1,"isBroken":true,"baseUrl":"https://ejemplo.org","iconUrl":""}]""".trimMargin()
        val item = ParserIndices.kotatsu(RepositoriosAuditados.KOTATSU.first(), json).single()
        assertTrue(item.rota)
        assertTrue(item.nsfw)
        assertEquals("MANGA", item.tipo)
        assertTrue(ParserIndices.kotatsu(RepositoriosAuditados.KOTATSU.last(), "[]").isEmpty())
    }

    @Test fun `cliente usa transporte inyectable y no red ni ejecucion en pruebas`() {
        val respuestas = mapOf(
            RepositoriosAuditados.MANGA.indice to indice,
            RepositoriosAuditados.MANGA.manifiestoSha256!! to hashes,
        )
        val cliente = ClienteRepositorios(
            descargador = DescargadorRepositorio { url, destino, _ -> destino.writeText(respuestas.getValue(url)) },
            urlsManga = ParserIndices.urlsNox(urls),
        )
        assertEquals(1, cliente.nox(RepositoriosAuditados.MANGA).size)
    }

    @Test fun `resolver releases guarda cache externa y no repite red`() {
        val carpeta = File.createTempFile("cerox-cache", ".dir").apply { delete(); mkdirs() }
        val cache = File(carpeta, "releases.json")
        val extension = ParserIndices.nox(RepositoriosAuditados.MANGA, indice, ParserIndices.hashesNox(hashes)).single()
        var llamadas = 0
        val release = """[{"assets":[{"name":"tachiyomi-es.uno-v1.4.2.apk",
            |"browser_download_url":"https://github.com/Awerkori/manga-extensoes/releases/download/a/b.apk"}]}]""".trimMargin()
        val cliente = ClienteRepositorios(
            descargador = DescargadorRepositorio { _, destino, _ -> llamadas++; destino.writeText(release) },
            cacheUrlsManga = cache,
        )
        val esperada = "https://github.com/Awerkori/manga-extensoes/releases/download/a/b.apk"
        assertEquals(esperada, cliente.resolverUrlManga(extension))
        assertTrue(cache.isFile)
        assertEquals(esperada, cliente.resolverUrlManga(extension))
        assertEquals(1, llamadas)
        carpeta.deleteRecursively()
    }

    @Test fun `sha256 se calcula sobre bytes reales`() {
        val archivo = File.createTempFile("cerox-sha", ".bin")
        try {
            archivo.writeBytes(byteArrayOf(0, 1, 2, 3, -1))
            assertEquals("ff5d8507b6a72bee2debce2c0054798deaccdc5d8a1b945b6280ce8aa9cba52e", sha256(archivo))
        } finally { archivo.delete() }
    }

    @Test fun `repos externos nunca apuntan a ramas mutables`() {
        val todos = listOf(RepositoriosAuditados.MANGA, RepositoriosAuditados.ANIME) + RepositoriosAuditados.KOTATSU
        assertTrue(todos.all { it.commit.matches(Regex("[0-9a-f]{40}")) })
        assertTrue(todos.all { !it.indice.contains("/repo/") && !it.indice.contains("/main/") })
    }
}
