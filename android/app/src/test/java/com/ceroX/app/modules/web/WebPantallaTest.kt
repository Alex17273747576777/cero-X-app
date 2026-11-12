package com.ceroX.app.modules.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** El cableado de la pantalla web: navegación, vistas y recursos traducidos. */
class WebPantallaTest {

    private val fuentes = File("src/main/java/com/ceroX/app")
    private val recursos = File("src/main/res")
    private val pantalla = File(fuentes, "modules/web/WebUiFragment.kt").readText()
    private val layout = File(recursos, "layout/fragment_web.xml").readText()

    @Test
    fun `hay una sola MainActivity y el destino web vive en ella`() {
        val repetidas = File(fuentes, "ui").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { Regex("\\bclass\\s+MainActivity\\b").containsMatchIn(it.readText()) }
            .map { it.relativeTo(File(fuentes, "ui")).invariantSeparatorsPath }
            .sorted()
            .toList()

        assertEquals(
            "MainActivity duplicada: el mismo paquete y nombre no compila",
            listOf("main/MainActivity.kt"),
            repetidas,
        )
        val principal = File(fuentes, "ui/main/MainActivity.kt").readText()
        assertTrue("Web no está en la barra inferior", principal.contains("Destino(\"web\""))
        assertTrue("el destino no crea la pantalla web", principal.contains("WebUiFragment()"))
    }

    @Test
    fun `cada vista que toca el fragmento existe en su layout y se usa`() {
        val declaradas = Regex("""android:id="@\+id/([a-z0-9_]+)"""")
            .findAll(layout).map { it.groupValues[1] }.toSet()
        val usadas = Regex("""\bb\.([a-z][A-Za-z0-9]*)""")
            .findAll(pantalla).map { it.groupValues[1] }.filter { it != "root" }
            .map { aSnake(it) }.toSet()

        assertTrue("el layout no declara vistas", declaradas.isNotEmpty())
        assertEquals("vistas pedidas y ausentes del layout", emptySet<String>(), usadas - declaradas)
        assertEquals("vistas declaradas que nadie usa", emptySet<String>(), declaradas - usadas)
    }

    @Test
    fun `las cadenas web estan en los quince idiomas`() {
        val carpetas = listOf("values") + listOf(
            "en", "ca", "pt", "fr", "id", "ja", "ko", "ru", "th", "tr", "uk", "vi", "zh", "ar",
        ).map { "values-$it" }
        val base = File(recursos, "values/strings.xml").readText()
        val claves = Regex("""<string name="(web_[a-z_]+)">""")
            .findAll(base).map { it.groupValues[1] }.toSet()
        assertTrue("la pantalla no tiene cadenas propias", claves.isNotEmpty())

        for (carpeta in carpetas) {
            val xml = File(recursos, "$carpeta/strings.xml").readText()
            for (clave in claves) {
                val valor = Regex("""<string name="$clave">([^<]+)</string>""").find(xml)
                assertTrue("falta $clave en $carpeta", valor != null)
                assertTrue("$clave está vacía en $carpeta", valor!!.groupValues[1].isNotBlank())
            }
        }
    }

    @Test
    fun `los controles tienen efecto y el WebView se destruye`() {
        val modulo = File(fuentes, "modules/web/WebModule.kt").readText()
        assertTrue("el motor no se puede cambiar", pantalla.contains("MotorBusqueda.siguiente"))
        assertTrue("la lista propia no se lee", modulo.contains("AlmacenExterno.archivo"))
        assertTrue("la ruta de la lista no es la esperada", modulo.contains("web/bloqueo.txt"))
        assertTrue("no se borran las cookies", pantalla.contains("removeAllCookies"))
        assertTrue("no se borra el almacenamiento web", pantalla.contains("WebStorage"))
        assertTrue("las descargas no pasan por el sistema", pantalla.contains("DownloadManager"))
        assertTrue("el WebView no se destruye", pantalla.contains("destroy()"))
    }

    private fun aSnake(nombre: String): String = buildString {
        for (c in nombre) {
            if (c.isUpperCase()) append('_').append(c.lowercaseChar()) else append(c)
        }
    }
}
