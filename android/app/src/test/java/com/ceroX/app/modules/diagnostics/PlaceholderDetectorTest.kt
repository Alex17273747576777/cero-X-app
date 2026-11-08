package com.ceroX.app.modules.diagnostics

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Caza los métodos que dicen haber hecho algo sin hacerlo.
 *
 * POR QUÉ EXISTE ESTA PRUEBA
 * --------------------------
 * Este proyecto ha tenido, como mínimo, siete sitios con el mismo patrón: un
 * método con el comentario "Placeholder" o "TODO" al lado que devolvía éxito.
 * Los que se encontraron y arreglaron:
 *
 *   NeuralAIModule.colorizeMangaPage()   devolvía success = true sin colorizar
 *   ExtensionInstaller.installViaSystem() devolvía true sin instalar
 *   ExtensionInstaller.installViaSandbox() idem
 *   ExtensionInstaller.detectShizuku()   devolvía false fijo
 *   BackupManager.restoreBackup()        success = true con restoredItems = 0
 *   ExtensionFactory.isEnabled()         devolvía true fijo
 *   KisaraReaderEngineModule             anunciaba 3 modelos inexistentes
 *
 * Encontrarlos uno a uno a mano funciona hasta que dejas de mirar. Esta prueba
 * los busca todos de golpe, en cada `gradle test`, y falla nombrando el archivo
 * y la línea.
 *
 * QUÉ ES UN PLACEHOLDER PELIGROSO
 * -------------------------------
 * No todo "TODO" es un problema: documentar una idea pendiente es sano. Lo que
 * no se puede tener es un método que **devuelve éxito** y al mismo tiempo lleva
 * el comentario de que no hace nada. Eso es una mentira con fecha de caducidad
 * que nadie ve.
 *
 * Por eso la prueba busca la COMBINACIÓN: comentario de relleno + `return true`,
 * `success = true` o `Resultado(exito = true` en las líneas siguientes.
 */
class PlaceholderDetectorTest {

    private val raiz = File("src/main/java/com/ceroX/app")

    /** Palabras que marcan un método sin terminar. */
    private val marcasDeRelleno = listOf(
        "placeholder",
        "placeholder:",
        "TODO:",
        "FIXME",
        "not implemented",
        "sin implementar",
        "de momento devuelve",
        "hardcodeado",
    )

    /** Lo que indica que el método dice haber tenido éxito. */
    private val formasDeExito = listOf(
        "return true",
        "success = true",
        "exito = true",
        "isValid = true",
        "IsValid = true",
    )

    /** Cuántas líneas hacia delante se busca el éxito tras la marca. */

    @Test
    fun `ningun metodo con marca de relleno devuelve exito`() {
        val culpables = mutableListOf<String>()

        raiz.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { archivo ->
                val lineas = archivo.readLines()
                lineas.forEachIndexed { i, linea ->
                    val t = linea.trim()
                    // DOS FALSOS POSITIVOS QUE ESTA PRUEBA TENÍA, Y HAY QUE
                    // QUITARLOS O LA GENTE DEJA DE CREERLE:
                    //
                    //  1. Los comentarios que EMPIEZAN por `*` o `//` y describen el
                    //     fallo antiguo ("antes marcaba success = true con el
                    //     comentario Placeholder") se acusaban a sí mismos. Un
                    //     comentario KDoc que documenta lo que se arregló es lo
                    //     contrario de un placeholder.
                    //  2. Un comentario de una sola línea `// Placeholder: X` seguido
                    //     de un `return true` a seis líneas es ambiguo. Se exige que
                    //     la marca esté en la MISMA línea o pegada al `return`.
                    if (t.startsWith("*") || t.startsWith("/*")) return@forEachIndexed
                    if (t.startsWith("//") && !t.lowercase().contains("placeholder")) {
                        return@forEachIndexed
                    }

                    val limpia = linea.lowercase()
                    val marca = marcasDeRelleno.firstOrNull { limpia.contains(it.lowercase()) }
                        ?: return@forEachIndexed

                    // Ventana corta: el éxito tiene que estar en las 3 líneas
                    // siguientes, no a seis. Si no, se acusa a cualquier método que
                    // tenga un TODO encima y un return abajo.
                    val desde = i
                    val hasta = minOf(lineas.size - 1, i + 3)
                    val contexto = (desde..hasta).joinToString("\n") { lineas[it] }

                    if (formasDeExito.any { contexto.contains(it) }) {
                        culpables.add(
                            "${archivo.path.substringAfter("com/ceroX/app/")}:${i + 1}\n" +
                                "      marca: ${linea.trim()}\n" +
                                "      éxito cerca: " +
                                lineas.subList(desde, hasta + 1)
                                    .firstOrNull { l -> formasDeExito.any { l.contains(it) } }
                                    ?.trim()
                        )
                    }
                }
            }

        assertTrue(
            "estos métodos dicen haber hecho algo y llevan escrito que no:\n  " +
                culpables.joinToString("\n  ") +
                "\n\nSi el método es de verdad un relleno, que devuelva fallo con el motivo. " +
                "Un 'no se puede' honesto deja al usuario hacer otra cosa; un 'hecho' falso no.",
            culpables.isEmpty()
        )
    }

