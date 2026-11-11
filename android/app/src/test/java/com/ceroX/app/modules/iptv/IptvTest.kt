package com.ceroX.app.modules.iptv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * La capa de datos del IPTV, probada de verdad.
 *
 * Nada de esto toca el framework: leer una lista M3U, convertir horas XMLTV, decidir
 * qué se está emitiendo y aplicar la política de fuentes son funciones puras, así que
 * se ejecutan en el JVM. Lo que sí necesita Android —el `XmlPullParser`— se prueba
 * aparte, en `XmltvLectorTest`, con Robolectric.
 */
class IptvTest {

    // ── listas de canales ────────────────────────────────────────────────────

    private val listaValida = """
        #EXTM3U
        #EXTGRP:General
        #EXTINF:-1 tvg-id="la1.es" tvg-name="La 1" tvg-logo="https://ejemplo.invalid/la1.png" group-title="Nacional",La 1 HD
        #EXTVLCOPT:http-user-agent=Mozilla/5.0 (X11; Linux x86_64)
        https://ejemplo.invalid/la1/index.m3u8
        #EXTINF:-1 tvg-id=24h.es,Canal 24 Horas
        #KODIPROP:inputstream.adaptive.license_type=clearkey
        https://ejemplo.invalid/24h.m3u8
        #EXTINF:0 tvg-logo="https://ejemplo.invalid/radio.png" group-title="",Radio Nacional
        https://ejemplo.invalid/rne.m3u8
    """.trimIndent()

    @Test
    fun `una lista de canales se lee con sus atributos`() {
        val r = ListaM3U.parsear(listaValida, "prueba.m3u")
        assertNull("no debería haber motivo de fallo: " + r.motivo, r.motivo)
        assertFalse("no es una playlist HLS", r.esListaHls)
        assertTrue("debería considerarse correcta", r.correcta)
        assertEquals(3, r.canales.size)

        val primero = r.canales[0]
        assertEquals("La 1 HD", primero.titulo)
        assertEquals("la1.es", primero.tvgId)
        assertEquals("La 1", primero.tvgNombre)
        assertEquals("https://ejemplo.invalid/la1.png", primero.logo)
        assertEquals("Nacional", primero.grupo)
        assertEquals(-1, primero.duracionSegundos)
        assertEquals("https://ejemplo.invalid/la1/index.m3u8", primero.url)

        // El segundo no trae group-title y hereda el #EXTGRP anterior.
        assertEquals("General", r.canales[1].grupo)
        assertEquals("Canal 24 Horas", r.canales[1].titulo)
        // Sin comillas, que es como lo escriben algunas listas generadas a mano.
        assertEquals("24h.es", r.canales[1].tvgId)

        // El tercero trae group-title presente pero vacío: manda el atributo y no
        // hereda el #EXTGRP anterior.
        assertNull(r.canales[2].grupo)
        assertEquals("Radio Nacional", r.canales[2].titulo)

        assertEquals(listOf("General", "Nacional"), ListaM3U.grupos(r.canales))
    }

    @Test
    fun `las opciones del reproductor se atan al canal que siguen`() {
        val r = ListaM3U.parsear(listaValida)
        assertEquals(
            "Mozilla/5.0 (X11; Linux x86_64)",
            r.canales[0].opciones["http-user-agent"],
        )
        assertEquals("clearkey", r.canales[1].opciones["inputstream.adaptive.license_type"])
        assertTrue("el tercer canal no llevaba opciones", r.canales[2].opciones.isEmpty())
    }

