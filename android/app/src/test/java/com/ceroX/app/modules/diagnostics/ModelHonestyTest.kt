package com.ceroX.app.modules.diagnostics

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Ningún módulo puede anunciar un modelo que no esté en el APK.
 *
 * Lección de la primera versión de esta prueba: falló tres veces y las tres eran
 * fallos de la prueba, no de la app.
 *   1. `import ai.onnxruntime.OrtSession` producía el falso positivo "ai.onnx".
 *   2. `MANIFEST.json` tiene dos listas —`modelos` y `descartados`— y comparar
 *      las dos contra el disco marcaba como ausentes los descartados, que
 *      precisamente tienen que faltar.
 *   3. El regex se comía nombres sin guion.
 * Una prueba que acusa en falso es peor que no tenerla: se acaba ignorando.
 *
 * POR QUÉ MIRAR EL FUENTE Y NO EJECUTAR EL MÓDULO
 * -----------------------------------------------
 * Un `healthCheck()` real necesita `SessionOptions` de ONNX Runtime, que no existe
 * en la JVM de pruebas. Mirar el texto cubre el fallo real: que alguien escriba
 * "PP-OCRv6_manga" en un mapa de estado.
 */
class ModelHonestyTest {

    private val raizModulos = File("src/main/java/com/ceroX/app/modules")
    /** El propio guardián nombra los modelos prohibidos a propósito: no se audita. */
    private val ficherosExcluidos = setOf("ModelHonestyGuard.kt")
    private val dirModelos = File("src/main/assets/models")

    private fun onnxEnDisco(): Set<String> =
        dirModelos.listFiles { f -> f.name.endsWith(".onnx") }?.map { it.name }?.toSet() ?: emptySet()

    /** Línea de código, no comentario. Documentar lo descartado está bien. */
    private fun esCodigo(linea: String): Boolean {
        val t = linea.trim()
        if (t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")) return false
        if (t.startsWith("import ")) return false
        return true
    }

    @Test
    fun `hay exactamente cinco modelos y son los verificados`() {
        val onnx = onnxEnDisco()
        assertTrue("se esperaban 5 modelos, hay ${onnx.size}: $onnx", onnx.size == 5)
        listOf("colorize.onnx", "esrgan_x4.onnx", "anime4x.onnx",
               "encoder_fp16.onnx", "decoder_model_quantized.onnx").forEach { esperado ->
            assertTrue("falta $esperado — el código lo usa y reventaría", onnx.contains(esperado))
        }
    }

    @Test
    fun `el manifiesto y el disco dicen lo mismo sobre lo empaquetado`() {
        val enDisco = onnxEnDisco()
        val json = File(dirModelos, "MANIFEST.json").readText()

        // Solo la parte antes de "descartados": esa lista son los que NO van.
        val soloEmpaquetados = json.substringBefore("\"descartados\"")
        val declarados = Regex("\"archivo\"\\s*:\\s*\"([^\"]+)\"")
            .findAll(soloEmpaquetados)
            .map { it.groupValues[1] }
            .toSet()

        assertTrue(
            "el manifiesto promete archivos que no existen: ${declarados - enDisco}",
            (declarados - enDisco).isEmpty()
        )
        assertTrue(
            "hay modelos empaquetados sin declarar: ${enDisco - declarados}",
            (enDisco - declarados).isEmpty()
        )
    }

    @Test
    fun `el manifiesto declara los descartados y no estan en el APK`() {
        val enDisco = onnxEnDisco()
        val json = File(dirModelos, "MANIFEST.json").readText()
        val descartados = Regex("\"archivo\"\\s*:\\s*\"([^\"]+)\"")
            .findAll(json.substringAfter("\"descartados\""))
            .map { it.groupValues[1] }
            .toSet()

        assertTrue("el manifiesto debe documentar los descartados", descartados.isNotEmpty())
        val colados = descartados intersect enDisco
        assertTrue(
            "un modelo descartado no puede estar en el APK: $colados",
            colados.isEmpty()
        )
    }

    @Test
    fun `ningun modulo cita una familia de modelo no empaquetada`() {
        val culpables = mutableListOf<String>()
        raizModulos.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name !in ficherosExcluidos }
            .forEach { archivo ->
                archivo.readLines().forEachIndexed { i, linea ->
                    if (!esCodigo(linea)) return@forEachIndexed
                    // Una clave que empieza por "no_" o "sin_" es un informe de lo
                    // que FALTA (p. ej. "no_empaquetados"). Documentar la ausencia
                    // es justo lo contrario de anunciar una mentira.
                    if (Regex("\"(no_|sin_)[a-z_]*\"\\s*to").containsMatchIn(linea)) return@forEachIndexed
                    ModelHonestyGuard.FAMILIAS_NO_EMPAQUETADAS.forEach { familia ->
                        // Regex con \b, no contains(): "ollama" contiene "llama" y
                        // acusaba en falso a KaiModule. Mismo error de raíz que el
                        // "ai/" dentro de "reikai/" de la primera medición.
                        if (Regex(familia).containsMatchIn(linea)) {
                            culpables.add("${archivo.path.substringAfterLast('/')}:${i + 1} → '$familia'")
                        }
                    }
                }
            }
        assertTrue(
            "anuncian modelos que no están en el APK:\n  " + culpables.joinToString("\n  "),
            culpables.isEmpty()
        )
    }

    @Test
    fun `ningun modulo cita un archivo onnx inexistente`() {
        val enDisco = onnxEnDisco()
        val culpables = mutableListOf<String>()
        raizModulos.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name !in ficherosExcluidos }
            .forEach { archivo ->
                archivo.readLines().forEachIndexed { i, linea ->
                    if (!esCodigo(linea)) return@forEachIndexed
                    Regex("[A-Za-z0-9._-]*_[A-Za-z0-9._-]*\\.onnx").findAll(linea).forEach { m ->
                        if (m.value !in enDisco) {
                            culpables.add("${archivo.path.substringAfterLast('/')}:${i + 1} → '${m.value}'")
                        }
                    }
                }
            }
        assertTrue(
            "citan archivos .onnx que no están en el APK:\n  " + culpables.joinToString("\n  "),
            culpables.isEmpty()
        )
    }
}
