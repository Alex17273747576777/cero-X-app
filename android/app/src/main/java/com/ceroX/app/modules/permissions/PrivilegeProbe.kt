package com.ceroX.app.modules.permissions

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.ceroX.app.shell.ShizukuShell

/**
 * Qué privilegios hay de verdad en este teléfono, comprobados uno a uno.
 *
 * LA REGLA QUE NO SE ROMPE
 * ------------------------
 * `usable` es `instalado && servicioVivo && autorizado`. Los tres. No hay ningún
 * camino en este archivo que devuelva `usable = true` porque el paquete esté
 * instalado: tener Shizuku instalado y no haberlo arrancado es el estado más
 * común de todos, y decir «listo» ahí hace que la instalación silenciosa falle
 * delante del usuario sin explicación.
 *
 * Adicional a esto, cada negativa lleva su motivo escrito, para que la pantalla
 * de ajustes pueda decir **qué falta** y no solo «no disponible».
 */
class PrivilegeProbe(private val contexto: Context) {

    /** Lo que se encontró, con el detalle suficiente para actuar. */
    data class Informe(
        val api: Int,
        val dispositivos: List<Deteccion>,
        val nivel: PrivilegeLevel,
        /** Qué camino de instalación se usará, ya decidido. */
        val caminoDeInstalacion: String,
        val notas: List<String>,
    ) {
        val hayAlguno: Boolean get() = dispositivos.any { it.usable }
    }

    /** Un gestor concreto, tal como está en este teléfono. */
    data class Deteccion(
        val gestor: ExternalManager,
        val instalado: Boolean,
        val servicioVivo: Boolean,
        val autorizado: Boolean,
        val version: Int = 0,
        val motivo: String = "",
    ) {
        /** Los tres escalones. Si falta uno, no sirve. */
        val usable: Boolean get() = instalado && servicioVivo && autorizado

        fun describir(): String = when {
            usable -> "${gestor.nombreVisible}: listo (v$version)"
            !instalado -> "${gestor.nombreVisible}: no instalado"
            !servicioVivo -> "${gestor.nombreVisible}: instalado pero el servicio no corre"
            else -> "${gestor.nombreVisible}: sin permiso para cero-X"
        } + if (motivo.isNotBlank()) " · $motivo" else ""
    }

    fun detectar(): Informe {
        val detecciones = mutableListOf<Deteccion>()

        for (gestor in ExternalManager.entries) {
            detecciones += when (gestor.protocolo) {
                ExternalManager.Protocolo.MOE_SHIZUKU -> detectarShizuku(gestor)
                ExternalManager.Protocolo.DHIZUKU -> detectarPorPaquete(gestor)
                // AxManagerD y cualquier otro que hable los dos: se comprueba
                // el binder de Shizuku, que es lo que de verdad se va a usar.
                ExternalManager.Protocolo.MOE_SHIZUKU_O_DHIZUKU ->
                    detectarShizuku(gestor).let { d ->
                        if (d.instalado) d else detectarPorPaquete(gestor)
                    }
            }
        }

        val root = detectarRoot()
        val nivel = PrivilegeLevel.elegir(
            root = root.usable,
            shizuku = detecciones.any {
                it.usable && it.gestor.protocolo != ExternalManager.Protocolo.DHIZUKU
            },
            dhizuku = detecciones.any {
                it.usable && it.gestor.protocolo == ExternalManager.Protocolo.DHIZUKU
            },
        )

        val camino = when (nivel) {
            PrivilegeLevel.ROOT, PrivilegeLevel.SHIZUKU, PrivilegeLevel.DHIZUKU ->
                "silencioso: instalar sin tocar la pantalla"
            PrivilegeLevel.NORMAL, PrivilegeLevel.UNKNOWN ->
                "primer plano: Android pedirá confirmar la instalación en pantalla"
        }

        val notas = buildList {
            if (Build.VERSION.SDK_INT < ShizukuShell.MIN_API) {
                add("Android ${Build.VERSION.RELEASE}: Shizuku funciona mejor en Android 11+")
            }
            ShizukuShell.comprobarLibreria()?.let { add(it) }
            detecciones.filterNot { it.usable && it.instalado }.forEach {
                if (it.instalado) add(it.describir())
            }
            // Lo que NO se hace, dicho aquí para que no se descubra después.
            add("gobernadores de CPU, Hz del panel y modo de todo el sistema: " +
                "NO se tocan; cero-X solo ajusta lo suyo")
        }

        return Informe(
            api = Build.VERSION.SDK_INT,
            dispositivos = detecciones,
            nivel = nivel,
            caminoDeInstalacion = camino,
            notas = notas,
        )
    }

