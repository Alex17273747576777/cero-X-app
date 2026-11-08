package com.cerox.comun

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Extensiones JS ejecutadas en QuickJS de verdad, desde la JVM.
 *
 * POR QUÉ ESTA PRUEBA NO ES UN SIMULACRO
 * --------------------------------------
 * Compila el sobre, lo antepone al anfitrión, lo escribe a un archivo y **lanza el
 * binario `qjs`**. Compara la salida impresa por JavaScript con lo que se espera.
 * Si `qjs` no está, la prueba falla con el motivo; no se salta en silencio, porque
 * una prueba que se salta cuando falta lo importante es un verde que no vale.
 *
 * Lo que demuestra, y que ninguna prueba de texto demuestra:
 *
 *   · que el sobre **es** JavaScript válido y evalúa;
 *   · que los cinco objetos del anfitrión existen (sin ellos fallaba con
 *     `ReferenceError: 'ceroX' is not defined`, y eso sólo se veía ejecutando);
 *   · que `module.exports.sources` llega hasta el JSON de salida;
 *   · que una expresión regular con grupos funciona igual que en V8;
 *   · que `http` está deliberadamente cerrado en este anfitrión.
 */
class ExtensionEjecutadaTest {

    private val motor by lazy { MotorQuickJsProceso() }

    private fun ejecutar(extension: String, llamada: String = "SALIDA"): String {
        val m = motor
        if (!m.disponible) fail("no hay binario qjs: compilar QuickJS y definir CEROX_QJS")
        val script = AnfitrionJs.scriptConSalida(extension) + "\nconsole.log(" + llamada + ");"
        return m.evaluar(script, SobreDeExtension.TIMEOUT_MS) ?: fail("qjs no imprimió nada")
    }

    private val extensionSimple = """
        var BASE = "https://ejemplo.test";
        exports.sources = [
          { name: "Ejemplo ES", lang: "es", baseUrl: BASE },
          { name: "Ejemplo Latino", lang: "es-419", baseUrl: BASE + "/latino" }
        ];
        exports.parsear = function (html) {
          var re = /<a[^>]+title="([^"]+)"[^>]+href="([^"]+)"/g, m, salida = [];
          while ((m = re.exec(html)) !== null) salida.push({ title: m[1], url: m[2] });
          return salida;
        };
    """.trimIndent()

    // ── El caso que fallaba ────────────────────────────────────────────────

    @Test
    fun el_anfitrion_define_los_cinco_objetos_que_espera_el_sobre() {
        val salida = ejecutar(extensionSimple)
        // La salida es el JSON de fuentes declaradas que devuelve el sobre.
        assertTrue(salida.startsWith("["), "salida inesperada: $salida")
        assertEquals(
            listOf("Ejemplo ES", "Ejemplo Latino"),
            SobreDeExtension.nombresDeclarados(salida),
        )
    }

    @Test
    fun sin_anfitrion_el_sobre_falla_y_eso_era_el_defecto() {
        // Se ejecuta el sobre **sin** preludio: tiene que fallar por `ceroX`.
        // Si esto pasara, el anfitrión no haría falta y la prueba anterior no
        // estaría demostrando nada.
        val m = motor
        if (!m.disponible) fail("no hay binario qjs")
        val error = runCatching {
            m.evaluar(SobreDeExtension.construir(extensionSimple), SobreDeExtension.TIMEOUT_MS)
        }.exceptionOrNull()
        assertTrue(error != null, "el sobre sin anfitrión no puede funcionar")
        assertTrue(
            error!!.message!!.contains("ceroX") || error.message!!.contains("ReferenceError"),
            "el motivo tiene que señalar la referencia que falta: ${error.message}",
        )
    }

    // ── JavaScript de verdad ───────────────────────────────────────────────

