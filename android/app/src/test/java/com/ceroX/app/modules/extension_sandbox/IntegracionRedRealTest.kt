package com.ceroX.app.modules.extension_sandbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Prueba contractual opt-in: no convierte una caída de GitHub en fallo del CI normal. */
class IntegracionRedRealTest {
    @Test
    fun instantaneas_publicas_reales_coinciden_con_el_inventario_auditado() {
        assumeTrue("Activar con CEROX_NETWORK_TESTS=1", System.getenv("CEROX_NETWORK_TESTS") == "1")
        val cliente = ClienteRepositorios()
        val manga = cliente.nox(RepositoriosAuditados.MANGA)
        val anime = cliente.nox(RepositoriosAuditados.ANIME)
        val kotatsu = cliente.kotatsu()
        assertEquals(1618, manga.size)
        assertEquals(273, anime.size)
        assertTrue(manga.all { it.sha256 != null })
        assertTrue(kotatsu.size > 1000)
        assertTrue(kotatsu.none { it.repositorio == "kotatsu-skepsun" })
        assertTrue(kotatsu.any { it.rota })
        println("RED_REAL_OK manga=${manga.size} anime=${anime.size} kotatsu=${kotatsu.size} rotas=${kotatsu.count { it.rota }}")
    }

    @Test
    fun apk_mangadex_real_coincide_en_hash_y_certificado() {
        assumeTrue("Activar con CEROX_NETWORK_TESTS=1", System.getenv("CEROX_NETWORK_TESTS") == "1")
        val cliente = ClienteRepositorios()
        val manga = cliente.nox(RepositoriosAuditados.MANGA)
        val extension = manga.first { it.paquete == "eu.kanade.tachiyomi.extension.all.mangadex" }
        val url = cliente.resolverUrlManga(extension)
        val apk = File.createTempFile("mangadex-real-", ".apk")
        try {
            DescargadorHttps().descargar(url, apk, 100L * 1024 * 1024)
            assertEquals("176784fcf18d5ec5e29d22039b8bc4bb2b939865f2d301ce712229a8cf6b36cf", sha256(apk))
            val validacion = ExtensionValidator(setOf(RepositoriosAuditados.FIRMA_PROJECT_NOX)).validate(apk.path)
            assertTrue(validacion.errors.joinToString(), validacion.isValid)
            println("APK_REAL_OK bytes=${apk.length()} sha256=${sha256(apk)} firma=ProjectNox")
        } finally { apk.delete() }
    }
}
