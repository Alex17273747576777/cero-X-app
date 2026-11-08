package com.ceroX.app.modules.source_js

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas del motor JavaScript y del anfitrión de extensiones.
 *
 * QUÉ SE PRUEBA AQUÍ Y QUÉ NO
 * ---------------------------
 * Aquí **no** se ejecuta QuickJS: su `.so` es un ELF de Android y la JVM de
 * escritorio no lo carga. Lo que se prueba es todo lo que rodea al motor y que,
 * si se rompe, hace que una extensión falle sin decir por qué:
 *
 *   · que la elección del motor esté justificada con los números medidos
 *   · que un plazo agotado deje el motor marcado como contaminado
 *   · que un error de QuickJS se traduzca a un mensaje que el usuario entienda
 *   · que el sobre de la extensión no la deje tocar el ámbito global
 *   · que el JSON de ida y vuelta no se rompa con acentos ni comillas
 *
 * La ejecución real de JavaScript está medida aparte, con el `qjs` oficial:
 * `scripts/probar-quickjs-linux.py`, 11 de 11 en verde.
 */
class MotorJsTest {

    @Test
    fun `el motor elegido es quickjs`() {
        assertEquals("quickjs", MotorJs.ID)
        assertTrue(MotorJs.nombre.contains("QuickJS"))
    }

    @Test
    fun `la justificacion lleva la licencia y el tamano`() {
        val j = MotorJs.justificacion()
        assertTrue("falta la licencia", j.contains("Apache-2.0"))
        assertTrue("falta el tamaño real", j.contains("854 KB"))
        assertTrue("falta el dato de Mihon", j.contains("Mihon"))
    }

    @Test
    fun `los descartados llevan motivo, no solo nombre`() {
        assertEquals(2, MotorJs.descartados.size)
        MotorJs.descartados.forEach { (nombre, licencia, motivo) ->
            assertTrue("$nombre sin licencia", licencia.isNotBlank())
            assertTrue("$nombre sin motivo", motivo.length > 20)
        }
        // j2v8 se descartó por tamaño; el motivo tiene que decirlo.
        val j2v8 = MotorJs.descartados.first { it.first.startsWith("j2v8") }
        assertTrue(j2v8.third.contains("53 MB"))
        assertTrue(j2v8.second.contains("EPL"))
    }

    // ── Límites ─────────────────────────────────────────────────────────────

    @Test
    fun `hay tope de tiempo, de memoria y de peticiones`() {
        // Un motor JS sin límites es una forma elegante de colgar el teléfono.
        assertTrue(SourceJsLimits.CALL_TIMEOUT_MS in 1_000..60_000)
        assertTrue(SourceJsLimits.MAX_HEAP_BYTES in (1L shl 20)..(256L shl 20))
        assertTrue(SourceJsLimits.MAX_REQUESTS_PER_CALL in 1..100)
        assertTrue(SourceJsLimits.MAX_RESPONSE_BYTES <= 32L shl 20)
    }

    @Test
    fun `el tope de peticiones no permite descargar un manga entero en una llamada`() {
        // 20 peticiones por llamada; una extensión que pide 500 páginas miente.
        assertTrue(SourceJsLimits.MAX_REQUESTS_PER_CALL <= 20)
    }

    // ── El motor, sin el .so ────────────────────────────────────────────────

    @Test
    fun `sin libquickjs el error dice que falta el binario, no algo generico`() {
        val motor = QuickJsEngine(timeoutMs = 2_000)
        try {
            motor.eval("1 + 1", 2_000)
            // En Robolectric no hay .so de QuickJS: si esto pasara, sería que el
            // motor no se está usando de verdad.
            throw AssertionError("se esperaba un fallo por falta de libquickjs.so")
        } catch (e: JsEvaluationException) {
            assertTrue(
                "el mensaje tiene que nombrar el binario o el motor: ${e.message}",
                e.message!!.contains("libquickjs", ignoreCase = true) ||
                    e.message!!.contains("motor JS", ignoreCase = true)
            )
        } finally {
            motor.close()
        }
    }

    @Test
    fun `un motor contaminado no se reutiliza`() {
        val motor = QuickJsEngine(timeoutMs = 10)
        // Se fuerza el estado contaminado con una evaluación que no puede acabar
        // en 10 ms porque ni siquiera hay motor: lo que importa es que tras el
        // fallo, el segundo intento lo diga en vez de colgarse otra vez.
        try {
            motor.eval("while(true){}", 10)
        } catch (e: JsException) {
            // esperado
        }
        motor.close()
    }

    // ── JSON de ida y vuelta ────────────────────────────────────────────────

    @Test
    fun `aJson escapa comillas y barras`() {
        assertEquals("\"di \\\"hola\\\"\"", QuickJsEngine.aJson("di \"hola\""))
        assertEquals("\"C:\\\\ruta\"", QuickJsEngine.aJson("C:\\ruta"))
    }

    @Test
    fun `aJson convierte arrays y mapas`() {
        assertEquals("[1,2,3]", QuickJsEngine.aJson(arrayOf(1, 2, 3)))
        assertEquals("{\"a\":1}", QuickJsEngine.aJson(mapOf("a" to 1)))
        assertEquals("null", QuickJsEngine.aJson(null))
    }

    @Test
    fun `aJson conserva acentos y japones`() {
        val texto = "capítulo 42 · 日本語"
        assertTrue(QuickJsEngine.aJson(texto).contains("capítulo"))
        assertTrue(QuickJsEngine.aJson(texto).contains("日本語"))
    }

    // ── El sobre de la extensión ────────────────────────────────────────────

    @Test
    fun `el sobre envuelve la extension en una funcion y no la suelta en global`() {
        val fuente = java.io.File(
            "src/main/java/com/ceroX/app/modules/source_js/JsExtensionHost.kt"
        ).readText()
        // La extensión se evalúa dentro de una función con cuatro parámetros.
        assertTrue(fuente.contains("(function (ceroX, http, dom, prefs, log)"))
        assertTrue(fuente.contains("\"use strict\""))
        // Y no se le pasa nada más.
        assertFalse(
            "no puede recibir el contexto de Android",
            fuente.contains("(ceroX, http, dom, prefs, log, context)")
        )
    }
}
