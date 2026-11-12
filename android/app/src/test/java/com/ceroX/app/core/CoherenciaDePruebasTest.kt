package com.ceroX.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Que las pruebas no se contradigan, y que ninguna exija lo que el proyecto prohíbe.
 *
 * DE DÓNDE VIENE
 * --------------
 * El módulo de IPTV guardaba las fuentes del usuario en el directorio interno de la
 * app. [ReglaDeAlmacenamientoTest] lo prohibía y llevaba publicada semanas: nadie la
 * leyó antes de escribir. Y lo peor no fue el código, fue que la prueba nueva del
 * módulo **exigía** ese directorio, con un mensaje que decía justo lo contrario de la
 * regla del proyecto («se guardan en el almacenamiento privado»). Una prueba así no
 * protege de nada: convierte el fallo en requisito. Cuando otra prueba lo denunció,
 * el mensaje que se leyó hablaba de almacenamiento y no de la contradicción, así que
 * el arreglo tardó un commit más de lo que hacía falta.
 *
 * QUÉ COMPROBADO
 * --------------
 *   1. Nada de lo prohibido en la tabla está en el **código** de la ruta indicada.
 *      Se mira el código y no la prosa: un comentario puede explicar por qué algo
 *      está prohibido, y de hecho debe. Las puertas que miran el fichero entero son
 *      otra cosa y viven en sus propias pruebas.
 *   2. Ninguna prueba publicada **exige la presencia** de algo prohibido. Ésta es la
 *      que habría cazado el incidente en el acto y diciendo el nombre del fichero.
 *   3. Esta prueba no escribe los literales que prohíbe: los compone a trozos. Es la
 *      disciplina que se le pide a cualquier comentario del proyecto, aplicada aquí y
 *      comprobada contra sí misma en vez de fiada.
 *   4. Cada prohibición dice por qué existe y de qué incidente viene. Una regla sin
 *      procedencia se vuelve superstición y se borra a la primera molestia.
 *
 * POR QUÉ LA COMPARACIÓN ES POR PALABRA Y NO POR SUBCADENA
 * --------------------------------------------------------
 * Dos mecanismos que el proyecto **aprueba** llevan dentro el nombre de lo
 * prohibido: el almacén cifrado donde viven los tokens, y el método que migra las
 * preferencias viejas para poder borrarlas. Por subcadena se denunciaban los dos, y
 * la prueba habría nacido fallando. Con la frontera de palabra no. El caso del
 * incidente se sigue cazando igual, porque allí lo prohibido iba detrás de un punto.
 */
class CoherenciaDePruebasTest {

    private val raiz: File = run {
        var d = File(".").absoluteFile
        while (!File(d, "src/main/java/com/ceroX/app").isDirectory && d.parentFile != null) {
            d = d.parentFile
        }
        require(File(d, "src/main/java/com/ceroX/app").isDirectory) { "no se encontró el árbol" }
        d
    }

    /**
     * Una prohibición, con su porqué y su incidente.
     *
     * Los trozos se componen en vez de escribir el literal seguido: si este fichero
     * contuviera lo que prohíbe, la prueba 3 fallaría, y con razón.
     */
    private class Prohibicion(
        val ruta: String,
        val trozos: List<String>,
        val porque: String,
        val incidente: String,
    ) {
        val literal: String get() = trozos.joinToString("")

        private val patron: Regex
            get() {
                val primero = literal.first()
                val frontera = if (primero.isLetterOrDigit() || primero == '_') {
                    "(?<![A-Za-z0-9_])"
                } else {
                    ""
                }
                return Regex(frontera + Regex.escape(literal))
            }

        fun apareceEn(texto: String): Boolean = patron.containsMatchIn(texto)
    }