    @Test
    fun `una playlist HLS no se hace pasar por lista de canales`() {
        val hls = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:6
            #EXTINF:5.005,
            segmento000.ts
            #EXTINF:6.006,
            segmento001.ts
            #EXT-X-ENDLIST
        """.trimIndent()
        val r = ListaM3U.parsear(hls, "flujo.m3u8")
        assertTrue("tiene que detectarse como HLS", r.esListaHls)
        assertEquals("ningún segmento es un canal", 0, r.canales.size)
        assertNotNull("y tiene que decir por qué", r.motivo)
        assertTrue(r.motivo!!.contains("#EXT-X-"))

        val maestra = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=1280000,RESOLUTION=704x396
            baja.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2560000
            alta.m3u8
        """.trimIndent()
        val rm = ListaM3U.parsear(maestra)
        assertTrue(rm.esListaHls)
        assertEquals(0, rm.canales.size)
        assertTrue("debe distinguir la maestra", rm.motivo!!.contains("maestra"))
    }

    @Test
    fun `lo que no se puede leer se anota con su linea y su razon`() {
        val rota = """
            #EXTM3U
            #EXTINF:-1 tvg-id="uno",Canal Uno
            #EXTINF:-1 tvg-id="dos",Canal Dos
            https://ejemplo.invalid/dos.m3u8
            esto no es una url
            #EXTINF:-1 tvg-id="tres",
        """.trimIndent()
        val r = ListaM3U.parsear(rota)
        assertEquals("sólo el segundo canal tiene URL detrás", 1, r.canales.size)
        assertEquals("Canal Dos", r.canales[0].titulo)
        assertTrue("debería haber tres anotaciones, hay ${r.saltadas.size}", r.saltadas.size >= 3)
        assertTrue(r.saltadas.any { it.contains("se quedó sin URL detrás") })
        assertTrue(r.saltadas.any { it.contains("tiene espacios") })
        assertTrue(r.saltadas.any { it.contains("sin título ni tvg-name") })
        assertTrue(r.saltadas.any { it.contains("se acaba el fichero") })

        val vacia = ListaM3U.parsear("#EXTM3U\n")
        assertEquals(0, vacia.canales.size)
        assertNotNull("una lista vacía no puede salir sin motivo", vacia.motivo)
    }

    @Test
    fun `una lista pelada de URLs da canales con titulo sacado de la ruta`() {
        val pelada = "https://ejemplo.invalid/canales/deporte.m3u8\nhttps://ejemplo.invalid/noticias.m3u8?token=1\n"
        val r = ListaM3U.parsear(pelada)
        assertNull(r.motivo)
        assertEquals(2, r.canales.size)
        assertEquals("deporte.m3u8", r.canales[0].titulo)
        assertEquals("noticias.m3u8", r.canales[1].titulo)
        assertEquals(-1, r.canales[0].duracionSegundos)

        assertEquals("buscar con cadena vacía devuelve todo", 2, ListaM3U.buscar(r.canales, "").size)
        assertEquals(1, ListaM3U.buscar(r.canales, "DEPORTE").size)
        assertEquals(0, ListaM3U.buscar(r.canales, "inexistente").size)
    }

    // ── horas XMLTV ──────────────────────────────────────────────────────────

    @Test
    fun `las horas XMLTV se convierten a millis y las ilegibles dan null`() {
        // 2026-11-10 21:30:00 +0200 son las 19:30 UTC.
        val conDesfase = HoraXmltv.aMillis("20261110213000 +0200")
        assertNotNull(conDesfase)
        val utc = HoraXmltv.aMillis("20261110193000")
        assertEquals("el desfase tiene que restarse para dar la hora UTC", utc, conDesfase)

        // Pegado, sin espacio, y con decimales.
        assertEquals(utc, HoraXmltv.aMillis("20261110213000+0200"))
        assertEquals(utc, HoraXmltv.aMillis("20261110213000.500 +0200"))

        // Formas cortas que admite la spec.
        assertEquals(HoraXmltv.aMillis("20261110193000"), HoraXmltv.aMillis("202611101930"))
        assertEquals(HoraXmltv.aMillis("20261110190000"), HoraXmltv.aMillis("2026111019"))
        assertEquals(HoraXmltv.aMillis("20261110000000"), HoraXmltv.aMillis("20261110"))

        // Desfase negativo: las 19:30 -0300 son las 22:30 UTC.
        assertEquals(
            HoraXmltv.aMillis("20261110223000"),
            HoraXmltv.aMillis("20261110193000 -0300"),
        )

        // Y lo que no se entiende da null, nunca cero.
        for (malo in listOf(null, "", "   ", "ayer", "2026-11-10 19:30:00", "20261110193000 +02",
            "13000000000000")) {
            assertNull("'$malo' no debería dar una hora", HoraXmltv.aMillis(malo))
        }

        // La ida y vuelta conserva el instante.
        val ida = 1762803000000L
        assertEquals(ida, HoraXmltv.aMillis(HoraXmltv.aTexto(ida)))
        assertTrue(HoraXmltv.aTexto(ida).endsWith(" +0000"))
    }

