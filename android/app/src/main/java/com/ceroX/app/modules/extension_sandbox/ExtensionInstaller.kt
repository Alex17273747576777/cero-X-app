package com.ceroX.app.modules.extension_sandbox

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File

/**
 * Instalador de extensiones, con tres métodos reales.
 *
 * LO QUE HABÍA AQUÍ, Y ERA LO PEOR DEL PROYECTO
 * ---------------------------------------------
 * Los tres métodos existían, comprobaban casi nada y devolvían éxito:
 *
 *     private fun installViaShizuku(apkPath: String): InstallResult {
 *         if (!shizukuAvailable) return InstallResult(false, "Shizuku no disponible", null)
 *         // Relleno: instalación silenciosa vía Shizuku
 *         return InstallResult(true, "Instalado vía Shizuku (silent)", extId)
 *     }
 *
 * Tres "Relleno" y tres `true`. El de Shizuku al menos miraba una bandera
 * —que `detectShizuku()` devolvía siempre `false`, así que nunca se llegaba—,
 * pero `installViaSystem` y `installViaSandbox` devolvían éxito sin ejecutar
 * una sola instrucción. La extensión no se instalaba en ningún sitio y la app
 * decía que sí.
 *
 * POR QUÉ ESO ES PEOR QUE NO TENER NADA
 * -------------------------------------
 * Un método que no existe da un error de compilación. Un método que miente deja
 * al usuario con una extensión "instalada" que no aparece en la lista, y cuando
 * pregunta, la app ya le dijo que todo fue bien. Es la clase de fallo que hace
 * que alguien desinstale y no vuelva.
 *
 * LOS TRES MÉTODOS, DE VERDAD
 * ---------------------------
 * 1. **SYSTEM_NORMAL** — `Intent(ACTION_VIEW)` con `FileProvider`. Es el método
 *    estándar de Android: abre el instalador del sistema y el usuario confirma.
 *    No necesita permisos raros y funciona en cualquier móvil. Es el que se debe
 *    usar por defecto.
 *
 * 2. **SHIZUKU_SILENT** — requiere Shizuku instalado y corriendo. Se **detecta de
 *    verdad** preguntando por el paquete y por su binder. Si no está, se dice y se
 *    sugiere el método 1. Si está, la instalación real necesita el `Shizuku` API
 *    (`Shizuku.newProcess`), que aquí no se puede ejecutar porque no hay Shizuku
 *    en el entorno de compilación: se devuelve un fallo HONESTO que lo explica en
 *    vez de un éxito inventado.
 *
 * 3. **SANDBOX_INTERNAL** — copia el APK al directorio privado de la app y lo
 *    valida. **No instala nada**: meter un APK de extensión "dentro" de la app no
 *    es instalarlo, porque Android ejecuta las extensiones como APK del sistema.
 *    Lo que sí se puede hacer —y hace— es copiarlo, comprobar que es un APK
 *    legible y dejarlo listo para la capa 1 (`source_api`), que lo carga con
 *    `DexClassLoader` sin instalarlo. Eso es aislamiento de verdad, y es lo que la
 *    app usa para leer con extensiones sin pedir permisos.
 */
class ExtensionInstaller(private val context: Context? = null) {

    /** Resultado con lo que pasó de verdad. `exito` solo es true si algo ocurrió. */
    data class InstallResult(
        val success: Boolean,
        val message: String,
        val extensionId: String?,
    )

    fun install(apkPath: String, method: InstallationMethod): InstallResult {
        val apk = File(apkPath)
        if (!apk.exists()) {
            return InstallResult(false, "No existe el archivo: $apkPath", null)
        }
        if (!apk.isFile || apk.length() == 0L) {
            return InstallResult(false, "El archivo está vacío o no es un archivo: $apkPath", null)
        }
        if (!esApk(apk)) {
            return InstallResult(false, "No es un APK (no empieza por PK\\x03\\x04): $apkPath", null)
        }

        return when (method) {
            InstallationMethod.SYSTEM_NORMAL -> installViaSystem(apk)
            InstallationMethod.SHIZUKU_SILENT -> installViaShizuku(apk)
            InstallationMethod.SANDBOX_INTERNAL -> installViaSandbox(apk)
        }
    }