    @Test
    fun una_extension_parsea_html_con_expresiones_regulares_dentro_del_motor() {
        val html = """<div><a title="One Piece" href="/op">x</a><a title="Berserk" href="/be">y</a></div>"""
        // Se ejecuta el parser de la extensión y se devuelve su JSON.
        val script = AnfitrionJs.scriptConSalida(
            extensionSimple + "\nexports.resultado = exports.parsear(" +
                "\"" + html.replace("\"", "\\\"") + "\");",
        ) + "\nconsole.log(declare);"
        // `declare` no existe: lo que se quiere es ejecutar el preludio y el
        // parser; el valor se saca con una segunda llamada más abajo.
        val error = runCatching { motor.evaluar(script, SobreDeExtension.TIMEOUT_MS) }.exceptionOrNull()
        assertTrue(
            error != null && error.message!!.contains("declare"),
            "el parser tiene que haberse ejecutado antes de fallar por lo otro: ${error?.message}",
        )
    }

    @Test
    fun el_parser_de_la_extension_devuelve_las_dos_series() {
        val html = """<a title="One Piece" href="/op">x</a><a title="Berserk" href="/be">y</a>"""
        // El HTML va entre comillas simples: dentro de una plantilla de Kotlin,
        // escapar comillas dobles produce una cadena que no cierra.
        val extension = extensionSimple + "\n" +
            "var s = exports.parsear('" + html + "');\n" +
            "console.log(s.length);\n" +
            "console.log(s[0].title);\n" +
            "console.log(s[1].url);\n"
        val salida = ejecutar(extension)
        val lineas = salida.lines()
        // Las tres primeras líneas son las del parser; la última, el JSON del sobre.
        assertEquals("2", lineas[0])
        assertEquals("One Piece", lineas[1])
        assertEquals("/be", lineas[2])
    }

    @Test
    fun el_motor_soporta_lo_que_usan_las_extensiones() {
        val extension = """
            var partes = [];
            partes.push(typeof Map === "function");
            partes.push(JSON.stringify({ a: "ñ", b: 1 }));
            var m = new Map(); m.set("k", "v");
            partes.push(m.get("k"));
            partes.push([..."abc"].join("-"));
            var f = (x) => x ?? "nulo";
            partes.push(f(null) + "/" + f("valor"));
            console.log(partes.join("|"));
        """.trimIndent()
        val salida = ejecutar(extension)
        val linea = salida.lines().first()
        assertEquals("true|{\"a\":\"ñ\",\"b\":1}|v|a-b-c|nulo/valor", linea)
    }

    @Test
    fun el_sobre_no_deja_a_la_extension_tocar_globales() {
        // `"use strict"` + función envolvente: asignar a una global implícita
        // tiene que fallar en el sitio, no crear una global.
        val extension = """
            (function () { accidental = 1; })();
            console.log("creó la global");
        """.trimIndent()
        val error = runCatching { ejecutar(extension) }.exceptionOrNull()
        assertTrue(error != null, "asignar a una global implícita tiene que fallar con use strict")
        assertTrue(
            error!!.message!!.contains("accidental") || error.message!!.contains("strict"),
            "el motivo tiene que ser la variable: ${error.message}",
        )
    }

    @Test
    fun http_esta_cerrado_a_proposito_en_este_anfitrion() {
        val extension = """http("https://ejemplo.test"); console.log("descargó");"""
        val error = runCatching { ejecutar(extension) }.exceptionOrNull()
        assertTrue(error != null, "http no puede funcionar en un anfitrión sin cliente")
        assertTrue(
            error!!.message!!.contains("http no está disponible"),
            "el motivo tiene que decir por qué: ${error.message}",
        )
    }

    @Test
    fun el_motor_corta_una_extension_que_no_termina() {
        // Un `while (true)` sin salida. En el escritorio se puede matar el
        // proceso; en Android no, y eso está documentado aparte.
        val error = runCatching {
            motor.evaluar("while (true) {}", 300)
        }.exceptionOrNull()
        assertTrue(error != null, "un bucle infinito tiene que cortarse")
        assertTrue(
            error!!.message!!.contains("ms") || error.message!!.contains("mató"),
            "el motivo tiene que hablar del plazo: ${error.message}",
        )
    }
}
