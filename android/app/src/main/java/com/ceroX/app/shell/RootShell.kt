package com.ceroX.app.shell

import java.util.concurrent.TimeUnit

/**
 * Shell por `su`, para teléfonos con root.
 *
 * QUÉ ES DISTINTO DE SHIZUKU
 * --------------------------
 * Root es uid 0: puede todo. Por eso este `Shell` **solo** se usa cuando el
 * usuario ya tenía root de antes y `su` le contestó `uid=0`. La app no pide root,
 * no lo escala y no lo intenta: si no hay, `disponible()` es false y el plan de
 * instalación se queda en primer plano.
 *
 * POR QUÉ `su -c` Y NO `sh -c`
 * ----------------------------
 * Porque `sh` sin `su` es el usuario de la app y no instala nada. El comando se
 * pasa como un solo argumento entre comillas para que `su` lo entregue entero al
 * shell de root, en vez de interpretarlo él.
 */
class RootShell(
    private val binario: String = "su",
) : Shell {

    override val origen: Shell.Origen = Shell.Origen.ROOT

    /** Se comprueba ejecutando `id`: un `su` que existe y no concede no es root. */
    override fun disponible(): Boolean = ejecutar("id", 5_000).stdout.contains("uid=0")

    override fun ejecutar(comando: String, timeoutMs: Long): Shell.Salida {
        val t0 = System.currentTimeMillis()
        return try {
            val proceso = ProcessBuilder(binario, "-c", comando).start()
            val salida = StringBuilder()
            val error = StringBuilder()
            val hiloSalida = Thread {
                runCatching { proceso.inputStream.bufferedReader().forEachLine { salida.appendLine(it) } }
            }
            val hiloError = Thread {
                runCatching { proceso.errorStream.bufferedReader().forEachLine { error.appendLine(it) } }
            }
            hiloSalida.start(); hiloError.start()
            if (!proceso.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                proceso.destroyForcibly()
                return Shell.Salida(
                    -2, salida.toString(), "el comando no terminó en $timeoutMs ms",
                    System.currentTimeMillis() - t0,
                )
            }
            hiloSalida.join(2_000); hiloError.join(2_000)
            Shell.Salida(proceso.exitValue(), salida.toString(), error.toString(), System.currentTimeMillis() - t0)
        } catch (e: Throwable) {
            // `IOException: Cannot run program "su"` es lo normal en un teléfono
            // sin root, y no es un error de la app: es la respuesta correcta.
            Shell.Salida(-1, "", "sin root: ${e.message}", System.currentTimeMillis() - t0)
        }
    }
}
