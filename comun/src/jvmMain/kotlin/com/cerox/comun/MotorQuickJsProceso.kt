package com.cerox.comun

import java.io.File

/**
 * Una extensión JavaScript ejecutada de verdad en el escritorio.
 *
 * POR QUÉ EL PROCESO `qjs` Y NO UNA LIBRERÍA ENLAZADA
 * --------------------------------------------------
 * Hay tres formas de tener JavaScript en la JVM: J2V8 (53 MB por ABI, EPL-1.0),
 * Rhino (1,6 MB, MPL-2.0, sin JIT) y QuickJS por JNI (`quickjs-android`, que es
 * el que usa Mihon). En Android ya está decidido y medido: `QuickJsEngine` usa
 * `app.cash.quickjs`. En **escritorio** la misma librería no existe publicada
 * para la JVM, así que se usa el binario `qjs` oficial del mismo QuickJS: mismo
 * motor, misma versión de ECMAScript, mismo sobre de extensión. La diferencia es
 * sólo cómo se le habla (un proceso en vez de un `long` de puntero).
 *
 * LO QUE ESTO **NO** ES
 * ---------------------
 * No es un intérprete nuevo ni un «sandbox» inventado. `qjs` ejecuta el script
 * en un proceso aparte: cuando termina el plazo se mata el proceso, y ahí sí se
 * puede matar (a diferencia de `quickjs-android`, que no expone
 * `interruptHandler` y deja un hilo colgado). Esa es una ventaja real del
 * escritorio y se dice tal cual.
 *
 * Lo que no hay: la API `ceroX` completa (http/dom/prefs) cableada al proceso.
 * Hoy el escritorio prueba el **motor y el sobre**; la red la pone el anfitrión.
 * Y eso está en la tabla de capacidades de [Plataforma], no en un comentario
 * escondido.
 */
class MotorQuickJsProceso(
    /** Ruta al binario `qjs`. Se busca en el PATH si no se pasa. */
    rutaQjs: String? = System.getenv("CEROX_QJS"),
    private val timeoutPorDefectoMs: Long = SobreDeExtension.TIMEOUT_MS,
) : MotorJsComun {

    private val binario: File? = localizar(rutaQjs)
    private var cerrado = false

    override val nombre: String
        get() = if (binario != null) "QuickJS (qjs ${binario.absolutePath})" else "QuickJS (no encontrado)"

    /** ¿Hay motor? Se pregunta antes de prometer que las extensiones corren. */
    val disponible: Boolean get() = binario != null && binario.canExecute()

    override fun evaluar(script: String, timeoutMs: Long): String? {
        check(!cerrado) { "el motor ya está cerrado" }
        val exe = binario ?: error(
            "no se encontró el binario qjs. Compilar QuickJS (make qjs) y apuntar CEROX_QJS a él."
        )
        val archivo = File.createTempFile("cerox-ext-", ".js")
        try {
            archivo.writeText(script, Charsets.UTF_8)
            val proceso = ProcessBuilder(exe.absolutePath, archivo.absolutePath)
                .redirectErrorStream(false)
                .start()
            val salida = StringBuilder()
            val errores = StringBuilder()
            val lectorSalida = Thread {
                proceso.inputStream.bufferedReader(Charsets.UTF_8).forEachLine { salida.appendLine(it) }
            }.apply { isDaemon = true; start() }
            val lectorErrores = Thread {
                proceso.errorStream.bufferedReader(Charsets.UTF_8).forEachLine { errores.appendLine(it) }
            }.apply { isDaemon = true; start() }

            val terminado = proceso.waitFor(
                (timeoutMs.takeIf { it > 0 } ?: timeoutPorDefectoMs),
                java.util.concurrent.TimeUnit.MILLISECONDS,
            )
            if (!terminado) {
                // Aquí sí se puede matar: es un proceso, no un hilo atascado.
                proceso.destroyForcibly()
                throw IllegalStateException("la extensión pasó de $timeoutPorDefectoMs ms y se mató el proceso")
            }
            lectorSalida.join(1_000)
            lectorErrores.join(1_000)
            if (proceso.exitValue() != 0) {
                val motivo = errores.toString().trim().ifBlank { "código ${proceso.exitValue()}" }
                throw IllegalStateException("qjs falló: $motivo")
            }
            return salida.toString().trim().ifBlank { null }
        } finally {
            archivo.delete()
        }
    }

    override fun cerrar() {
        cerrado = true
    }

    companion object {
        /** Busca `qjs` en la ruta indicada, en `CEROX_QJS` y en el PATH. */
        fun localizar(ruta: String?): File? {
            val candidatos = buildList {
                ruta?.let { add(File(it)) }
                System.getenv("CEROX_QJS")?.let { add(File(it)) }
                add(File("/opt/quickjs/qjs"))
                System.getenv("PATH").orEmpty().split(File.pathSeparator)
                    .filter { it.isNotBlank() }
                    .forEach { add(File(it, "qjs")) }
                    .also { add(File(System.getProperty("user.home"), "qjs")) }
            }
            return candidatos.firstOrNull { it.isFile && it.canExecute() }
        }
    }
}
