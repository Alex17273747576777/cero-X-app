package com.ceroX.app.shell

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import moe.shizuku.server.IRemoteProcess
import moe.shizuku.server.IShizukuService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Shell a través del binder de Shizuku, sin root.
 *
 * LA RUTA FÁCIL NO EXISTE, Y ESO SE COMPROBÓ
 * ------------------------------------------
 * El primer intento fue `Shizuku.newProcess(...)`, que es lo que aparece en casi
 * todos los ejemplos de internet. **No compila**: en `dev.rikka.shizuku:api`
 * 13.1.5 ese método es `private`. El compilador lo dijo con todas las letras:
 *
 *     Cannot access 'static fun newProcess(...)': it is private in 'rikka/shizuku/Shizuku'
 *
 * Así que se va por la ruta de verdad, la misma que usan Shizuku, Sui y los dos
 * gestores que pasó el usuario (AxManagerD y OmniShizuku):
 *
 *   1. se obtiene el binder `moe.shizuku.server.IShizukuService`,
 *   2. se llama `newProcess(cmd, env, dir)`, que devuelve un `IRemoteProcess`,
 *   3. se leen sus `ParcelFileDescriptor` de entrada, salida y error.
 *
 * LAS CUATRO INTERFACES AIDL
 * --------------------------
 * Están copiadas tal cual en `app/src/main/aidl/moe/shizuku/server/`, con los
 * mismos números de transacción que el original (`newProcess = 7`, `waitFor`,
 * `exitValue`…). Inventar un número de transacción es la forma más rápida de
 * recibir basura del binder sin un solo error.
 *
 * QUÉ NO ES ESTO
 * --------------
 * No es root. Shizuku corre como uid 2000 (`shell`, los mismos permisos que
 * ADB). Puede ejecutar `pm install`, `am`, `settings`, `cmd`; no puede leer los
 * datos privados de otras apps. Por eso la app dice «Shizuku» y no «root».
 */