    private val prohibiciones = listOf(
        Prohibicion(
            "src/main/java/com/ceroX/app/modules/iptv",
            listOf("files", "Dir"),
            "los datos del usuario van a la carpeta propia de la app en el " +
                "almacenamiento externo; el interno sólo guarda los tokens cifrados",
            "917c3f06 lo guardaba ahí y 708a9904 lo sacó",
        ),
        Prohibicion(
            "src/main/java/com/ceroX/app/modules/iptv",
            listOf("Shared", "Preferences"),
            "las preferencias de Android viven en el interno y no se pueden " +
                "redirigir; los ajustes van a core/Prefs",
            "regla de almacenamiento del proyecto, vigilada por ReglaDeAlmacenamientoTest",
        ),
        Prohibicion(
            "src/main/java/com/ceroX/app/modules/iptv",
            listOf("cache", "Dir"),
            "la caché interna no es sitio para las fuentes que instala el usuario",
            "misma regla de almacenamiento",
        ),
        Prohibicion(
            "src/main/java/com/ceroX/app",
            listOf("registry", ".getInstance"),
            "ese camino devolvía null porque los módulos se registran en el gestor " +
                "de ciclo de vida, y el cast no nulo cerraba la pantalla al abrirla",
            "3f0fe9d0 y 83c1ef78: seis pantallas se cerraban al tocarlas",
        ),
        Prohibicion(
            "src/main/java/com/ceroX/app",
            listOf("create", "MediaItem"),
            "un método del reproductor que no existe: lo escribió la memoria en vez " +
                "de la documentación, y sólo lo caza el compilador",
            "c7815891, después de un rojo en CI con Unresolved reference",
        ),
        Prohibicion(
            "build.gradle",
            listOf("latest", ".release"),
            "un resolvedor que elige la última versión cambiaría lo que se compila " +
                "sin que nadie lo decida",
            "917c3f06, donde el comentario que lo explicaba lo escribía",
        ),
        Prohibicion(
            "build.gradle",
            listOf("media3-exoplayer", ":+"),
            "un rango dinámico en la pieza principal del reproductor mezclaría dos " +
                "versiones distintas del mismo reproductor",
            "917c3f06: las dos piezas llevan la versión fija y la misma",
        ),
        Prohibicion(
            "build.gradle",
            listOf("media3-exoplayer-hls", ":+"),
            "y lo mismo en la pieza de las listas en directo",
            "917c3f06",
        ),
        Prohibicion(
            "src/main/java/com/ceroX/app",
            listOf("TODO", ":"),
            "una marca de trabajo pendiente en el código publicado es relleno: o se " +
                "hace, o se devuelve el fallo con su motivo",
            "barrido de fabricación 8d56dff7…c7c657d4, 45 sitios que inventaban éxito",
        ),
    )

    /** El código de un archivo, sin comentarios ni KDoc. */
    private fun codigo(f: File): String = f.readLines()
        .filterNot { it.trimStart().startsWith("*") }
        .filterNot { it.trimStart().startsWith("//") }
        .filterNot { it.trimStart().startsWith("/*") }
        .joinToString("\n")

    private fun ficherosDe(ruta: String): List<File> {
        val f = File(raiz, ruta)
        assertTrue("la tabla nombra una ruta que no existe: $ruta", f.exists())
        return if (f.isDirectory) {
            f.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        } else {
            listOf(f)
        }
    }

    /** La aserción más cercana hacia arriba: verdadero si manda un assertTrue. */
    private fun asercionCercana(lineas: List<String>, i: Int): Boolean {
        for (j in i downTo maxOf(0, i - 8)) {
            val l = lineas[j]
            if (l.contains("assertTrue")) return true
            if (l.contains("assertFalse")) return false
        }
        return false
    }

    @Test
    fun `lo prohibido no esta en el codigo`() {
        for (p in prohibiciones) {
            val infractores = ficherosDe(p.ruta)
                .filter { p.apareceEn(codigo(it)) }
                .map { it.name }
            assertEquals(
                p.porque + " — y sin embargo aparece en: " + infractores,
                emptyList<String>(),
                infractores,
            )
        }
    }

    @Test
    fun `ninguna prueba exige la presencia de lo que el proyecto prohibe`() {
        val contradicciones = mutableListOf<String>()
        val pruebas = File(raiz, "src/test").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
        assertTrue("no se leyó ninguna prueba", pruebas.isNotEmpty())

        for (prueba in pruebas) {
            val lineas = prueba.readLines()
            for ((i, linea) in lineas.withIndex()) {
                if (!linea.contains("contains(")) continue
                for (p in prohibiciones) {
                    if (!p.apareceEn(linea)) continue
                    if (asercionCercana(lineas, i)) {
                        contradicciones.add(
                            prueba.name + ":" + (i + 1) + " exige algo prohibido en " + p.ruta,
                        )
                    }
                }
            }
        }
        assertEquals(
            "pruebas que exigen lo que otra prohíbe. Se cambia el código primero y la " +
                "prueba después, nunca al revés, y nunca se relaja una aserción para " +
                "que pase",
            emptyList<String>(),
            contradicciones.distinct(),
        )
    }

    @Test
    fun `esta prueba no escribe los literales que prohibe`() {
        val propio = File(raiz, "src/test/java/com/ceroX/app/core/CoherenciaDePruebasTest.kt")
        assertTrue("no me encuentro a mí misma", propio.isFile)
        val texto = propio.readText()
        val citados = prohibiciones.filter { texto.contains(it.literal) }
            .map { "la de " + it.ruta.substringAfterLast('/') }
        assertEquals(
            "esta prueba cita lo que prohíbe: describir, no citar, que es lo mismo " +
                "que se le pide a cualquier comentario del proyecto",
            emptyList<String>(),
            citados,
        )
    }

    @Test
    fun `cada prohibicion dice por que existe y de que incidente viene`() {
        val mudas = prohibiciones
            .filter { it.porque.length < 25 || it.incidente.isBlank() || it.trozos.isEmpty() }
            .map { it.ruta }
        assertEquals(
            "prohibiciones sin explicar: una regla que no dice de dónde viene se borra " +
                "a la primera molestia",
            emptyList<String>(),
            mudas,
        )
    }
}
