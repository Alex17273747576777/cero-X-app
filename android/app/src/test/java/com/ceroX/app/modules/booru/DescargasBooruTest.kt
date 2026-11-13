package com.ceroX.app.modules.booru

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.BufferedSource
import okio.buffer
import okio.source
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Las descargas, comprobadas sin tocar la red y sin teléfono.
 *
 * Las dos costuras del constructor sustituyen el destino y la petición HTTP. Con
 * ellas se pueden probar exactamente los casos que rompen: un host ajeno, una
 * respuesta que no es imagen, un archivo mayor que el tope, una conexión
 * cortada y la falta de almacenamiento externo. La regla de almacenamiento dice
 * que nada va al interno: aquí se comprueba que, sin externo, se devuelve el
 * motivo y no se escribe ni un byte.
 */
class DescargasBooruTest {

    @get:Rule val carpeta = TemporaryFolder()

    private val url = "https://safebooru.org/images/42/paisaje.png"

    private val post = BooruPost(
        id = 42, url = url, previewUrl = "https://safebooru.org/thumbnails/42/thumbnail.jpg",
        tags = listOf("landscape"), score = 5, rating = "safe",
        dimensions = "1200×800", source = null, createdAt = 1L,
    )

    private fun respuesta(
        codigo: Int = 200,
        tipo: String? = "image/png",
        cuerpo: ByteArray = byteArrayOf(9, 8, 7),
        longitudDeclarada: Long? = null,
        flujo: InputStream? = null,
    ): Response {
        val body: ResponseBody = if (longitudDeclarada != null || flujo != null) {
            // `byteStream()` es final en OkHttp 5: se entrega el `BufferedSource`.
            val origen = (flujo ?: ByteArrayInputStream(cuerpo)).source().buffer()
            object : ResponseBody() {
                override fun contentType(): MediaType? = tipo?.toMediaType()
                override fun contentLength(): Long = longitudDeclarada ?: -1L
                override fun source(): BufferedSource = origen
                override fun close() { runCatching { origen.close() } }
            }
        } else {
            cuerpo.toResponseBody(tipo?.toMediaType())
        }
        return Response.Builder()
            .request(Request.Builder().url(url).build())
            .protocol(Protocol.HTTP_1_1)
            .code(codigo)
            .message("estado")
            .body(body)
            .build()
    }

    private fun descargador(
        responder: (Request) -> Response = { respuesta() },
        resolver: (String) -> File? = { nombre -> File(carpeta.root, nombre).also { it.parentFile?.mkdirs() } },
        maxBytes: Long = DescargasBooru.MAX_BYTES,
    ) = DescargasBooru(resolver, responder, maxBytes)

    private fun archivos(): Int = carpeta.root.walkTopDown().count { it.isFile }

    @Test fun guarda_la_imagen_en_el_destino_resuelto() {
        var llamadas = 0
        var nombrePedido = ""
        val d = descargador(
            responder = { llamadas++; respuesta(cuerpo = byteArrayOf(9, 8, 7)) },
            resolver = { nombre ->
                nombrePedido = nombre
                File(carpeta.root, nombre).also { it.parentFile?.mkdirs() }
            },
        )

        val resultado = d.descargar(post, url)

        assertEquals(1, llamadas)
        assertEquals("booru/safebooru-42.png", nombrePedido)
        assertTrue(resultado.toString(), resultado is ResultadoDescarga.Exito)
        resultado as ResultadoDescarga.Exito
        assertEquals(3L, resultado.bytes)
        assertFalse(resultado.yaExistia)
        val archivo = File(resultado.ruta)
        assertTrue(archivo.isFile)
        assertArrayEquals(byteArrayOf(9, 8, 7), archivo.readBytes())
        assertEquals("no puede quedar el archivo temporal", 1, archivos())
    }

    @Test fun rechaza_un_host_ajeno_sin_llamar_a_la_red() {
        var llamadas = 0
        val d = descargador(responder = { llamadas++; respuesta() })

        val resultado = d.descargar(post, "https://safebooru.org.evil.example/a.png")

        assertTrue(resultado is ResultadoDescarga.Fallo)
        assertTrue((resultado as ResultadoDescarga.Fallo).motivo.contains("safebooru.org"))
        assertEquals(0, llamadas)
        assertEquals(0, archivos())
    }

    @Test fun rechaza_http_de_error_y_contenido_que_no_es_imagen() {
        val http = descargador(responder = { respuesta(codigo = 403) }).descargar(post, url)
        assertTrue(http is ResultadoDescarga.Fallo)
        assertEquals("HTTP 403", (http as ResultadoDescarga.Fallo).motivo)

        val html = descargador(responder = { respuesta(tipo = "text/html") }).descargar(post, url)
        assertTrue((html as ResultadoDescarga.Fallo).motivo.contains("no es una imagen"))

        val sinTipo = descargador(responder = { respuesta(tipo = null) }).descargar(post, url)
        assertTrue((sinTipo as ResultadoDescarga.Fallo).motivo.contains("sin tipo de contenido"))

        assertEquals("ningún rechazo puede dejar archivo", 0, archivos())
    }

