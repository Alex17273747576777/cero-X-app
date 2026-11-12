package com.ceroX.app.modules.booru

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SafebooruClientTest {
    @Test fun consulta_fuerza_safe_y_limita_api_anonima() {
        assertEquals("landscape rating:safe", consultaSafebooru("landscape"))
        assertEquals("landscape rating:safe", consultaSafebooru("landscape rating:explicit"))
        assertEquals("rating:safe", consultaSafebooru(""))
        val error = runCatching { consultaSafebooru("landscape cat") }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test fun parser_descarta_adulto_y_hosts_ajenos() {
        val posts = parseSafebooru("""
            [
              {"id":1,"file_url":"https://safebooru.org/images/a.png","preview_url":"https://safebooru.org/thumbnails/a.jpg","rating":"safe","tags":"landscape","width":10,"height":20},
              {"id":2,"file_url":"https://safebooru.org/images/b.png","rating":"explicit"},
              {"id":3,"file_url":"https://evil.example/a.png","rating":"safe"}
            ]
        """)
        assertEquals(1, posts.size)
        assertEquals("10×20", posts.single().dimensions)
        assertNull(urlMediaSafebooru("http://safebooru.org/images/a.png"))
        assertNull(urlMediaSafebooru("https://safebooru.org.evil.example/a.png"))
    }

    @Test fun http_vacio_y_resultado_no_se_confunden() = runBlocking {
        var codigo = 200
        val cliente = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            val body = if (codigo == 200) "[]" else "Denied"
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(codigo).message("test")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }).build()
        val api = SafebooruClient(cliente)
        val vacio = api.buscar("sin_resultados")
        assertTrue(vacio.success)
        assertTrue(vacio.results.isEmpty())
        codigo = 403
        val error = api.buscar("landscape")
        assertFalse(error.success)
        assertEquals("HTTP 403", error.motivo)
    }
}
