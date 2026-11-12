package com.ceroX.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Que los flujos de GitHub Actions se puedan leer.
 *
 * DE DÓNDE VIENE
 * --------------
 * Un commit añadió al flujo del APK la derivación de la etiqueta y de la lista de
 * cambios. La continuación de una sustitución de mandato se escribió en la columna 0 del
 * fichero, dentro de un bloque de varias líneas que pedía diez espacios de sangría. En
 * YAML eso cierra el bloque, y lo que viene después ya no es un mandato: es una clave
 * que no existe. GitHub lo reportó como un run fallido **sin ningún job** y con el nombre
 * en forma de ruta, que es como dice «no he podido ni leer el fichero».
 *
 * Las 472 pruebas salieron en verde al lado del run fallido. Ninguna leía los flujos:
 * una prueba de JVM no los toca y el compilador no los ve. Ésta sí los lee.
 *
 * QUÉ COMPRUEBA
 * -------------
 * La regla estructural que se rompió, sin necesitar una biblioteca de YAML en el
 * classpath: dentro de un bloque literal, toda línea con menos sangría que el bloque
 * tiene que ser una clave o un elemento de lista. Si es otra cosa, cuelga fuera del
 * bloque y el fichero ya no es válido.
 *
 * No es un validador de YAML completo y no pretende serlo: es el detector del fallo que
 * ocurrió, que es lo que una prueba de este proyecto hace con cada fallo que ocurre.
 */
class FlujosValidosTest {

    private val raiz: File = run {
        var d = File(".").absoluteFile
        while (!File(d, ".github/workflows").isDirectory && d.parentFile != null) {
            d = d.parentFile
        }
        require(File(d, ".github/workflows").isDirectory) { "no se encontró el repositorio" }
        d
    }

    private val flujos: List<File>
        get() = File(raiz, ".github/workflows").listFiles { f -> f.extension == "yml" }
            ?.sortedBy { it.name }
            ?: emptyList()

    /** `clave: |` o `clave: |-`, que abre un bloque literal. */
    private val apertura = Regex("""^(\s*)[^\s#][^:]*:\s*\|[-+]?\s*$""")

    /** Lo que sí puede aparecer con menos sangría: una clave o un elemento de lista. */
    private val claveOLista = Regex("""^\s*(?:-\s)?[^\s:][^:]*:(?:\s|$)""")

    /** Líneas que cuelgan fuera de la sangría del bloque literal donde nacieron. */
    private fun colgando(texto: String): List<String> {
        val lineas = texto.split("\n")
        val problemas = mutableListOf<String>()
        var i = 0
        while (i < lineas.size) {
            val m = apertura.find(lineas[i])
            if (m == null) {
                i++
                continue
            }
            var j = i + 1
            while (j < lineas.size && lineas[j].isBlank()) j++
            if (j >= lineas.size) break
            val sangria = lineas[j].length - lineas[j].trimStart().length
            if (sangria <= m.groupValues[1].length) {
                i = j
                continue
            }
            var k = j
            while (k < lineas.size) {
                val l = lineas[k]
                if (l.isBlank()) {
                    k++
                    continue
                }
                if (l.length - l.trimStart().length >= sangria) {
                    k++
                    continue
                }
                if (claveOLista.containsMatchIn(l) || l.trim() == "...") break
                problemas.add("línea " + (k + 1) + ": " + l.trim().take(60))
                break
            }
            i = maxOf(k, i + 1)
        }
        return problemas
    }

    @Test
    fun `la puerta encuentra los flujos del repo`() {
        // Sin esto la prueba pasaría sin haber mirado nada, que es la forma más tonta
        // de tener una puerta: un recorrido vacío no encuentra culpables.
        assertTrue("no se leyó ningún flujo", flujos.size >= 2)
        assertTrue("falta el flujo del APK", flujos.any { it.name == "apk.yml" })
        assertTrue("falta el flujo de las pruebas", flujos.any { it.name == "pruebas.yml" })
    }

    @Test
    fun `ninguna linea cuelga fuera de la sangria de su bloque`() {
        val culpables = mutableListOf<String>()
        for (f in flujos) {
            for (p in colgando(f.readText())) {
                culpables.add(f.name + " " + p)
            }
        }
        assertEquals(
            "líneas fuera de la sangría de su bloque literal. El flujo deja de ser YAML " +
                "válido y GitHub lo reporta como un run fallido sin ningún job, que no " +
                "dice dónde está el fallo: hay que leer el fichero",
            emptyList<String>(),
            culpables,
        )
    }

    @Test
    fun `la puerta caza la columna cero que rompio el flujo`() {
        // La muestra mala es el fallo exacto, no uno parecido. Una puerta que no caza
        // su propio incidente es decoración.
        val roto = listOf(
            "jobs:",
            "  apk:",
            "    steps:",
            "      - name: Publicar",
            "        run: |",
            "          if [ -n \"\$X\" ]; then",
            "            CAMBIOS=\"Cambios desde \$X:",
            "\$(git log --oneline)",
            "          fi",
        ).joinToString("\n")
        val bien = roto.replace("\n\$(git log", "\n            \$(git log")

        assertTrue("no caza el fallo que la produjo", colgando(roto).isNotEmpty())
        assertEquals("acusa un texto que está bien", emptyList<String>(), colgando(bien))
    }
}
