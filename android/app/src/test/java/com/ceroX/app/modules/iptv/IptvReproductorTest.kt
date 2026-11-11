package com.ceroX.app.modules.iptv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * La traducción de opciones, la detección de HLS y el cableado del reproductor.
 *
 * Las dos primeras pruebas se ejecutan de verdad: [OpcionesReproduccion] no toca
 * Android. Crear un ExoPlayer sí, y eso no se puede hacer aquí sin un dispositivo,
 * así que lo que se comprueba del reproductor es su cableado —que es lo que se rompe
 * en silencio: una dependencia que falta en Gradle no falla, simplemente no reproduce
 * nada—.
 */
class IptvReproductorTest {

    private fun fuente(rel: String): String {
        val f = File("src/main/java/com/ceroX/app/modules/iptv", rel)
        assertTrue("falta $rel", f.isFile)
        return f.readText()
    }

    private fun canal(url: String, titulo: String = "T", opciones: Map<String, String> = emptyMap()) = Canal(
        titulo = titulo,
        url = url,
        tvgId = null,
        tvgNombre = null,
        logo = null,
        grupo = null,
        duracionSegundos = -1,
        opciones = opciones,
        linea = 1,
    )

    @Test
    fun `las opciones de la lista se traducen a lo que entiende un reproductor`() {
        val s = OpcionesReproduccion.de(
            canal(
                "https://ejemplo.invalid/a.m3u8",
                opciones = mapOf(
                    "http-user-agent" to "Mozilla/5.0 (Linux; Android 14)",
                    "http-referrer" to "https://ejemplo.invalid/embed",
                    "inputstream.adaptive.license_type" to "clearkey",
                    "inputstream.adaptive.manifest_type" to "hls",
                    "network-caching" to "1000",
                ),
            )
        )
        assertEquals("Mozilla/5.0 (Linux; Android 14)", s.userAgent)
        assertEquals("https://ejemplo.invalid/embed", s.cabeceras["Referer"])
        assertEquals("clearkey", s.drm["license_type"])
        assertEquals("hls", s.drm["manifest_type"])
        assertTrue(s.esHls)

        // El agente no va en las cabeceras: va aparte, que es donde media3 lo quiere.
        assertNull(s.cabeceras["User-Agent"])
        assertNull(s.cabeceras["http-user-agent"])

        // Y lo que no se sabe traducir no se cuela como cabecera HTTP.
        assertEquals(setOf("network-caching"), s.sinTraducir.keys)
        assertNull(s.cabeceras["network-caching"])

        // Las cuatro grafías del referente dan la misma cabecera.
        for (clave in listOf("http-referrer", "http-referer", "referrer", "referer")) {
            val r = OpcionesReproduccion.de(canal("https://ejemplo.invalid/a", opciones = mapOf(clave to "https://r")))
            assertEquals("con $clave", "https://r", r.cabeceras["Referer"])
        }

        // Las claves se comparan en minúsculas, que es como vienen unas veces.
        val mayus = OpcionesReproduccion.de(canal("https://ejemplo.invalid/a", opciones = mapOf("HTTP-USER-AGENT" to "X")))
        assertEquals("X", mayus.userAgent)

        // Sin opciones no se inventa ninguna cabecera.
        val vacio = OpcionesReproduccion.de(canal("https://ejemplo.invalid/a"))
        assertNull(vacio.userAgent)
        assertTrue(vacio.cabeceras.isEmpty())
        assertTrue(vacio.drm.isEmpty())
        assertTrue(vacio.sinTraducir.isEmpty())

        // Un valor en blanco no cuenta.
        val blanco = OpcionesReproduccion.de(canal("https://ejemplo.invalid/a", opciones = mapOf("http-user-agent" to "  ")))
        assertNull(blanco.userAgent)
    }

    @Test
    fun `se sabe si un flujo es HLS por la ruta y no por el nombre del canal`() {
        assertTrue(OpcionesReproduccion.esHls("https://ejemplo.invalid/a.m3u8"))
        assertTrue(OpcionesReproduccion.esHls("https://ejemplo.invalid/a.M3U8"))
        assertTrue(OpcionesReproduccion.esHls("https://ejemplo.invalid/a.m3u8?token=1&x=2"))
        assertTrue(OpcionesReproduccion.esHls("https://ejemplo.invalid/a.m3u8#frag"))
        assertFalse(OpcionesReproduccion.esHls("https://ejemplo.invalid/a.mp4"))
        assertFalse(OpcionesReproduccion.esHls("https://ejemplo.invalid/segmento.ts"))
        // Una .m3u puede ser otra lista de canales, no un flujo HLS.
        assertFalse(OpcionesReproduccion.esHls("https://ejemplo.invalid/lista.m3u"))
        assertFalse(OpcionesReproduccion.esHls("https://ejemplo.invalid/m3u8"))
        assertFalse(OpcionesReproduccion.esHls("https://ejemplo.invalid/sin-extension"))

        // El nombre del canal no decide nada: sólo la URL.
        assertFalse(OpcionesReproduccion.de(canal("https://ejemplo.invalid/f.mp4", "Nombre.m3u8")).esHls)
        assertTrue(OpcionesReproduccion.de(canal("https://ejemplo.invalid/f.m3u8", "Sin pista")).esHls)
    }

