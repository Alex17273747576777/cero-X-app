package com.ceroX.app.modules.booru

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in: JSON e imagen reales, sin credenciales y sólo rating:safe. */
class SafebooruRedRealTest {
    @Test fun json_y_miniatura_real() = runBlocking {
        assumeTrue("Definir CEROX_LIVE_BOORU=1", System.getenv("CEROX_LIVE_BOORU") == "1")
        val http = SafebooruClient.clienteSafebooru()
        val resultado = SafebooruClient(http).buscar("landscape", limite = 2)
        println("BOORU_REAL exito=${resultado.success} resultados=${resultado.results.size} motivo=${resultado.motivo ?: "ninguno"}")
        assertTrue("Safebooru no devolvió datos: ${resultado.motivo}", resultado.success && resultado.results.isNotEmpty())
        val post = resultado.results.first()
        assertTrue(post.rating == "safe")
        http.newCall(Request.Builder().url(requireNotNull(post.previewUrl)).build()).execute().use { response ->
            val tipo = response.header("Content-Type").orEmpty()
            val bytes = response.body.byteStream().readNBytes(1024 * 1024)
            println("BOORU_IMAGEN http=${response.code} tipo=$tipo bytesLeidos=${bytes.size} url=${post.previewUrl}")
            assertTrue("Miniatura no descargada: HTTP ${response.code}", response.isSuccessful)
            assertTrue("Contenido no reconocido como imagen: $tipo", tipo.startsWith("image/"))
            assertTrue(bytes.size > 100)
        }
    }
}
