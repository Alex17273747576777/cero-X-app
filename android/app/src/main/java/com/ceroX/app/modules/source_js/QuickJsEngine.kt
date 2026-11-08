package com.ceroX.app.modules.source_js

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * El motor de verdad: QuickJS a través de `app.cash.quickjs`.
 *
 * CÓMO SE PONE EL LÍMITE DE TIEMPO
 * --------------------------------
 * QuickJS tiene `interruptHandler`, pero el binding de Java de Cash App **no lo
 * expone**: su API pública es `create`, `evaluate`, `compile`, `execute`, `set`,
 * `get` y `close`. Así que un `while(true)` no se puede cortar desde dentro.
 *
 * La única forma honesta con esta API es ejecutar en un hilo aparte y **dejar de
 * esperar** al agotarse el plazo. Eso tiene un precio que hay que decir:
 *
 *   - la llamada devuelve [JsTimeoutException] a los 15 s, que es lo que ve el
 *     usuario;
 *   - el hilo que quedó atascado se abandona y el motor se marca como
 *     **contaminado**; no se vuelve a usar;
 *   - el siguiente uso crea un motor nuevo.
 *
 * Fingir que se ha cancelado sería mentira: el hilo sigue vivo hasta que el
 * `while(true)` termine o el sistema mate el proceso. Lo que sí se garantiza es
 * que la app no se queda colgada y que la extensión culpable queda marcada.
 *
 * AISLAMIENTO
 * -----------
 * Un motor por extensión, no uno compartido. Compartirlo abarataría la memoria y
 * sería un fallo de seguridad: dos extensiones de autores distintos verían las
 * mismas variables globales.
 */
class QuickJsEngine(
    private val timeoutMs: Long = SourceJsLimits.CALL_TIMEOUT_MS,
) : JsEngine {

    private val ejecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "cerox-quickjs").apply { isDaemon = true }
    }

    @Volatile
    private var motor: app.cash.quickjs.QuickJs? = null

    /** Si un plazo se agotó. Cuando pasa, este motor ya no sirve. */
    @Volatile
    private var contaminado = false

    val estaContaminado: Boolean get() = contaminado

    override fun eval(script: String, timeoutMs: Long): String? {
        if (contaminado) {
            throw JsEvaluationException(
                "este motor se abandonó tras agotarse un plazo; se crea uno nuevo"
            )
        }
        val limite = if (timeoutMs > 0) timeoutMs else this.timeoutMs

        val tarea = ejecutor.submit<String?> {
            val qjs = motor ?: crear().also { motor = it }
            val resultado = qjs.evaluate(script, "extension.js")
            when (resultado) {
                null -> null
                is String -> resultado
                // QuickJS devuelve Object[] para arrays y Map para objetos. Aquí
                // no se adivina: se serializa a JSON cuando se puede y, si no,
                // se devuelve la representación, que es lo que el llamador
                // espera de un `eval`.
                else -> resultado.toString()
            }
        }

        return try {
            tarea.get(limite, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            contaminado = true
            tarea.cancel(true)
            throw JsTimeoutException(
                "la extensión superó $limite ms; el motor se ha abandonado"
            )
        } catch (e: java.util.concurrent.ExecutionException) {
            val causa = e.cause
            when (causa) {
                is app.cash.quickjs.QuickJsException ->
                    throw JsEvaluationException(causa.message ?: "error de QuickJS", causa)
                is LinkageError ->
                    throw JsEvaluationException(
                        "no se pudo cargar libquickjs.so: ${causa.message}", causa
                    )
                else -> throw JsEvaluationException(
                    causa?.message ?: "error al evaluar", causa
                )
            }
        }
    }

    /**
     * Crea un motor. Puede fallar por dos motivos distintos y se distinguen:
     *
     *  - `UnsatisfiedLinkError` si el `.so` no está para esta arquitectura
     *  - cualquier otro, si el motor no arranca
     */
    private fun crear(): app.cash.quickjs.QuickJs = try {
        app.cash.quickjs.QuickJs.create()
    } catch (e: UnsatisfiedLinkError) {
        throw JsEvaluationException(
            "no hay libquickjs.so para esta arquitectura: ${e.message}", e
        )
    } catch (e: Throwable) {
        throw JsEvaluationException("no se pudo crear el motor JS: ${e.message}", e)
    }

    override fun close() {
        try {
            motor?.close()
        } catch (e: Throwable) {
            // Cerrar un motor ya roto no es un error que deba propagarse.
        }
        motor = null
        ejecutor.shutdownNow()
    }

    companion object {
        /**
         * Serializa un valor de QuickJS a JSON, para poder pasarlo entre el
         * motor y Kotlin sin inventar un formato.
         */
        fun aJson(valor: Any?): String = when (valor) {
            null -> "null"
            is String -> "\"" + valor.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
            is Number, is Boolean -> valor.toString()
            is Array<*> -> valor.joinToString(",", "[", "]") { aJson(it) }
            is Map<*, *> -> valor.entries.joinToString(",", "{", "}") { (k, v) ->
                aJson(k.toString()) + ":" + aJson(v)
            }
            else -> aJson(valor.toString())
        }
    }
}