    @Test
    fun `ningun healthCheck devuelve true escrito a mano sin condicion`() {
        // El patrón concreto que apareció tres veces:
        //     "algo_compatible" to true
        //     "algo_mejorado" to true
        // dentro de un healthCheck, sin ninguna condición ni comprobación.
        val culpables = mutableListOf<String>()
        // Claves cuyo valor true es una afirmación, no un ajuste del usuario.
        val sospechosas = listOf(
            "compatible", "mejorado", "improved", "stabilized", "estable",
            "verificado", "verified", "funciona", "works", "listo",
        )

        raiz.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { archivo ->
                if (!archivo.readText().contains("healthCheck")) return@forEach
                archivo.readLines().forEachIndexed { i, linea ->
                    val t = linea.trim()
                    if (!t.contains(" to true")) return@forEachIndexed
                    if (t.startsWith("//") || t.startsWith("*")) return@forEachIndexed
                    val clave = t.substringBefore("\" to true").substringAfterLast("\"")
                    if (sospechosas.any { clave.lowercase().contains(it) }) {
                        culpables.add("${archivo.name}:${i + 1} → \"$clave\" to true")
                    }
                }
            }

        assertTrue(
            "claves de estado que afirman algo con un `true` literal:\n  " +
                culpables.joinToString("\n  ") +
                "\n\nUna afirmación en un healthCheck tiene que venir de una comprobación, " +
                "no de una constante. Si no se puede comprobar, se quita la clave o se " +
                "pone el valor real con una nota explicando qué falta.",
            culpables.isEmpty()
        )
    }

    @Test
    fun `no quedan dobles exclamaciones sobre resultados nuleables`() {
        // `result.extensionId!!` sobre el valor de retorno de un método que puede
        // devolver null es una NullPointerException esperando su turno. Apareció
        // en ExtensionSandboxModule y habría reventado con el método de instalación
        // por defecto.
        val culpables = mutableListOf<String>()
        raiz.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { archivo ->
                archivo.readLines().forEachIndexed { i, linea ->
                    val t = linea.trim()
                    if (t.startsWith("//") || t.startsWith("*")) return@forEachIndexed
                    // Solo interesan los `!!` sobre cosas que vienen de fuera.
                    if (Regex("""\b(result|resultado|respuesta|pagina|bitmap|file)\.\w+!!""")
                            .containsMatchIn(t)
                    ) {
                        culpables.add("${archivo.name}:${i + 1} → ${t.take(90)}")
                    }
                }
            }
        assertTrue(
            "doble exclamación sobre un valor que puede ser null:\n  " +
                culpables.joinToString("\n  "),
            culpables.isEmpty()
        )
    }
}
