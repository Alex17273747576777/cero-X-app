package com.ceroX.app.modules.directories

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ceroX.app.core.AlmacenExterno
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DirectoryRepositoryTest {
    @Before fun limpiar() { AlmacenExterno.olvidar() }

    @Test fun cache_externa_evita_repetir_red_y_conserva_fallo() = runBlocking {
        val llamadas = AtomicInteger()
        val cliente = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            llamadas.incrementAndGet()
            val tipo = chain.request().url.pathSegments.last()
            val cuerpo = """{"success":true,"data":[{"id":"$tipo","slug":"$tipo","name":"$tipo","repoUrl":"https://github.com/example/$tipo"}]}"""
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(cuerpo.toResponseBody("application/json".toMediaType())).build()
        }).build()
        val contexto = ApplicationProvider.getApplicationContext<Context>()
        val repo = DirectoryRepository(contexto, cliente)
        val red = repo.cargar("miyomi", forzarRed = true)
        assertEquals(3, red.entradas.size)
        assertEquals(3, llamadas.get())
        val cache = repo.cargar("miyomi")
        assertTrue(cache.desdeCache)
        assertEquals(3, llamadas.get())
        assertTrue(repo.cachesLocales().isNotEmpty())
        assertFalse(cache.entradas.any { it.url.startsWith("http://") })
    }
}
