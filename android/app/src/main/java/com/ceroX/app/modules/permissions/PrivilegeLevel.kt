package com.ceroX.app.modules.permissions

/**
 * Nivel de privilegio con el que cero-X puede hacer algo fuera de su propio
 * sandbox.
 *
 * La regla que manda aquí, y que viene de las 14 mentiras corregidas:
 * **un nivel solo existe si su sonda respondió que sí.** Nunca se declara ROOT
 * porque el teléfono "parece rooteado", ni SHIZUKU porque la app está instalada.
 * Instalada no es lo mismo que corriendo, y corriendo no es lo mismo que
 * autorizada.
 */
enum class PrivilegeLevel(
    val id: String,
    /** Si el usuario tiene que hacer algo para que este nivel exista. */
    val requiresUserAction: Boolean,
    /** Qué se puede hacer con este nivel, en una línea. */
    val canDo: String,
) {
    NORMAL(
        id = "normal",
        requiresUserAction = false,
        canDo = "Instalar por PackageInstaller con confirmación del sistema",
    ),
    SHIZUKU(
        id = "shizuku",
        requiresUserAction = true,
        canDo = "Instalar y leer paquetes sin tocar pantalla, si Shizuku corre y autorizó a cero-X",
    ),
    /**
     * Device Owner / Profile Owner vía Dhizuku. Es un nivel propio y no un
     * sinónimo de SHIZUKU: no necesita ADB ni cable, y no da acceso a un shell
     * arbitrario, así que lo que se puede prometer es distinto.
     */
    DHIZUKU(
        id = "dhizuku",
        requiresUserAction = true,
        canDo = "Instalar sin tocar pantalla vía Device Owner, sin ADB ni root",
    ),
    ROOT(
        id = "root",
        requiresUserAction = true,
        canDo = "Instalar en modo silencioso y leer estado del sistema por su",
    ),
    UNKNOWN(
        id = "unknown",
        requiresUserAction = false,
        canDo = "No se pudo determinar: se asume NORMAL",
    );

    companion object {
        fun fromId(id: String): PrivilegeLevel =
            values().firstOrNull { it.id.equals(id, ignoreCase = true) } ?: UNKNOWN

        /**
         * Elige el mejor nivel disponible de los que **respondieron que sí**.
         *
         * Orden: root > Shizuku > Dhizuku > normal. Root primero porque es el
         * único que da shell completo; Shizuku antes que Dhizuku porque es el
         * que más apps tienen montado y el que la gente ya autorizó.
         */
        fun elegir(root: Boolean, shizuku: Boolean, dhizuku: Boolean): PrivilegeLevel = when {
            root -> ROOT
            shizuku -> SHIZUKU
            dhizuku -> DHIZUKU
            else -> NORMAL
        }
    }
}