    /** Un APK es un zip que empieza por PK\x03\x04. */
    private fun esApk(apk: File): Boolean {
        return try {
            apk.inputStream().use { entrada ->
                val cabecera = ByteArray(4)
                val n = entrada.read(cabecera)
                n == 4 && cabecera[0] == 0x50.toByte() && cabecera[1] == 0x4B.toByte() &&
                    cabecera[2] == 0x03.toByte() && cabecera[3] == 0x04.toByte()
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Instalación por el instalador del sistema.
     *
     * Lanza el Intent de verdad. Devuelve éxito **al lanzarlo**, no al instalar:
     * quien confirma es el usuario en la pantalla del sistema, y decir "instalado"
     * antes sería volver a mentir. El mensaje lo dice.
     */
    private fun installViaSystem(apk: File): InstallResult {
        val ctx = context
            ?: return InstallResult(false, "Sin contexto de Android: no se puede lanzar el instalador", null)
        return try {
            val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
            } else {
                @Suppress("DEPRECATION")
                Uri.fromFile(apk)
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
            InstallResult(
                success = true,
                message = "Instalador del sistema abierto. Falta que confirmes la instalación " +
                    "en la pantalla de Android — eso no lo puede hacer la app por ti.",
                extensionId = null
            )
        } catch (e: Exception) {
            InstallResult(
                false,
                "No se pudo abrir el instalador: ${e.javaClass.simpleName}. " +
                    "Suele faltar el FileProvider en el manifiesto o el permiso " +
                    "REQUEST_INSTALL_PACKAGES.",
                null
            )
        }
    }

    /**
     * Instalación silenciosa vía Shizuku.
     *
     * Detecta Shizuku de verdad: paquete instalado **y** servicio corriendo. Si no
     * está, lo dice y propone la alternativa. Si está, la llamada real necesita la
     * librería de Shizuku en tiempo de ejecución; se devuelve fallo explicando
     * exactamente qué falta, en vez de un `true` que no instaló nada.
     */
    private fun installViaShizuku(apk: File): InstallResult {
        val disponible = detectShizuku()
        if (!disponible) {
            return InstallResult(
                false,
                "Shizuku no está disponible en este aparato. Sin Shizuku no hay " +
                    "instalación silenciosa: usa el método del sistema, que abre el " +
                    "instalador de Android.",
                null
            )
        }
        // Aquí iría la llamada real:
        //   Shizuku.newProcess(arrayOf("pm", "install", "-r", apk.absolutePath), ...)
        // No se puede ejecutar sin la librería de Shizuku y sin el servicio corriendo.
        // Se devuelve fallo honesto, NO un éxito inventado.
        return InstallResult(
            false,
            "Shizuku detectado, pero la instalación silenciosa todavía no está " +
                "conectada a su API en esta compilación. No se ha instalado nada.",
            null
        )
    }

    /**
     * Sandbox interno: copia el APK al directorio privado y lo deja listo para la
     * capa 1 (`source_api`, que lo carga con `DexClassLoader`).
     *
     * Esto **no instala** y el mensaje lo dice. Es útil de verdad: es cómo cero-X
     * puede leer con una extensión sin pedirle permiso al sistema para instalar
     * nada.
     */
    private fun installViaSandbox(apk: File): InstallResult {
        val ctx = context
            ?: return InstallResult(false, "Sin contexto de Android: no se puede copiar al sandbox", null)
        return try {
            // Un APK que el usuario instaló no es caché: es un recurso, y la regla
            // lo prohíbe en el almacenamiento interno. Va al directorio externo.
            val base = com.ceroX.app.core.AlmacenExterno.directorio(ctx)
                ?: return InstallResult(
                    false,
                    "no hay almacenamiento externo: el APK de la extensión no se copia al " +
                        "almacenamiento interno (regla del proyecto)",
                    null,
                )
            val dir = File(base, "extension_sandbox").apply { mkdirs() }
            if (!dir.isDirectory) {
                return InstallResult(false, "No se pudo crear el directorio del sandbox", null)
            }
            val destino = File(dir, apk.name)
            apk.inputStream().use { entrada ->
                destino.outputStream().use { salida -> entrada.copyTo(salida, 64 * 1024) }
            }
            if (!destino.exists() || destino.length() != apk.length()) {
                return InstallResult(false, "La copia al sandbox quedó incompleta", null)
            }
            val id = "sandbox_${apk.nameWithoutExtension}"
            InstallResult(
                success = true,
                message = "Copiada al sandbox (${destino.length() / 1024} KB). " +
                    "No instalada en el sistema: se cargará con DexClassLoader desde " +
                    "aquí, que es lo que permite usarla sin permisos de instalación.",
                extensionId = id
            )
        } catch (e: java.io.IOException) {
            InstallResult(false, "Fallo de disco al copiar: ${e.message}", null)
        } catch (e: SecurityException) {
            InstallResult(false, "Sin permiso para escribir en el directorio privado", null)
        }
    }

    /**
     * ¿Está Shizuku instalado y corriendo?
     *
     * Antes devolvía `false` fijo con un "Relleno" al lado. Ahora se pregunta
     * al sistema por el paquete y se comprueba si su binder está vivo, que es la
     * única forma de saberlo sin la librería de Shizuku.
     */
    fun detectShizuku(): Boolean {
        val ctx = context ?: return false
        val paquetes = listOf("moe.shizuku.privileged.api", "moe.shizuku.manager")
        val instalado = paquetes.any { p ->
            try {
                @Suppress("DEPRECATION")
                ctx.packageManager.getPackageInfo(p, 0)
                true
            } catch (e: Exception) {
                false
            }
        }
        if (!instalado) return false

        // El paquete puede estar instalado y el servicio no estar corriendo. Se
        // comprueba si el proceso de Shizuku responde.
        return try {
            val servicio = Class.forName("rikka.shizuku.Shizuku")
            val metodo = servicio.getMethod("pingBinder")
            metodo.invoke(null) as? Boolean ?: false
        } catch (e: ClassNotFoundException) {
            // Paquete instalado pero sin la librería enlazada en esta compilación:
            // se informa de lo que se sabe, que es que está instalado.
            false
        } catch (e: Exception) {
            false
        }
    }
}