class ShizukuShell(
    /** Cómo se comprueba que Shizuku está vivo. Inyectable para poder probar. */
    private val estado: () -> EstadoShizuku = { EstadoShizuku.leer() },
) : Shell {

    override val origen: Shell.Origen = Shell.Origen.SHIZUKU

    /** Lo que se puede saber de Shizuku sin pedir permiso. */
    data class EstadoShizuku(
        val paqueteInstalado: Boolean,
        val servicioVivo: Boolean,
        val permisoConcedido: Boolean,
        val version: Int,
        val motivo: String,
    ) {
        val usable: Boolean get() = paqueteInstalado && servicioVivo && permisoConcedido

        companion object {
            const val PAQUETE_SHIZUKU = "moe.shizuku.privileged.api"

            /**
             * Contexto de aplicación. Lo pone `CeroXApplication` para no tener
             * que arrastrar un `Context` por toda la cadena de privilegios.
             */
            @Volatile
            var contextoGlobal: Context? = null
                set(valor) {
                    field = valor?.applicationContext
                }

            /**
             * Lee el estado real. Ninguno de los tres booleanos se supone: los
             * tres se preguntan, y si la pregunta falla se dice por qué.
             */
            fun leer(): EstadoShizuku {
                val ctx = contextoGlobal
                    ?: return EstadoShizuku(false, false, false, 0, "sin contexto de Android")

                val instalado = try {
                    ctx.packageManager.getPackageInfo(PAQUETE_SHIZUKU, 0) != null
                } catch (e: Throwable) {
                    false
                }
                if (!instalado) {
                    return EstadoShizuku(false, false, false, 0, "Shizuku no está instalado")
                }

                return try {
                    val vivo = rikka.shizuku.Shizuku.pingBinder()
                    val version = rikka.shizuku.Shizuku.getVersion()
                    val permiso = rikka.shizuku.Shizuku.checkSelfPermission() ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                    EstadoShizuku(
                        paqueteInstalado = true,
                        servicioVivo = vivo,
                        permisoConcedido = permiso,
                        version = version,
                        motivo = when {
                            !vivo -> "Shizuku está instalado pero el servicio no corre"
                            !permiso -> "Shizuku corre pero cero-X no está autorizado"
                            else -> ""
                        },
                    )
                } catch (e: LinkageError) {
                    EstadoShizuku(
                        true, false, false, 0,
                        "la librería de Shizuku no está en este APK (${e.javaClass.simpleName})"
                    )
                } catch (e: Throwable) {
                    EstadoShizuku(true, false, false, 0, "${e.javaClass.simpleName}: ${e.message}")
                }
            }

            /** Pide permiso a Shizuku. Solo se puede llamar desde una Activity. */
            fun pedirPermiso(requestCode: Int): Boolean = try {
                rikka.shizuku.Shizuku.requestPermission(requestCode)
                true
            } catch (e: Throwable) {
                false
            }

            /** Si Shizuku muestra el diálogo de permiso (siempre que no esté dado). */
            fun debeMostrarDialogo(): Boolean = try {
                rikka.shizuku.Shizuku.shouldShowRequestPermissionRationale()
            } catch (e: Throwable) {
                false
            }
        }
    }

    override fun disponible(): Boolean = estado().usable

    /**
     * Ejecuta un comando y espera la salida.
     *
     * Se conecta al servicio en cada llamada en vez de mantener una conexión
     * abierta: una conexión permanente a un binder que puede morir (Shizuku se
     * reinicia al reiniciar el teléfono) deja la app con un `IBinder` muerto y
     * sin forma de saberlo. Conectar cuesta milisegundos y siempre dice la
     * verdad sobre si el servicio está ahí.
     */
    override fun ejecutar(comando: String, timeoutMs: Long): Shell.Salida {
        val t0 = System.currentTimeMillis()
        val e = estado()
        if (!e.usable) {
            return Shell.Salida(-1, "", "Shizuku no usable: ${e.motivo}", 0)
        }

        val servicio = conectar(5_000)
            ?: return Shell.Salida(
                -1, "",
                "no se pudo conectar con el servicio de Shizuku (¿el gestor está corriendo?)",
                System.currentTimeMillis() - t0,
            )

        var remoto: IRemoteProcess? = null
        try {
            remoto = servicio.newProcess(arrayOf("sh", "-c", comando), null, null)
                ?: return Shell.Salida(-1, "", "Shizuku devolvió un proceso nulo", 0)

            // Se leen los dos flujos en paralelo. Leer solo uno y esperar es el
            // fallo clásico: si el comando escribe mucho en stderr, el buffer se
            // llena y el proceso se queda bloqueado hasta el timeout.
            val salida = StringBuilder()
            val error = StringBuilder()
            val hiloSalida = hiloLector(remoto.inputStream, salida)
            val hiloError = hiloLector(remoto.errorStream, error)

            val termino = remoto.waitForTimeout(timeoutMs, "ms")
            if (!termino) {
                runCatching { remoto.destroy() }
                return Shell.Salida(
                    -2, salida.toString(),
                    "el comando no terminó en $timeoutMs ms",
                    System.currentTimeMillis() - t0,
                )
            }
            hiloSalida?.join(2_000)
            hiloError?.join(2_000)
            return Shell.Salida(
                remoto.exitValue(),
                salida.toString(),
                error.toString(),
                System.currentTimeMillis() - t0,
            )
        } catch (e: RemoteException) {
            return Shell.Salida(
                -1, "", "el binder de Shizuku rechazó la llamada: ${e.message}",
                System.currentTimeMillis() - t0,
            )
        } catch (e: Throwable) {
            return Shell.Salida(
                -1, "", "${e.javaClass.simpleName}: ${e.message}",
                System.currentTimeMillis() - t0,
            )
        } finally {
            runCatching { remoto?.destroy() }
        }
    }

    /** Envuelve un flujo remoto en un hilo que no puede tumbar la app. */
    private fun hiloLector(descriptor: ParcelFileDescriptor?, destino: StringBuilder): Thread? {
        if (descriptor == null) return null
        return Thread {
            runCatching {
                ParcelFileDescriptor.AutoCloseInputStream(descriptor)
                    .bufferedReader()
                    .forEachLine { destino.appendLine(it) }
            }
        }.also { it.isDaemon = true; it.start() }
    }

    /**
     * Conecta al binder del servicio.
     *
     * `Shizuku.getBinder()` devuelve el binder ya autorizado cuando la app tiene
     * permiso; es el camino que la propia librería expone. Si devuelve null, es
     * que el servicio no está y no se finge lo contrario.
     */
    private fun conectar(timeoutMs: Long): IShizukuService? {
        return try {
            val binder: IBinder? = rikka.shizuku.Shizuku.getBinder()
            if (binder == null || !binder.pingBinder()) return null
            IShizukuService.Stub.asInterface(binder)
        } catch (e: Throwable) {
            null
        }
    }

    companion object {
        const val PAQUETE = EstadoShizuku.PAQUETE_SHIZUKU

        /** Shizuku funciona de forma fiable desde Android 11 (API 30). */
        const val MIN_API = 30

        /** Si la librería está en el APK. Se comprueba al arrancar. */
        fun comprobarLibreria(): String? = try {
            Class.forName("rikka.shizuku.Shizuku")
            null
        } catch (e: Throwable) {
            "la librería de Shizuku no está en este APK: la instalación automática " +
                "usará el camino de primer plano"
        }

        /**
         * Función que la librería de Shizuku pide para saber qué app pide el
         * permiso. Sin esto, `requestPermission` no llega a ninguna parte.
         */
        @Suppress("unused")
        val proveedorDePaquete = rikka.shizuku.ShizukuProvider()
    }
}
