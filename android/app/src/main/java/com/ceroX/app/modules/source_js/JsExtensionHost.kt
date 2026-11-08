package com.ceroX.app.modules.source_js

/**
 * Anfitrión de extensiones JavaScript.
 *
 * POR QUÉ EXISTE ESTA CLASE
 * -------------------------
 * La capa 1 (source_api/eu.kanade.tachiyomi) cubre las extensiones JVM del
 * ecosistema Mihon. Eso es un tercio del ecosistema: deja fuera a Mangayomi,
 * AnymeX, Dartotsu y todo el ecosistema Flutter, y sobre todo deja fuera a
 * iOS y a escritorio, porque una extensión APK no corre fuera de la JVM.
 *
 * Esta capa cubre el otro modelo: la extensión es un archivo JavaScript que se
 * evalúa en un motor embebido. La misma extensión corre en Android, iOS y
 * escritorio sin recompilarse.
 *
 * LO QUE ESTA CLASE NO HACE TODAVÍA
 * ---------------------------------
 * No incluye un motor JS. La elección del motor es una decisión con
 * consecuencias —tamaño del APK, licencia, soporte de ARM— y hay tres
 * candidatos viables, todos disponibles:
 *
 *   app.cash.quickjs:quickjs-android   0.9.2   pequeño, MIT, sin JIT
 *   com.eclipsesource.j2v8:j2v8        6.3.4   V8 completo, más peso
 *   org.mozilla:rhino                  1.9.2   sin JNI, el más lento
 *
 * Se deja el punto de integración preparado con la interfaz [JsEngine] para
 * que enchufar cualquiera de los tres sea una clase, no una reescritura.
 */
class JsExtensionHost(
    private val engine: JsEngine,
    private val api: SourceJsApi,
) {

    /** Ejecuta el código de arranque de una extensión y devuelve sus fuentes. */
    fun loadExtension(id: String, source: String): JsExtensionResult {
        val script = buildScript(id, source)
        return try {
            val raw = engine.eval(script, SourceJsLimits.CALL_TIMEOUT_MS)
            val declared = parseDeclaredSources(raw)
            if (declared.isEmpty()) {
                JsExtensionResult.NoSources
            } else {
                JsExtensionResult.Loaded(id, declared)
            }
        } catch (e: JsTimeoutException) {
            // Distinguir timeout de error es lo que permite decirle al usuario
            // «esta extensión tarda demasiado» en vez de «algo falló».
            JsExtensionResult.Failed(id, "La extensión superó ${SourceJsLimits.CALL_TIMEOUT_MS} ms")
        } catch (e: JsEvaluationException) {
            JsExtensionResult.Failed(id, e.message ?: "Error al evaluar la extensión")
        }
    }

    /** Llama a una función de la extensión con argumentos y devuelve el resultado. */
    fun call(extensionId: String, functionName: String, args: String): String? =
        try {
            engine.eval(
                "ceroX.__call(${quote(extensionId)}, ${quote(functionName)}, $args)",
                SourceJsLimits.CALL_TIMEOUT_MS,
            )
        } catch (e: JsException) {
            null
        }

    /**
     * Construye el sobre que se le pasa al motor.
     *
     * La extensión no se evalúa en el ámbito global: se envuelve en una función
     * que recibe `http`, `dom`, `prefs` y `log` como parámetros. Así una
     * extensión no puede tocar el ámbito global del anfitrión aunque quiera.
     */
    private fun buildScript(id: String, source: String): String = """
        (function (ceroX, http, dom, prefs, log) {
          "use strict";
          var EXPORT = null;
          var sources = [];
          var module = { exports: {} };
          // La extensión se evalúa aquí dentro, con los cuatro objetos y nada más.
          (function () {
            $source
          }).call(module.exports);
          var declared = module.exports.sources || sources || [];
          return JSON.stringify(declared.map(function (s) {
            return { name: s.name, lang: s.lang, baseUrl: s.baseUrl };
          }));
        })(ceroX, __http, __dom, __prefs, __log)
    """.trimIndent()

    private fun parseDeclaredSources(raw: String?): List<String> {
        if (raw.isNullOrBlank() || raw == "[]" || raw == "null") return emptyList()
        // Se cuenta por comas de primer nivel en vez de montar un parser JSON
        // completo: lo único que se necesita aquí es saber si declaró algo.
        return raw.split("\"name\":").drop(1).map { it.trim().substringBefore("\"") }
    }

    private fun quote(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}

/** Resultado de cargar una extensión JS. */
sealed class JsExtensionResult {
    data class Loaded(val id: String, val sources: List<String>) : JsExtensionResult()
    data class Failed(val id: String, val reason: String) : JsExtensionResult()
    object NoSources : JsExtensionResult()
}

/** Abstracción del motor. Aquí se enchufa QuickJS, J2V8 o Rhino. */
interface JsEngine {
    fun eval(script: String, timeoutMs: Long): String?
    fun close()
}

class JsException(message: String, cause: Throwable? = null) : Exception(message, cause)
class JsTimeoutException(message: String) : JsException(message)
class JsEvaluationException(message: String, cause: Throwable? = null) : JsException(message, cause)
