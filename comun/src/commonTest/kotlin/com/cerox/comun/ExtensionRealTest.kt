package com.cerox.comun

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Extensiones JavaScript reales, con el sobre real, y su resultado comprobado.
 *
 * QUÉ DIFERENCIA ESTO DE LAS PRUEBAS DE MOTORJS DEL MÓDULO ANDROID
 * ---------------------------------------------------------------
 * `MotorJsTest` (en la app) prueba el anfitrión y la **decisión** del motor,
 * porque allí no se puede cargar el `.so` de Android en la JVM de escritorio. Un
 * verde ahí no demuestra que una extensión se ejecute.
 *
 * Esta prueba sí baja al detalle de lo que hace una extensión de verdad: parsea
 * HTML con expresiones regulares, declara fuentes, ordena capítulos, devuelve
 * JSON y usa sintaxis moderna. El script que se construye aquí es **el mismo**
 * que se le pasa a `qjs` en `src/jvmMain/MotorQuickJsProceso.kt` y el mismo que
 * se le pasa a `quickjs-android` en el teléfono, porque ambos llaman a
 * `SobreDeExtension.construir`.
 *
 * La prueba de que ese texto efectivamente **corre** está en
 * `scripts/probar-quickjs-comun.py`, que ejecuta estos mismos casos con el
 * binario `qjs`. Esta prueba comprueba que el texto que se le entrega es el
 * correcto y que el parser de la respuesta no se rompe con acentos.
 */
class ExtensionRealTest {

    /** Una extensión con la forma de las de Mihon: declara fuentes y parsea. */
    private val extensionDeEjemplo = """
        var BASE = "https://ejemplo.test";
        var catalogo = exports.sources = [];
        catalogo.push({ name: "Ejemplo ES", lang: "es", baseUrl: BASE });
        catalogo.push({ name: "Ejemplo Latino", lang: "es-419", baseUrl: BASE + "/latino" });

        // El parseo que hace toda extensión: del HTML a una lista de series.
        exports.parsearCatalogo = function (html) {
          var re = /<a[^>]+title="([^"]+)"[^>]+href="([^"]+)"/g;
          var salida = [];
          var m;
          while ((m = re.exec(html)) !== null) salida.push({ title: m[1], url: m[2] });
          return salida;
        };

        // Y el que ordena capítulos, que es el fallo clásico.
        exports.ordenar = function (nombres) {
          return nombres.slice().sort(function (a, b) {
            return parseInt(a, 10) - parseInt(b, 10);
          });
        };
    """.trimIndent()

    /** El script que se evalúa: sobre + una llamada que imprime el resultado. */
    private fun scriptCon(llamada: String): String =
        SobreDeExtension.construir(extensionDeEjemplo) + "\nconsole.log(" + llamada + ");"

    // ── El sobre ────────────────────────────────────────────────────────────

    @Test
    fun el_sobre_envuelve_la_extension_en_una_funcion_estricta() {
        val s = SobreDeExtension.construir(extensionDeEjemplo)
        assertTrue(s.startsWith("(function (ceroX, http, dom, prefs, log)"), s.take(60))
        assertTrue(s.contains("\"use strict\""))
        assertTrue(s.contains("module.exports"))
        // La extensión tiene que ir dentro, no al lado.
        assertTrue(s.indexOf("var BASE =") > s.indexOf("(function ()"))
    }

    @Test
    fun el_sobre_devuelve_json_con_los_nombres_declarados() {
        val s = SobreDeExtension.construir(extensionDeEjemplo)
        // La última expresión del sobre es el JSON.stringify de las fuentes.
        assertTrue(s.contains("JSON.stringify(declaradas"))
        assertTrue(s.contains("s.name"))
    }

    // ── La sintaxis que usan las extensiones de verdad ──────────────────────

    @Test
    fun la_extension_de_ejemplo_usa_lo_que_el_motor_tiene_que_soportar() {
        // Si alguien «simplifica» el ejemplo hasta que sólo use `var` y `for`,
        // la prueba deja de demostrar nada. Esto lo impide.
        assertTrue(extensionDeEjemplo.contains("/<a[^>]+title="), "falta una expresión regular")
        assertTrue(extensionDeEjemplo.contains("while ("), "falta un bucle")
        assertTrue(extensionDeEjemplo.contains("function ("), "falta una función")
        assertTrue(extensionDeEjemplo.contains("parseInt"), "falta una conversión numérica")
        assertTrue(extensionDeEjemplo.contains(".slice()"), "falta copia de array")
        assertTrue(extensionDeEjemplo.contains("exports."), "falta el contrato CommonJS")
    }

    @Test
    fun el_script_que_se_ejecuta_no_mezcla_la_extension_con_el_sobre() {
        val script = scriptCon("1")
        // Una sola función envolvente: si la extensión se pegara fuera, podría
        // declarar globales y pisar a la siguiente extensión.
        assertEquals(1, Regex("""\(function \(ceroX""").findAll(script).count())
        assertTrue(script.endsWith(");"))
    }

    // ── El parser de la respuesta ───────────────────────────────────────────

    @Test
    fun se_leen_los_nombres_aunque_lleven_acentos_y_comillas() {
        val json = """[{"name":"Ejemplo ES","lang":"es","baseUrl":"https://a"},""" +
            """{"name":"Café y \"comillas\"","lang":"es","baseUrl":"https://b"}]"""
        // Las comillas escapadas (") forman parte del nombre; una implementación
        // que corte en la primera comilla devuelve «Café y » y pierde el resto.
        assertEquals(
            listOf("Ejemplo ES", "Café y \"comillas\""),
            SobreDeExtension.nombresDeclarados(json),
        )
    }

    @Test
    fun un_name_que_no_es_cadena_no_inventa_un_nombre() {
        // `"name": null` y `"name": 3` son válidos en JSON y no son nombres.
        assertTrue(SobreDeExtension.nombresDeclarados("""[{"name":null}]""").isEmpty())
        assertTrue(SobreDeExtension.nombresDeclarados("""[{"name":3}]""").isEmpty())
        assertEquals(
            listOf("Buena"),
            SobreDeExtension.nombresDeclarados("""[{"name":null},{"name":"Buena"}]"""),
        )
    }

    @Test
    fun se_resuelven_los_escapes_unicode_del_nombre() {
        assertEquals(
            listOf("Año"),
            SobreDeExtension.nombresDeclarados("""[{"name":"A\u00f1o"}]"""),
        )
    }

    @Test
    fun una_extension_sin_fuentes_no_se_confunde_con_una_rota() {
        assertTrue(SobreDeExtension.nombresDeclarados("[]").isEmpty())
        assertTrue(SobreDeExtension.nombresDeclarados("null").isEmpty())
        assertTrue(SobreDeExtension.nombresDeclarados("").isEmpty())
        assertEquals(listOf("Una"), SobreDeExtension.nombresDeclarados("""[{"name":"Una"}]"""))
    }

    // ── Lo que el sobre no le da a una extensión ────────────────────────────

    @Test
    fun la_extension_no_puede_pedir_el_contexto_de_android_ni_la_jvm() {
        // Estos nombres son los que rompen el aislamiento si se filtran.
        listOf("Context", "java", "android", "Runtime", "ProcessBuilder", "window").forEach {
            assertTrue(
                it !in SobreDeExtension.parametros,
                "el sobre no puede pasar $it a la extensión",
            )
        }
        assertEquals(listOf("http", "dom", "prefs", "log"), SobreDeExtension.parametros)
    }
}