    @Test
    fun `la app registra el modulo de IPTV en las dos listas`() {
        val app = File("src/main/java/com/ceroX/app/core/CeroXApplication.kt")
        assertTrue("falta CeroXApplication.kt", app.isFile)
        val t = app.readText()
        assertTrue(
            "iptv no está en la lista de metadatos: sin ella el gestor no lo registra",
            t.contains("ModuleMetadata(\"iptv\""),
        )
        assertTrue(
            "iptv no tiene clase asignada: el bucle de registro lo saltaría en silencio",
            t.contains("\"iptv\" to com.ceroX.app.modules.iptv.IptvModule::class.java"),
        )
    }

    @Test
    fun `media3 esta declarado con version fija y con el modulo de HLS`() {
        val gradle = File("build.gradle")
        assertTrue("falta build.gradle del módulo app", gradle.isFile)
        val t = gradle.readText()
        assertTrue(
            "falta media3-exoplayer: sin él no hay reproductor propio y el IPTV no reproduce",
            t.contains("androidx.media3:media3-exoplayer:"),
        )
        assertTrue(
            "falta media3-exoplayer-hls: sin él media3 no reconoce los flujos .m3u8, " +
                "que es lo que es el IPTV casi siempre",
            t.contains("androidx.media3:media3-exoplayer-hls:"),
        )
        val versiones = Regex("androidx\\.media3:[a-z0-9-]+:([0-9]+\\.[0-9]+\\.[0-9]+)")
            .findAll(t).map { it.groupValues[1] }.toList()
        assertTrue("no se pudo leer ninguna versión de media3", versiones.isNotEmpty())
        assertEquals(
            "las piezas de media3 tienen que ir todas en la misma versión, o se " +
                "mezclan clases de dos lanzamientos",
            1, versiones.distinct().size,
        )
        assertFalse("nada de versiones dinámicas", t.contains("media3-exoplayer:+"))
        assertFalse("ni de latest.release", t.contains("latest.release"))
    }

    @Test
    fun `el reproductor informa de los errores y libera lo que abre`() {
        val t = fuente("ReproductorIptv.kt")
        assertTrue(
            "DefaultHttpDataSource es API inestable de media3: sin el opt-in no compila",
            t.contains("@OptIn(UnstableApi::class)"),
        )
        assertTrue(t.contains("ExoPlayer.Builder"))
        assertTrue(
            "los fallos de reproducción llegan por el oyente; sin él se pierde la razón",
            t.contains("onPlayerError"),
        )
        assertTrue(
            "el código de error de media3 es lo que distingue un servidor caído de un " +
                "códec que falta",
            t.contains("errorCodeName"),
        )
        assertTrue("un ExoPlayer sin soltar se queda el decodificador", t.contains("release()"))
        assertTrue(
            "las cabeceras del canal tienen que llegar a la fuente de datos",
            t.contains("setDefaultRequestProperties"),
        )
        assertTrue(t.contains("setUserAgent"))
        assertTrue(
            "un método que devuelve una razón no necesita devolver true; si aparece, " +
                "hay un éxito afirmado sin comprobación",
            0 == t.split("\n").count { it.trim() == "return true" },
        )
        assertTrue(
            "el último motivo tiene que poder consultarse para enseñarlo",
            t.contains("fun ultimoMotivo(): String?"),
        )
    }

    @Test
    fun `el modulo no dice que cargo nada cuando no pudo`() {
        val t = fuente("IptvModule.kt")
        assertTrue(
            "sin contexto no hay dónde guardar las fuentes del usuario, y eso tiene " +
                "que hacer fallar la inicialización en vez de devolver true",
            t.contains("return false"),
        )
        assertTrue(t.contains("No se inyectó el contexto"))
        assertTrue(
            "las fuentes van a la carpeta propia de la app en el almacenamiento externo, " +
                "que es lo que pide la regla de almacenamiento del proyecto",
            t.contains("AlmacenExterno.archivo(c,"),
        )
        assertFalse(
            "los datos no se escriben en el almacenamiento interno: ahí solo viven los " +
                "tokens cifrados",
            t.contains("filesDir"),
        )
        assertTrue(
            "si no hay almacenamiento externo no se cae al interno, se dice por qué falló",
            t.contains("No hay almacenamiento externo disponible"),
        )
        assertTrue("la huella tiene que comprobarse al cargar", t.contains("Verificador.comprobar"))
        assertTrue(
            "si la fuente declara una huella y no cuadra, no se usa lo descargado",
            t.contains("fuente.huellaEsperada != null"),
        )
        assertTrue(
            "una playlist HLS no es una lista de canales y no debe guardarse como tal",
            t.contains("r.esListaHls"),
        )
        for (clave in listOf(
            "almacen_creado", "fuentes", "canales_cargados", "programas_de_guia",
            "reproductor_creado", "ultimo_motivo", "motivo_del_almacen",
        )) {
            assertTrue("el health check no informa de $clave", t.contains("\"$clave\" to"))
        }
        assertTrue(
            "al parar y al destruir hay que liberar el reproductor",
            t.contains("reproductor?.liberar()"),
        )
        assertTrue(
            "initialize no debe crear el ExoPlayer: reserva decodificador sin que " +
                "nadie lo haya pedido",
            !t.contains("override fun initialize(config: Map<String, Any>): Boolean {\n        reproductor"),
        )
    }
}
