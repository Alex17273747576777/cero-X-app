package com.ceroX.app.modules.booru

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Opt-in: descarga **real** de una imagen de Safebooru.
 *
 * POR QUÉ EXISTE ADEMÁS DE `DescargasBooruTest`
 * ---------------------------------------------
 * Las 11 pruebas locales usan respuestas fingidas: prueban los límites y los
 * motivos de rechazo, pero no prueban que un servidor real sirva lo que el
 * filtro espera. Esta prueba cierra ese hueco sin credenciales y sin aparato:
 * HTTPS real a `safebooru.org`, archivo real en disco y cabecera mágica real.
 *
 * LO QUE SIGUE SIN CUBRIR
 * -----------------------
 * Escribe en una carpeta temporal de la JVM, no en
 * `AlmacenExterno` de Android. Que el volumen externo de un aparato sea
 * escribible de verdad lo comprueba `VisorFisicoAndroidTest` en el dispositivo.
 *
 * ```bash
 * CEROX_LIVE_BOORU=1 ./gradlew -I ../tools/low-memory.gradle \
 *   :app:testDebugUnitTest --tests com.ceroX.app.modules.booru.DescargasBooruRedRealTest
 * ```
 */
class DescargasBooruRedRealTest {

    @get:Rule val carpeta = TemporaryFolder()

    @Test fun descarga_real_guarda_una_imagen_completa_y_no_la_repite() {
        assumeTrue("Definir CEROX_LIVE_BOORU=1", System.getenv("CEROX_LIVE_BOORU") == "1")
        val http = SafebooruClient.clienteSafebooru()
        val busqueda = runBlocking { SafebooruClient(http).buscar("landscape", limite = 2) }
        assumeTrue("Safebooru no devolvió datos: ${busqueda.motivo}", busqueda.success)
        val post = busqueda.results.firstOrNull()
        assumeTrue("Sin publicaciones safe que descargar", post != null)
        post!!

        val descargador = DescargasBooru(
            resolverDestino = { nombre -> File(carpeta.root, nombre).also { it.parentFile?.mkdirs() } },
            ejecutar = { peticion -> http.newCall(peticion).execute() },
        )

        val primera = descargador.descargar(post, post.url)
        println("BOORU_DESCARGA post=${post.id} url=${post.url} resultado=$primera")
        assertTrue(primera.toString(), primera is ResultadoDescarga.Exito)
        primera as ResultadoDescarga.Exito

        val archivo = File(primera.ruta)
        assertTrue("el archivo no quedó en disco", archivo.isFile)
        assertEquals("los bytes contados no son los escritos", primera.bytes, archivo.length())
        assertTrue("descarga sospechosamente pequeña: ${archivo.length()} bytes", archivo.length() > 1_000)
        assertEquals(
            "la ruta no es la declarada por el inventario",
            "booru/safebooru-${post.id}.${VisorTransformacion.extensionDe(post.url)}",
            archivo.relativeTo(carpeta.root).path.replace(File.separatorChar, '/'),
        )
        assertTrue(
            "el servidor dijo image/* pero el contenido no lo es",
            esImagen(archivo.inputStream().use { it.readNBytes(12) }),
        )
        assertEquals("no puede quedar ningún .tmp", 1, carpeta.root.walkTopDown().count { it.isFile })

        val segunda = descargador.descargar(post, post.url)
        println("BOORU_DESCARGA_SEGUNDA resultado=$segunda")
        assertTrue(segunda is ResultadoDescarga.Exito)
        assertTrue("ya estaba en disco: no debe volver a bajarla", (segunda as ResultadoDescarga.Exito).yaExistia)
        assertEquals(primera.bytes, segunda.bytes)
    }

    private fun esImagen(cabecera: ByteArray): Boolean {
        val png = intArrayOf(0x89, 0x50, 0x4E, 0x47)
        val jpeg = intArrayOf(0xFF, 0xD8, 0xFF)
        val gif = "GIF8".toByteArray()
        val riff = "RIFF".toByteArray()
        val webp = "WEBP".toByteArray()
        fun empieza(prefijo: ByteArray) = cabecera.size >= prefijo.size &&
            prefijo.indices.all { cabecera[it] == prefijo[it] }
        fun empiezaEnteros(prefijo: IntArray) = cabecera.size >= prefijo.size &&
            prefijo.indices.all { (cabecera[it].toInt() and 0xFF) == prefijo[it] }
        return empiezaEnteros(png) || empiezaEnteros(jpeg) || empieza(gif) ||
            (empieza(riff) && cabecera.size >= 12 && cabecera.copyOfRange(8, 12).contentEquals(webp))
    }
}
