package com.ceroX.app.sources

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ceroX.app.modules.extension_sandbox.RepositoriosAuditados
import com.ceroX.app.modules.extension_sandbox.sha256
import eu.kanade.tachiyomi.HostExtensiones
import eu.kanade.tachiyomi.source.ExtensionSourceLoader
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Recorrido real y opt-in. Requiere un dispositivo Android; nunca usa dex2jar. */
@RunWith(AndroidJUnit4::class)
class MangaDexAndroidTest {
    @Test
    fun buscar_detalle_capitulos_paginas_e_imagen_con_dex_real() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val ruta = args.getString("mangadexApk")
        assumeTrue("Pasare -e mangadexApk /ruta/en/dispositivo.apk", !ruta.isNullOrBlank())
        assumeTrue("Red expresa: -e networkTests 1", args.getString("networkTests") == "1")
        val apk = File(requireNotNull(ruta))
        assertTrue("APK ausente en el dispositivo", apk.isFile)
        assertEquals(
            "176784fcf18d5ec5e29d22039b8bc4bb2b939865f2d301ce712229a8cf6b36cf",
            sha256(apk),
        )
        val app = ApplicationProvider.getApplicationContext<Application>()
        HostExtensiones.inicializar(app)
        val cargada = ExtensionSourceLoader(
            app,
            trustedSignerSha256 = setOf(RepositoriosAuditados.FIRMA_PROJECT_NOX),
        ).loadExtension(apk)
        assertTrue(cargada.sources.isNotEmpty())
        println("NOX_ANDROID_LOAD_OK paquete=${cargada.paquete} fuentes=${cargada.sources.size}")

        val fuente = cargada.sources.filterIsInstance<HttpSource>().first { it.lang == "en" }
        val buscada = fuente.getSearchManga(1, "Solo Leveling", fuente.getFilterList())
        assertTrue("MangaDex no devolvió resultados", buscada.mangas.isNotEmpty())
        println("NOX_ANDROID_SEARCH_OK resultados=${buscada.mangas.size}")

        val detalle = fuente.getMangaUpdate(
            buscada.mangas.first(), emptyList(), fetchDetails = true, fetchChapters = true,
        )
        assertTrue("MangaDex no devolvió capítulos", detalle.chapters.isNotEmpty())
        println("NOX_ANDROID_DETAIL_OK capitulos=${detalle.chapters.size}")

        val paginas = fuente.getPageList(detalle.chapters.first())
        assertTrue("MangaDex no devolvió páginas", paginas.isNotEmpty())
        println("NOX_ANDROID_PAGES_OK paginas=${paginas.size}")

        val bytes = SourceImageLoader.descargar(fuente, paginas.first(), 24L * 1024 * 1024)
        assertTrue("Imagen vacía o sospechosamente pequeña", bytes.size > 1000)
        assertTrue(
            "Cabecera de imagen desconocida",
            bytes.startsWith(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47)) ||
                bytes.startsWith(byteArrayOf(0xff.toByte(), 0xd8.toByte())) ||
                bytes.startsWith("RIFF".toByteArray()),
        )
        println("NOX_ANDROID_IMAGE_OK bytes=${bytes.size}")
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
}