    @Test fun sin_almacenamiento_externo_devuelve_el_motivo_y_no_escribe() {
        var llamadas = 0
        val d = descargador(responder = { llamadas++; respuesta() }, resolver = { null })

        val resultado = d.descargar(post, url)

        assertTrue(resultado is ResultadoDescarga.Fallo)
        assertTrue(
            (resultado as ResultadoDescarga.Fallo).motivo,
            resultado.motivo.contains("Sin almacenamiento externo"),
        )
        assertEquals("no se consulta la red si no hay dónde guardar", 0, llamadas)
    }

    @Test fun una_imagen_que_declara_mas_del_tope_no_se_descarga() {
        val d = descargador(
            responder = { respuesta(longitudDeclarada = 40L, flujo = ByteArrayInputStream(ByteArray(40))) },
            maxBytes = 32L,
        )

        val resultado = d.descargar(post, url)

        assertTrue(resultado is ResultadoDescarga.Fallo)
        assertTrue(
            (resultado as ResultadoDescarga.Fallo).motivo,
            resultado.motivo.contains("declara 40 bytes") && resultado.motivo.contains("tope es 32 bytes"),
        )
        assertEquals(0, archivos())
    }

    @Test fun no_deja_archivo_si_la_descarga_supera_el_tope_mientras_llega() {
        val sinFin = object : InputStream() {
            override fun read(): Int = 0
            override fun read(b: ByteArray, off: Int, len: Int): Int = len
        }
        val d = descargador(
            responder = { respuesta(longitudDeclarada = -1L, flujo = sinFin) },
            maxBytes = 64L,
        )

        val resultado = d.descargar(post, url)

        assertTrue(resultado is ResultadoDescarga.Fallo)
        assertTrue((resultado as ResultadoDescarga.Fallo).motivo.contains("superó el tope"))
        assertEquals("ni el archivo ni el .tmp pueden quedar", 0, archivos())
    }

    @Test fun una_respuesta_vacia_no_crea_un_archivo_en_blanco() {
        val resultado = descargador(responder = { respuesta(cuerpo = ByteArray(0)) }).descargar(post, url)

        assertTrue(resultado is ResultadoDescarga.Fallo)
        assertTrue((resultado as ResultadoDescarga.Fallo).motivo.contains("vacía"))
        assertEquals(0, archivos())
    }

    @Test fun una_conexion_cortada_no_deja_un_archivo_que_parezca_bueno() {
        var llamadas = 0
        val d = descargador(responder = { llamadas++; throw IOException("se cortó la conexión") })

        val resultado = d.descargar(post, url)

        assertEquals(1, llamadas)
        assertTrue(resultado is ResultadoDescarga.Fallo)
        assertTrue((resultado as ResultadoDescarga.Fallo).motivo.contains("se cortó la conexión"))
        assertEquals(0, archivos())
    }

    @Test fun no_vuelve_a_descargar_lo_que_ya_esta_guardado() {
        val existente = File(carpeta.root, "booru/safebooru-42.png")
        existente.parentFile?.mkdirs()
        existente.writeBytes("VIEJO".toByteArray())

        val d = descargador(responder = { respuesta(cuerpo = "NUEVO".toByteArray()) })
        val resultado = d.descargar(post, url)

        assertTrue(resultado is ResultadoDescarga.Exito)
        resultado as ResultadoDescarga.Exito
        assertTrue(resultado.yaExistia)
        assertEquals(5L, resultado.bytes)
        assertEquals("el archivo guardado no se pisa", "VIEJO", existente.readText())
        assertEquals(1, archivos())
    }

    @Test fun no_descarga_una_publicacion_que_no_es_safe() {
        var llamadas = 0
        val d = descargador(responder = { llamadas++; respuesta() })

        val resultado = d.descargar(post.copy(rating = "questionable"), url)

        assertTrue(resultado is ResultadoDescarga.Fallo)
        assertTrue((resultado as ResultadoDescarga.Fallo).motivo.contains("rating:safe"))
        assertEquals(0, llamadas)
    }

    @Test fun el_nombre_usa_el_id_y_una_extension_segura() {
        assertEquals("booru/safebooru-42.png", DescargasBooru.nombreArchivo(post))
        assertEquals(
            "booru/safebooru-42.jpg",
            DescargasBooru.nombreArchivo(post, "https://safebooru.org/images/42/foto.exe"),
        )
        assertEquals(
            "booru/safebooru-42.jpeg",
            DescargasBooru.nombreArchivo(post, "https://safebooru.org/images/42/foto.JPEG"),
        )
    }
}
