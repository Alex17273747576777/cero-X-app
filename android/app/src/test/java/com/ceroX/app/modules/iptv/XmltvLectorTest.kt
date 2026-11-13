package com.ceroX.app.modules.iptv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * El lector XMLTV, que sí necesita Android.
 *
 * Va con Robolectric porque `XmlPullParserFactory` es del framework: en una prueba
 * JVM normal el `returnDefaultValues` de Gradle devolvería nulos en vez de ejecutar
 * nada, y las pruebas pasarían sin probar. Es la misma razón por la que
 * `PageCacheTest` va con Robolectric.
 */
// Robolectric 4.13 no conoce targetSdk 36; se fija Android 14, la última imagen que
// trae. El lector sólo usa XmlPullParser, idéntico desde API 26.
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [36])
class XmltvLectorTest {

    private val guia = """
        <?xml version="1.0" encoding="UTF-8"?>
        <tv generator-info-name="prueba">
          <channel id="la1.es">
            <display-name>La 1</display-name>
            <display-name>La 1 HD</display-name>
            <icon src="https://ejemplo.invalid/la1.png"/>
          </channel>
          <channel id="la2.es">
            <display-name>La 2</display-name>
          </channel>
          <programme start="20261110200000 +0000" stop="20261110210000 +0000" channel="la1.es">
            <title lang="es">Telediario 1</title>
            <sub-title>Edición de las ocho</sub-title>
            <desc lang="es">Informativo de la noche.</desc>
            <category>Noticias</category>
            <category>Actualidad</category>
            <icon src="https://ejemplo.invalid/td1.png"/>
          </programme>
          <programme start="20261110210000 +0000" channel="la1.es">
            <title>Cine de noche</title>
          </programme>
          <programme start="ayer" channel="la2.es">
            <title>Este se descarta</title>
          </programme>
          <programme start="20261110220000 +0000" channel="la2.es">
            <title>Documental</title>
            <desc></desc>
          </programme>
        </tv>
    """.trimIndent()

    @Test
    fun `lee canales y programas de una guia real`() {
        val r = LectorXmltv.leer(guia, "guia.xml")
        assertNull("no debería haber motivo: " + r.motivo, r.motivo)
        assertTrue(r.correcta)
        assertEquals(2, r.canales.size)

        val la1 = r.canales.first { it.id == "la1.es" }
        assertEquals(listOf("La 1", "La 1 HD"), la1.nombres)
        assertEquals("La 1", la1.nombre)
        assertEquals("https://ejemplo.invalid/la1.png", la1.icono)

        assertEquals("se descartó uno, así que quedan tres", 3, r.programas.size)
        val td1 = r.programas.first { it.titulo == "Telediario 1" }
        assertEquals("la1.es", td1.canal)
        assertEquals(HoraXmltv.aMillis("20261110200000 +0000"), td1.inicio)
        assertEquals(60, td1.duracionMinutos)
        assertEquals("Edición de las ocho", td1.subtitulo)
        assertEquals("Informativo de la noche.", td1.descripcion)
        assertEquals(listOf("Noticias", "Actualidad"), td1.categorias)
        assertEquals("https://ejemplo.invalid/td1.png", td1.icono)

        // El que no trae stop se queda sin fin, y un desc vacío no es descripción.
        val cine = r.programas.first { it.titulo == "Cine de noche" }
        assertNull(cine.fin)
        val doc = r.programas.first { it.titulo == "Documental" }
        assertNull("un <desc> vacío no debería contar como descripción", doc.descripcion)
    }

    @Test
    fun `una entrada rota se descarta con su razon y no arrastra a las demas`() {
        val r = LectorXmltv.leer(guia)
        assertEquals(1, r.descartados.size)
        assertTrue(
            "el motivo tiene que decir qué pasaba: ${r.descartados.firstOrNull()}",
            r.descartados.first().contains("start ilegible"),
        )
        assertEquals("los demás programas siguen ahí", 3, r.programas.size)

        val sinTitulo = LectorXmltv.leer(
            """<?xml version="1.0"?><tv><programme start="20261110200000" channel="x"><desc>Sin título</desc></programme></tv>"""
        )
        assertEquals(0, sinTitulo.programas.size)
        assertTrue(sinTitulo.descartados.any { it.contains("sin <title>") })

        val sinCanal = LectorXmltv.leer(
            """<?xml version="1.0"?><tv><channel><display-name>Sin id</display-name></channel></tv>"""
        )
        assertEquals(0, sinCanal.canales.size)
        assertTrue(sinCanal.descartados.any { it.contains("sin atributo id") })
    }

    @Test
    fun `lo que no es XMLTV vuelve con el motivo y sin datos`() {
        val vacio = LectorXmltv.leer("", "vacio.xml")
        assertEquals(0, vacio.canales.size)
        assertEquals(0, vacio.programas.size)
        assertNotNull("un XMLTV vacío tiene que dar motivo", vacio.motivo)

        val otroXml = LectorXmltv.leer("""<?xml version="1.0"?><rss><channel><title>Noticias</title></channel></rss>""")
        assertNotNull("un RSS no es una guía y debe decirse", otroXml.motivo)
        assertEquals(0, otroXml.programas.size)

        val roto = LectorXmltv.leer("<tv><programme start=")
        assertNotNull("XML truncado tiene que dar motivo, no reventar", roto.motivo)
        assertTrue(roto.motivo!!.contains("No se pudo leer"))

        // Y la guía leída se puede consultar con la lógica pura.
        val r = LectorXmltv.leer(guia)
        val ahora = HoraXmltv.aMillis("20261110203000 +0000") as Long
        assertEquals("Telediario 1", Guia.enEmision(r.programas, "la1.es", ahora)?.titulo)
        assertEquals(
            listOf("Cine de noche"),
            Guia.aContinuacion(r.programas, "la1.es", ahora, 3).map { it.titulo },
        )
    }
}