    // ── guía ─────────────────────────────────────────────────────────────────

    private fun prog(canal: String, inicio: Long, fin: Long?, titulo: String) = Programa(
        canal = canal, inicio = inicio, fin = fin, titulo = titulo,
        subtitulo = null, descripcion = null, categorias = emptyList(), icono = null,
    )

    @Test
    fun `la guia sabe que se emite ahora y que viene despues`() {
        val h0 = 1762800000000L
        val programas = listOf(
            prog("la1", h0, h0 + 1800000, "Telediario 1"),
            prog("la1", h0 + 1800000, h0 + 3600000, "Sobremesa"),
            prog("la1", h0 + 3600000, h0 + 7200000, "Cine"),
            prog("la2", h0 - 600000, null, "Directo sin hora de fin"),
        )

        assertEquals("Telediario 1", Guia.enEmision(programas, "la1", h0 + 60000)?.titulo)
        assertEquals("Sobremesa", Guia.enEmision(programas, "la1", h0 + 1800000)?.titulo)
        assertNull("un hueco sin programa no está en emisión", Guia.enEmision(programas, "la1", h0 - 1)?.titulo)
        assertEquals(
            "un programa sin fin sigue en emisión",
            "Directo sin hora de fin",
            Guia.enEmision(programas, "la2", h0 + 99999999)?.titulo,
        )
        assertNull(Guia.enEmision(programas, "canal-que-no-existe", h0))

        val siguientes = Guia.aContinuacion(programas, "la1", h0 + 1800000, 5)
        assertEquals(listOf("Sobremesa", "Cine"), siguientes.map { it.titulo })
        assertEquals("pedir cero no devuelve nada", 0, Guia.aContinuacion(programas, "la1", h0, 0).size)

        assertEquals(2, Guia.porCanal(programas).size)
        assertEquals(3, Guia.porCanal(programas)["la1"]?.size)
        assertEquals(
            "la franja incluye lo que se solapa, no sólo lo que empieza dentro",
            2, Guia.franja(programas, "la1", h0 + 900000, h0 + 2700000).size,
        )
        assertEquals(30, prog("x", h0, h0 + 1800000, "t").duracionMinutos)
        assertNull(prog("x", h0, null, "t").duracionMinutos)
    }

    // ── fuentes externas ─────────────────────────────────────────────────────