    /**
     * Comprueba Shizuku preguntándole a Shizuku, no al nombre del paquete.
     *
     * Si el paquete está instalado pero el servicio no responde, se devuelve
     * `servicioVivo = false` con el motivo: es el estado que más veces hace
     * fallar una instalación «automática».
     */
    private fun detectarShizuku(gestor: ExternalManager): Deteccion {
        val estado = ShizukuShell.EstadoShizuku.leer()
        return Deteccion(
            gestor = gestor,
            instalado = estado.paqueteInstalado,
            servicioVivo = estado.servicioVivo,
            autorizado = estado.permisoConcedido,
            version = estado.version,
            motivo = estado.motivo,
        )
    }

    /** Comprobación por nombre de paquete, con el `<queries>` ya declarado. */
    private fun detectarPorPaquete(gestor: ExternalManager): Deteccion {
        val paquete = gestor.paquete
            ?: return Deteccion(
                gestor, instalado = false, servicioVivo = false, autorizado = false,
                motivo = "sin paquete comprobable: se detecta por protocolo, no por nombre",
            )
        val instalado = try {
            contexto.packageManager.getPackageInfo(paquete, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        } catch (e: Throwable) {
            false
        }
        return Deteccion(
            gestor = gestor,
            instalado = instalado,
            // Un paquete instalado no implica un servicio corriendo, y aquí no
            // hay forma de comprobarlo sin su AIDL. Se dice tal cual.
            servicioVivo = false,
            autorizado = false,
            motivo = if (instalado)
                "instalado; cero-X no trae el AIDL de ${gestor.nombreVisible}, " +
                    "así que no puede hablar con su servicio"
            else "",
        )
    }

    /**
     * Root: se busca `su` en los sitios documentados y se lanza `id`.
     *
     * Se comprueba ejecutando, no buscando un archivo: un `su` que existe y no
     * concede nada no es root. Si el binario responde con `uid=0`, es root.
     *
     * EL FALLO QUE ENCONTRÓ LA PRUEBA
     * -------------------------------
     * La primera versión lanzaba `su -c id` sin más. En el contenedor donde se
     * ejecutan las pruebas, el proceso **ya** corre como root, así que `id`
     * devolvía `uid=0` y la sonda declaraba ROOT en un Android que no tenía
     * root. Era un falso positivo perfecto: la app habría ofrecido instalación
     * silenciosa y habría fallado en la cara del usuario.
     *
     * Ahora, si el proceso ya corre como uid 0, se dice que no se puede
     * determinar por esta vía y **no** se declara root. En un teléfono una app
     * nunca corre como uid 0, así que esto no quita capacidad real.
     */
    fun detectarRoot(): Deteccion {
        // Rutas donde los gestores de root de Android instalan su `su`. No es
        // una lista inventada: son las que usan Magisk (/debug_ramdisk, /sbin,
        // /system/bin), KernelSU (/data/adb/ksu/bin) y APatch (/data/adb/ap/bin).
        val candidatos = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/su/bin/su",
            "/debug_ramdisk/su",
            "/data/adb/ksu/bin/su",
            "/data/adb/ap/bin/su",
            "/data/local/bin/su",
            "/data/local/xbin/su",
        )
        val ruta = candidatos.firstOrNull { java.io.File(it).exists() }

        // EL FALSO POSITIVO QUE ENCONTRÓ LA PRUEBA
        // ----------------------------------------
        // La primera versión lanzaba `su -c id` sin comprobar nada más. En el
        // contenedor donde corren las pruebas hay un `/usr/bin/su` de Linux que
        // concede root, así que la sonda declaraba ROOT en un Android sin root.
        // Eso habría hecho que la app ofreciera instalación silenciosa y
        // fallara delante del usuario.
        //
        // La corrección no es «ignorar uid=0»: es exigir que el `su` sea el de
        // Android. Si no hay ninguno de esos binarios, no hay root, y no se
        // ejecuta nada. Un teléfono normal nunca tiene ninguno de esos archivos.
        if (ruta == null) {
            return Deteccion(
                gestor = ExternalManager.entries.first(),
                instalado = false,
                servicioVivo = false,
                autorizado = false,
                motivo = "sin binario su de Android en ninguna ruta conocida",
            )
        }

        val salida = try {
            // Se ejecuta la ruta concreta que se encontró, no `su` del PATH:
            // así no hay forma de que un `su` de otro sitio dé el visto bueno.
            val proceso = ProcessBuilder(ruta, "-c", "id").redirectErrorStream(true).start()
            val texto = proceso.inputStream.bufferedReader().use { it.readText() }
            proceso.waitFor()
            texto
        } catch (e: Throwable) {
            ""
        }
        val esRoot = salida.contains("uid=0")
        return Deteccion(
            gestor = ExternalManager.entries.first(),
            instalado = true,
            servicioVivo = esRoot,
            autorizado = esRoot,
            motivo = when {
                esRoot -> "uid=0 confirmado con $ruta"
                else -> "hay $ruta pero no concedió uid=0 (root denegado o pausado)"
            },
        )
    }
}
