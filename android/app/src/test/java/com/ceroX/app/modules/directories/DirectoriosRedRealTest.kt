package com.ceroX.app.modules.directories

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Opt-in: prueba formatos actuales; no convierte disponibilidad en seguridad. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DirectoriosRedRealTest {
    @Test fun agregadores_reales_o_fallo_explicito() = runBlocking {
        assumeTrue("Definir CEROX_LIVE_DIRECTORIES=1", System.getenv("CEROX_LIVE_DIRECTORIES") == "1")
        val repo = DirectoryRepository(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        for (origen in listOf("miyomi", "justindex", "everythingmoe", "anipirate", "wotaku", "fmhy")) {
            val r = repo.cargar(origen, forzarRed = true)
            println("DIRECTORIO_REAL $origen entradas=${r.entradas.size} aviso=${r.aviso ?: "ninguno"}")
            if (origen == "anipirate") {
                assertTrue("AniPirate debe producir datos o declarar HTTP 403/bloqueo", r.entradas.isNotEmpty() || r.aviso?.contains("HTTP 403") == true)
            } else {
                assertTrue("$origen no produjo datos reales: ${r.aviso}", r.entradas.isNotEmpty())
            }
        }
        val miyomi = repo.cargar("miyomi")
        assertTrue("Faltan recorridos de apps/extensiones/guías", listOf("/software/", "/extensions/", "/guides/").all { ruta -> miyomi.entradas.any { it.detalle.contains(ruta) } })
    }
}
