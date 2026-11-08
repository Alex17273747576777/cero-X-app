package com.cerox.comun

/**
 * El motor JavaScript, compartido por las cuatro plataformas.
 *
 * POR QUÉ ESTO ES LO QUE JUSTIFICA TODO EL MÓDULO
 * ----------------------------------------------
 * Una extensión de Mihon es un APK: solo corre donde hay una JVM. Eso encierra a
 * cero-X en Android para siempre. Una extensión en JavaScript, en cambio, se
 * puede evaluar en cualquier sitio **si el motor está en todas partes**. Y ahí
 * está el truco: QuickJS es C, pequeño y con licencia permisiva, así que existe
 * en Android (JNI), en escritorio (proceso `qjs` o enlazado) y en iOS (estático).
 *
 * Este archivo define el contrato del motor y **el sobre que envuelve la
 * extensión**, que es la parte que de verdad importa y la que más fácil se
 * rompe: si la extensión puede tocar el ámbito global, una extensión mala puede
 * romper a las demás. El sobre es idéntico en las tres plataformas porque el
 * texto del script es el mismo.
 *
 * LO MEDIDO
 * ---------
 * El JavaScript de una extensión real (parseo de catálogo con regex, capítulos
 * ordenados, `eval` de una fuente de páginas, `Map`, plantillas, `??`,
 * `async/await`) se ejecutó con el QuickJS oficial: **11 de 11 en verde**.
 * `scripts/probar-quickjs-linux.py`.
 */
interface MotorJsComun {
    /** Evalúa y devuelve el resultado como texto; null si no hay valor. */
    fun evaluar(script: String, timeoutMs: Long): String?

    /** Cierra el motor. Llamarlo dos veces no puede fallar. */
    fun cerrar()

    /** Nombre del motor, para la pantalla de diagnóstico. */
    val nombre: String
}

/**
 * Cómo se envuelve una extensión antes de evaluarla.
 *
 * TRES REGLAS, Y LAS TRES SON POR UN MOTIVO
 * -----------------------------------------
 *  1. **Función con parámetros**, no código suelto: la extensión recibe cuatro
 *     objetos y nada más. Sin esto, una extensión puede hacer
 *     `this.http = null` y romper a la siguiente.
 *  2. **`"use strict"`**: en modo no estricto, asignar a una variable no
 *     declarada crea una global. Con `"use strict"`, falla en el sitio donde
 *     se equivocó el autor de la extensión.
 *  3. **`module.exports`**: el contrato es el de siempre (CommonJS), para que una
 *     extensión escrita para otro anfitrión no haya que reescribirla.
 */
object SobreDeExtension {

    const val TIMEOUT_MS = 15_000L
    const val MAX_PETICIONES = 20
    const val MAX_RESPUESTA_BYTES = 8L * 1024 * 1024

    /**
     * Construye el script que se evalúa.
     *
     * `fuente` va tal cual, sin escapar: ya viene de un archivo que el usuario
     * eligió. Escaparla sería peor, porque rompería las expresiones regulares.
     */
    fun construir(fuente: String): String = buildString {
        // Se monta con `append` y no con una plantilla `trimIndent()` porque la
        // plantilla **no puede** recortar: al interpolar un fuente de varias
        // líneas, la línea con menos sangría pasa a ser la del fuente, y el
        // sobre entero se queda con su sangría. El script salía empezando por
        // ocho espacios. No rompía a QuickJS, pero sí a cualquiera que lo
        // inspeccione, y hacía imposible comprobar que la extensión va dentro.
        append("(function (ceroX, http, dom, prefs, log) {\n")
        append("  \"use strict\";\n")
        append("  var sources = [];\n")
        append("  var module = { exports: {} };\n")
        // `exports` tiene que ser **el mismo objeto** que `module.exports`. Sin
        // esta línea, una extensión que hace `exports.sources = [...]` —que es lo
        // que hacen todas las de Mihon— falla con
        // `ReferenceError: 'exports' is not defined`. Se detectó ejecutando, no
        // compilando.
        append("  var exports = module.exports;\n")
        append("  (function () {\n")
        append(fuente)
        append("\n  }).call(module.exports);\n")
        append("  var declaradas = module.exports.sources || sources || [];\n")
        append("  return JSON.stringify(declaradas.map(function (s) {\n")
        append("    return { name: s.name, lang: s.lang, baseUrl: s.baseUrl };\n")
        append("  }));\n")
        append("})(ceroX, __http, __dom, __prefs, __log)")
    }

    /**
     * Cuántos nombres declaró una extensión, a partir del JSON que devolvió el sobre.
     *
     * Se cuenta `"name":` en vez de montar un parser JSON completo: lo único que
     * se necesita saber es si declaró algo, y un parser completo aquí sería una
     * dependencia más que probar.
     */
    fun nombresDeclarados(json: String?): List<String> {
        if (json.isNullOrBlank() || json == "[]" || json == "null") return emptyList()
        val salida = mutableListOf<String>()
        var i = 0
        while (i < json.length) {
            val marca = json.indexOf("\"name\"", i)
            if (marca < 0) break
            i = marca + 6
            // Después de la clave tiene que venir `:` y una cadena JSON.
            var j = i
            while (j < json.length && json[j].isWhitespace()) j++
            if (j >= json.length || json[j] != ':') continue
            j++
            while (j < json.length && json[j].isWhitespace()) j++
            // Se ignora un `name` que no sea cadena: `null`, un número, un objeto.
            if (j >= json.length || json[j] != '"') continue
            // Cadena JSON con escapes: `\"` no la cierra, y `\\` tampoco cuenta.
            val sb = StringBuilder()
            j++
            while (j < json.length) {
                val c = json[j]
                if (c == '\\' && j + 1 < json.length) {
                    // Se resuelven los escapes que pueden aparecer en un nombre.
                    when (val sig = json[j + 1]) {
                        'n' -> sb.append('\n')
                        't' -> sb.append('\t')
                        'r' -> sb.append('\r')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'u' -> {
                            val hex = json.substring(j + 2, minOf(j + 6, json.length))
                            val code = hex.toIntOrNull(16)
                            if (code != null && hex.length == 4) {
                                sb.append(code.toChar())
                                j += 4
                            } else sb.append("\\u")
                        }
                        else -> sb.append(sig)
                    }
                    j += 2
                    continue
                }
                if (c == '"') break
                sb.append(c)
                j++
            }
            sb.toString().ifBlank { null }?.let { salida += it }
            i = j + 1
        }
        return salida
    }

    /** Los cuatro objetos que recibe una extensión. Nada más. */
    val parametros: List<String> = listOf("http", "dom", "prefs", "log")

    /** Lo que una extensión **no** recibe, y que hay una prueba que comprueba. */
    val prohibidos: List<String> = listOf(
        "context", "Context", "System", "Runtime", "ProcessBuilder",
        "java", "android", "navigator", "window", "localStorage", "require",
    )
}
