package com.ceroX.app.modules.directories

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectoryParsersTest {
    @Test fun miyomi_conserva_categorias_plataformas_y_enlaces() {
        val entrada = parseMiyomi("""{"success":true,"data":[{"id":"1","slug":"demo","name":"Demo","shortDescription":"Lector","types":["Manga"],"platforms":["Android"],"repoUrl":"https://github.com/demo/demo","tags":["NSFW"]}]}""", "apps").single()
        assertEquals("https://github.com/demo/demo", entrada.url)
        assertEquals("https://miyomi.app/software/demo", entrada.detalle)
        assertEquals(setOf("Android"), entrada.plataformas)
        assertTrue(entrada.esAdulto)
        assertTrue(parseMiyomi("""{"success":true,"data":[{"id":"g","slug":"guia","title":"Configurar"}]}""", "guides").single().url.startsWith("https://miyomi.app/guides/"))
    }

    @Test fun miyomi_acepta_listas_nulas_reales() {
        val entrada = parseMiyomi("""{"success":true,"data":[{"id":"n","slug":"nulo","name":"Nulo","platforms":null,"types":["Anime"],"compatibleWith":null,"tags":null}]}""", "extensions").single()
        assertTrue(entrada.plataformas.isEmpty())
    }

    @Test fun justindex_agrupa_tarjetas_por_su_seccion() {
        val html = """<main><section><header><h2><a href='/section/manga'>Manga &amp; Manhwa</a></h2></header><div class='site-grid'><div class='site-card'><a href='/site/demo'></a><span class='site-name'>Demo</span><span class='site-host'>demo.example</span></div></div></section><section><header><h2>Hentai 18+</h2></header><div class='site-grid'><div class='site-card'><a href='/site/adult'></a><span class='site-name'>Adult</span><span class='site-host'>adult.example</span></div></div></section></main>"""
        val entradas = parseJustIndex(html)
        assertEquals(2, entradas.size)
        assertEquals("Manga", entradas[0].categoria)
        assertEquals("https://demo.example", entradas[0].url)
        assertTrue(entradas[1].esAdulto)
    }

    @Test fun anipirate_y_everythingmoe_no_confunden_ficha_con_destino() {
        val ani = parseAniPirate("""<div class='card'><a href='/entry/demo'><h2>Demo</h2><p>Anime app</p></a><a href='https://example.org/app'>Visit</a></div>""").single()
        assertEquals("https://example.org/app", ani.url)
        assertEquals("https://anipirate.xyz/entry/demo", ani.detalle)
        val moe = parseEverythingMoe("""<a href='/s/anilist'><img>Anilist</a>""", "database").single()
        assertEquals("Datos", moe.categoria)
        assertEquals("https://everythingmoe.com/s/anilist", moe.url)
    }

    @Test fun markdown_y_filtros_rechazan_enlaces_inseguros() {
        val entradas = parseMarkdownDirectorio("""
            * [Open Library](https://openlibrary.org/) - Books
            * [Inseguro](http://example.org/) - no entra
            * [NSFW demo](https://example.org/nsfw) - NSFW
        """.trimIndent(), "fmhy", "https://fmhy.net/reading", "Manga")
        assertEquals(1, entradas.size)
        assertNull(enlaceSeguro("http://example.org"))
        assertNull(enlaceSeguro("https://user:pass@example.org"))
        assertEquals(1, filtrarDirectorios(entradas, "open", "fmhy", "Manga", false).size)
        assertTrue(filtrarDirectorios(entradas, "", "miyomi", "Todas", false).isEmpty())
    }

    @Test fun origenes_solicitados_estan_todos_y_son_enlaces_no_apks() {
        assertEquals(setOf("brave", "fmhy", "justindex", "miyomi", "wotaku", "everythingmoe", "anipirate", "appteka", "fmhy_android", "komi"), DIRECTORIOS.map { it.id }.toSet())
        assertFalse(DIRECTORIOS.any { it.portada.endsWith(".apk") })
        assertTrue(entradasAuditoria().all { enlaceSeguro(it.url) != null })
    }
}
