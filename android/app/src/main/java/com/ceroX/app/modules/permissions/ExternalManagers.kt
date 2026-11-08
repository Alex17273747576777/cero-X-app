package com.ceroX.app.modules.permissions

/**
 * Qué puede hacer cada gestor de paquetes externo, según el propio gestor.
 *
 * El error clásico —y el que tenía cero-X— es asumir que "tener Shizuku
 * instalado" equivale a "poder instalar por Shizuku". Son tres cosas distintas:
 *
 *   1. El paquete existe (está instalado).
 *   2. El servicio está corriendo (el usuario lo arrancó o el arranque
 *      automático lo hizo).
 *   3. cero-X está autorizada (el usuario le dio permiso en Shizuku).
 *
 * Solo con las tres se puede instalar. Si falta una, el nivel no existe y hay
 * que decirlo, no intentarlo y fallar en silencio.
 */
data class ManagerCapability(
    val packageName: String,
    val displayName: String,
    val installed: Boolean,
    val serviceRunning: Boolean,
    val authorized: Boolean,
    val notes: String = "",
) {
    /** Los tres escalones, no dos. */
    val usable: Boolean get() = installed && serviceRunning && authorized

    /** Qué falta exactamente, para poder enseñárselo al usuario. */
    fun missing(): List<String> = buildList {
        if (!installed) add("no está instalado")
        if (installed && !serviceRunning) add("está instalado pero el servicio no corre")
        if (installed && serviceRunning && !authorized) add("corre pero no autorizó a cero-X")
    }

    fun describe(): String =
        if (usable) "$displayName: listo"
        else "$displayName: ${missing().joinToString(", ")}"
}

/**
 * Catálogo de gestores que cero-X reconoce.
 *
 * Se listan por nombre de paquete porque es lo único que no cambia cuando el
 * proyecto cambia de nombre o de repositorio. Las notas dicen lo que se sabe de
 * cada uno sin adornos.
 */
object ExternalManagers {

    /** Shizuku original (RikkaApps). */
    const val SHIZUKU_PKG = "moe.shizuku.privileged.api"

    /** Shizuku forks / variantes conocidas. */
    val SHIZUKU_VARIANTS = listOf(
        "moe.shizuku.privileged.api",
        "moe.shizuku.manager",
        "rikka.shizuku",
    )

    /** Root: no es un paquete, es un binario. Se comprueba aparte. */
    val ROOT_BINARIES = listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/su/bin/su",
        "/magisk/.core/bin/su",
        "/debug_ramdisk/su",
    )

    /**
     * Apps de gestión de paquetes que se pueden usar como *destino* de una
     * instalación, no como fuente de privilegio. Esto importa porque el usuario
     * las nombró y conviene no confundirlas con Shizuku.
     */
    val PACKAGE_MANAGERS = listOf(
        ManagerCapability(
            packageName = "com.mixplorer",
            displayName = "MiXplorer",
            installed = false, serviceRunning = false, authorized = false,
            notes = "Explorador con instalador propio. No da privilegio: instala con confirmación.",
        ),
        ManagerCapability(
            packageName = "eu.kanade.tachiyomi.extension",
            displayName = "Extensión Mihon/Keiyoushi",
            installed = false, serviceRunning = false, authorized = false,
            notes = "Formato de extensión del ecosistema, no un gestor de privilegios.",
        ),
    )

    /**
     * Nombres que suenan a gestor de privilegios pero no se han verificado.
     *
     * Se dejan escritos aquí a propósito: si alguien busca "AxManager" o
     * "OmniShizuku" en el código, encuentra esta nota en vez de suponer que hay
     * soporte. Inventar un nombre de paquete para que la lista parezca completa
     * sería exactamente el tipo de mentira que esta app está quitando.
     */
    val UNVERIFIED_NAMES = listOf("AxManager", "OmniShizuku", "Omni Shizuku")
}
