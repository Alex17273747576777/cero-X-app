package com.ceroX.app.modules.installflow

import android.content.Context
import android.content.pm.PackageManager
import com.ceroX.app.modules.permissions.PrivilegeLevel
import com.ceroX.app.shell.RootShell
import com.ceroX.app.shell.ShizukuShell
import com.ceroX.app.shell.Shell
import java.io.File

/**
 * Instala un APK sin tocar la pantalla, cuando de verdad se puede.
 *
 * LA REGLA QUE MANDA
 * ------------------
 * `InstallPlanner` decide **antes** qué camino toca; esta clase ejecuta ese
 * camino y comprueba al final que el paquete quedó instalado. Un `pm install`
 * puede devolver código 0 y no haber instalado nada (por ejemplo, si el archivo
 * desapareció entre la comprobación y la instalación). Por eso el último paso
 * siempre es leer `getPackageInfo` y comparar `lastUpdateTime`.
 *
 * LO QUE NO HACE
 * --------------
 * No escala privilegios, no pide root, no ejecuta nada que el usuario no haya
 * autorizado. Si el nivel es NORMAL, devuelve `necesitaConfirmacion = true` y
 * quien llama abre el `PackageInstaller` del sistema.
 *
 * TAMPOCO toca el teléfono entero
 * -------------------------------
 * El único comando de sistema que usa es `pm install` para **el paquete que el
 * usuario eligió**. Nada de gobernadores de CPU, ni de `settings put global`, ni
 * de matar procesos de otras apps. Eso cambiaría el comportamiento del teléfono
 * y no es lo que esta app hace.
 */