    @Test
    fun `la politica de fuentes rechaza lo que vendria de dentro del APK`() {
        for (mala in listOf("file:///android_asset/canales.m3u", "content://media/iptv",
            "asset://listas.m3u8", "android.resource://com.ceroX.app/raw/canales")) {
            val motivo = PoliticaFuentes.urlAceptable(mala)
            assertNotNull("'$mala' tiene que rechazarse", motivo)
            assertTrue(
                "el motivo tiene que decir que las fuentes vienen de fuera: $motivo",
                motivo!!.contains("fuera de la app"),
            )
        }
        assertNotNull(PoliticaFuentes.urlAceptable(""))
        assertNotNull(PoliticaFuentes.urlAceptable("https://ejemplo.invalid/a b.m3u"))
        assertNotNull(PoliticaFuentes.urlAceptable("ftp://ejemplo.invalid/lista.m3u"))
        assertNotNull(PoliticaFuentes.urlAceptable("ejemplo.invalid/lista.m3u"))
        assertNull(PoliticaFuentes.urlAceptable("https://ejemplo.invalid/lista.m3u"))
        assertNull(PoliticaFuentes.urlAceptable("http://ejemplo.invalid/lista.m3u"))

        assertNull(PoliticaFuentes.tipoAceptable("m3u"))
        assertNull(PoliticaFuentes.tipoAceptable("xmltv"))
        assertNotNull(PoliticaFuentes.tipoAceptable("epg"))

        val existente = Fuente("Casa", "https://ejemplo.invalid/a.m3u", "m3u", null, 0L)
        assertNotNull(PoliticaFuentes.nombreAceptable("", listOf(existente)))
        assertNotNull(PoliticaFuentes.nombreAceptable("casa", listOf(existente)))
        assertNotNull(PoliticaFuentes.nombreAceptable("x".repeat(81), listOf(existente)))
        assertNull(PoliticaFuentes.nombreAceptable("Trabajo", listOf(existente)))
    }