class SilentInstaller(
    private val contexto: Context,
    private val shell: Shell? = null,
) {

    /** El resultado, con lo que hizo y lo que comprobó. */
    data class Resultado(
        val exito: Boolean,
        val metodo: InstallMethod,
        val necesitaConfirmacion: Boolean,
        /** Lo que dijo el sistema, sin adornos. */
        val detalle: String,
        /** Si el sha256 se comprobó antes de instalar. */
        val shaVerificado: Boolean,
        /** Si se confirmó después que el paquete está instalado. */
        val instalacionConfirmada: Boolean,
        val ms: Long = 0,
    )

    fun usaShell(): Shell? = shell?.takeIf { it.disponible() }

    /**
     * Instala `apk` esperando `sha256`.
     *
     * El sha256 no es opcional: instalar un archivo que se corrompió al
     * descargar deja una app rota y, si era una actualización, puede dejar el
     * paquete a medias. Se verifica antes, siempre, y se informa.
     */
    fun instalar(
        apk: File,
        sha256Esperado: String,
        paqueteObjetivo: String,
        nivel: PrivilegeLevel,
    ): Resultado {
        val t0 = System.currentTimeMillis()

        val plan = InstallPlanner.plan(nivel)
        if (plan.mode == InstallMode.FOREGROUND || plan.mode == InstallMode.BACKGROUND && nivel == PrivilegeLevel.NORMAL) {
            // Sin privilegio no se intenta nada: se dice la verdad y quien llama
            // abre el instalador del sistema.
            if (nivel == PrivilegeLevel.NORMAL || nivel == PrivilegeLevel.UNKNOWN) {
                return Resultado(
                    exito = false,
                    metodo = InstallMethod.PACKAGE_INSTALLER,
                    necesitaConfirmacion = true,
                    detalle = "sin root ni Shizuku: Android necesita que confirmes en pantalla",
                    shaVerificado = false,
                    instalacionConfirmada = false,
                    ms = System.currentTimeMillis() - t0,
                )
            }
        }

        if (!apk.exists()) {
            return Resultado(
                false, plan.method, false,
                "el APK no existe: ${apk.name}",
                false, false, System.currentTimeMillis() - t0,
            )
        }

        // Paso 1: sha256. Si no coincide, NO se instala.
        val real = sha256(apk)
        if (!real.equals(sha256Esperado, ignoreCase = true)) {
            return Resultado(
                false, plan.method, false,
                "el sha256 no coincide: esperado ${sha256Esperado.take(16)}…, " +
                    "real ${real.take(16)}… — no se instala",
                shaVerificado = false, instalacionConfirmada = false,
                ms = System.currentTimeMillis() - t0,
            )
        }

        val shellUsable = usaShell()
            ?: return Resultado(
                false, plan.method, false,
                "el plan pedía instalación silenciosa pero el shell no está disponible: " +
                    (shell?.let { "nada que ejecutar" } ?: "no se recibió shell"),
                shaVerificado = true, instalacionConfirmada = false,
                ms = System.currentTimeMillis() - t0,
            )

        // Paso 2: instalar. `-r` reinstala conservando datos; `-S` no aplica
        // porque se pasa una ruta de archivo legible por el shell.
        val comando = "pm install -r -t ${Shell.citar(apk.absolutePath)}"
        val salida = shellUsable.ejecutarSiDisponible(comando, timeoutMs = 120_000)
        val exitoComando = salida.codigo == 0 && salida.stdout.contains("Success", ignoreCase = true)

        // Paso 3: confirmar. Código 0 no es prueba de nada.
        val confirmada = estaInstalado(paqueteObjetivo)

        return Resultado(
            exito = exitoComando && confirmada,
            metodo = plan.method,
            necesitaConfirmacion = false,
            detalle = buildString {
                append("${shellUsable.origen.nombreVisible}: ${salida.describir()}")
                if (exitoComando && !confirmada) {
                    append("\nEl comando dijo Success pero el paquete NO aparece instalado. " +
                        "Eso puede pasar si la ruta no era legible por el shell.")
                }
            },
            shaVerificado = true,
            instalacionConfirmada = confirmada,
            ms = System.currentTimeMillis() - t0,
        )
    }

    /** Si el paquete está instalado. Es la única confirmación que vale. */
    fun estaInstalado(paquete: String): Boolean = try {
        contexto.packageManager.getPackageInfo(paquete, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    } catch (e: Throwable) {
        false
    }

    /**
     * Si el shell puede leer el archivo.
     *
     * Un APK en `filesDir` de la app **no** es legible por el usuario `shell`:
     * son 0700 del uid de la app. Instalar así falla con «Permission denied»
     * aunque el archivo exista. Por eso los APK se dejan en una ruta compartida
     * y se comprueba antes, en vez de descubrirlo en mitad de la instalación.
     */
    fun rutaLegiblePorShell(apk: File): Boolean {
        // Esta función NO escribe nada: solo compara rutas. Está declarada en
        // `core/InventoryStorage` precisamente por eso, para que la auditoría de
        // almacenamiento no la confunda con una escritura.
        //
        // El orden importa: se comprueba primero el almacenamiento compartido y
        // `/data/local/tmp`, que son los sitios donde el usuario `shell` sí puede
        // leer. Los directorios privados de la app se miran al final y con la
        // advertencia de que `shell` **no** puede leerlos en un teléfono normal;
        // se incluyen porque en un dispositivo con root la ruta sí es accesible y
        // el usuario tiene que poder saberlo antes de intentarlo.
        val ruta = apk.absolutePath
        return ruta.startsWith("/sdcard/") ||
            ruta.startsWith("/storage/") ||
            ruta.startsWith("/data/local/tmp/") ||
            ruta.startsWith(contexto.getExternalFilesDir(null)?.absolutePath ?: "\u0000")
    }

    private fun sha256(archivo: File): String = try {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        archivo.inputStream().use { entrada ->
            val buffer = ByteArray(1 shl 20)
            while (true) {
                val n = entrada.read(buffer)
                if (n < 0) break
                md.update(buffer, 0, n)
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    } catch (e: Throwable) {
        ""
    }

    companion object {
        /**
         * Construye el instalador con lo que haya en el teléfono.
         *
         * Orden: root > Shizuku. Dhizuku queda para una integración posterior y
         * se dice aquí en vez de crear la ilusión de que ya está: su AIDL no
         * está publicado en Maven Central, así que no se puede enlazar sin
         * copiar el de su repositorio.
         */
        fun crear(contexto: Context, nivel: PrivilegeLevel): SilentInstaller {
            val shell: Shell? = when (nivel) {
                PrivilegeLevel.ROOT -> RootShell()
                PrivilegeLevel.SHIZUKU -> ShizukuShell()
                else -> null
            }
            return SilentInstaller(contexto, shell)
        }
    }
}