    @Test
    fun `la huella es la conocida y no se hace pasar por comprobada cuando no hay nada que comprobar`() {
        // Vectores conocidos de SHA-256, no calculados por el propio código.
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Verificador.sha256(ByteArray(0)),
        )
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Verificador.sha256("abc".toByteArray()),
        )

        assertNull(
            "mayúsculas y espacios no deberían importar",
            Verificador.comprobar(" BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD ",
                Verificador.sha256("abc".toByteArray())),
        )
        assertNotNull(Verificador.comprobar("0".repeat(64), Verificador.sha256("abc".toByteArray())))
        assertNotNull(
            "sin huella declarada no se puede comprobar el origen, y eso no es un éxito",
            Verificador.comprobar(null, Verificador.sha256("abc".toByteArray())),
        )
        assertNotNull(Verificador.comprobar("", Verificador.sha256("abc".toByteArray())))
        assertNotNull("si no se pudo calcular la huella, tampoco", Verificador.comprobar("x", ""))
    }

    @Test
    fun `el almacen guarda las fuentes en JSON y dice cuando no puede leerlas`() {
        val fichero = File(System.getProperty("java.io.tmpdir"), "fuentes-prueba-${System.nanoTime()}.json")
        try {
            val almacen = AlmacenFuentes(fichero)

            // Sin fichero: lista vacía y ningún motivo, que no es lo mismo que roto.
            val inicial = almacen.listar()
            assertEquals(0, inicial.fuentes.size)
            assertNull(inicial.motivo)

            val primera = almacen.anadir("Casa", "https://ejemplo.invalid/casa.m3u", "m3u", null)
            assertNotNull("debería añadirse: " + primera.motivo, primera.fuente)
            assertNull(primera.motivo)
            assertTrue("el fichero tiene que existir tras guardar", fichero.isFile)

            // Repetidos: mismo nombre, misma URL, tipo desconocido, URL interna.
            assertNotNull(almacen.anadir("casa", "https://ejemplo.invalid/otra.m3u", "m3u", null).motivo)
            assertNotNull(almacen.anadir("Trabajo", "https://ejemplo.invalid/casa.m3u", "m3u", null).motivo)
            assertNotNull(almacen.anadir("Trabajo", "https://ejemplo.invalid/otra.m3u", "epg", null).motivo)
            assertNotNull(almacen.anadir("Trabajo", "file:///android_asset/x.m3u", "m3u", null).motivo)
            assertEquals("nada de eso debería haberse guardado", 1, almacen.listar().fuentes.size)

            val segunda = almacen.anadir(
                "Guía", "https://ejemplo.invalid/guia.xml", "xmltv", "ab".repeat(32),
            )
            assertNotNull(segunda.fuente)
            assertEquals("ab".repeat(32), segunda.fuente?.huellaEsperada)
            assertEquals(2, almacen.listar().fuentes.size)

            // La huella se anota y sobrevive a releer el fichero.
            assertNull(almacen.registrarHuella("Casa", "cd".repeat(32)))
            assertNotNull(almacen.registrarHuella("Inexistente", "x"))
            val releido = AlmacenFuentes(fichero).listar()
            assertNull("releer no debería dar motivo: " + releido.motivo, releido.motivo)
            assertEquals(2, releido.fuentes.size)
            assertEquals("cd".repeat(32), releido.fuentes.first { it.nombre == "Casa" }.ultimaHuella)
            assertEquals(0, almacen.quitar("Casa").let { if (it == null) 0 else 1 })
            assertEquals(1, AlmacenFuentes(fichero).listar().fuentes.size)
            assertNotNull(almacen.quitar("Casa"))

            // Un fichero roto se informa, no se borra a escondidas.
            fichero.writeText("{esto no es json", Charsets.UTF_8)
            val roto = AlmacenFuentes(fichero).listar()
            assertEquals(0, roto.fuentes.size)
            assertNotNull("un JSON ilegible tiene que dar motivo", roto.motivo)
            assertTrue("y no debe borrar el fichero del usuario", fichero.isFile)
        } finally {
            fichero.delete()
            File(fichero.path + ".tmp").delete()
        }
    }

    // ── nada empaquetado ─────────────────────────────────────────────────────

    @Test
    fun `el APK no lleva ninguna lista de canales ni ninguna guia`() {
        // Extensiones que delatan una lista de canales o una guía empaquetada.
        val deLista = listOf("m3u", "m3u8", "xml", "xmltv")
        val culpables = mutableListOf<String>()
        for (carpeta in listOf("src/main/assets", "src/main/res/raw")) {
            val dir = File(carpeta)
            if (!dir.isDirectory) continue
            dir.walkTopDown().filter { it.isFile }.forEach { f ->
                val ext = f.extension.lowercase()
                if (ext in deLista) {
                    culpables.add(f.path)
                } else if (ext == "json" && !f.path.contains("assets${File.separator}models")) {
                    // Un .json fuera de assets/models podría ser un índice de fuentes
                    // empaquetado. Los de assets/models son el manifiesto de los
                    // modelos, que ModelHonestyTest ya vigila por su cuenta.
                    culpables.add(f.path)
                }
            }
        }
        assertTrue(
            "hay ficheros de datos empaquetados en el APK:\n  " + culpables.joinToString("\n  ") +
                "\n\nEl IPTV de cero-X es neutro: las fuentes viven fuera y las añade el " +
                "usuario. Nada de listas ni de guías dentro de la aplicación.",
            culpables.isEmpty(),
        )
    }

    @Test
    fun `ninguna URL de lista esta escrita a mano en el codigo del IPTV`() {
        val dir = File("src/main/java/com/ceroX/app/modules/iptv")
        assertTrue("falta el paquete del IPTV", dir.isDirectory)
        // Una URL que apunta a una lista o a una guía sería una fuente empaquetada
        // en el código. El agente de usuario lleva una URL del repositorio, que no
        // es una fuente, y por eso el patrón mira la extensión.
        val patron = Regex("\"https?://[^\"]*\\.(m3u8?|xmltv|xml|ts)([?\"#][^\"]*)?\"")
        val culpables = mutableListOf<String>()
        dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
            f.readLines().forEachIndexed { i, linea ->
                if (patron.containsMatchIn(linea)) culpables.add("${f.name}:${i + 1}")
            }
        }
        assertTrue(
            "hay URLs de fuentes escritas a mano:\n  " + culpables.joinToString("\n  "),
            culpables.isEmpty(),
        )
    }
}
