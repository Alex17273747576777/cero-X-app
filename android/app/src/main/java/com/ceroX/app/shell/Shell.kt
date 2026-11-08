package com.ceroX.app.shell

/**
 * Un intérprete de shell, sea cual sea el privilegio que lo respalde.
 *
 * POR QUÉ UNA INTERFAZ Y NO UN `if (shizuku) ... else if (root) ...`
 * ----------------------------------------------------------------
 * Porque los tres caminos (Shizuku, Dhizuku, root) hacen lo mismo —ejecutar un
 * comando y devolver su salida— y meterlos en un `when` dentro del módulo de
 * instalación obligaría a repetir ese `when` en cada sitio que quiera correr
 * algo. Con una interfaz, el módulo de instalación no sabe ni le importa quién
 * le da el shell; lo pide y lo usa.
 *
 * LO QUE NO ES
 * ------------
 * No es una puerta trasera: el `Shell` de usuario **no** existe. Si no hay
 * Shizuku, ni Dhizuku, ni root, esta interfaz no tiene implementación y la app
 * lo dice. No hay un modo «simulado» que devuelva éxito.
 */
interface Shell {

    /** De dónde viene el privilegio, para poder enseñarlo en la pantalla. */
    val origen: Origen

    enum class Origen {
        /** `Shizuku.newProcess`: el gestor de Shizuku, sin root. */
        SHIZUKU,

        /** `Dhizuku` con un proceso remoto: Device Owner / Profile Owner. */
        DHIZUKU,

        /** `su -c`: root de verdad. */
        ROOT;

        val nombreVisible: String
            get() = when (this) {
                SHIZUKU -> "Shizuku"
                DHIZUKU -> "Dhizuku (Device Owner)"
                ROOT -> "root (su)"
            }
    }

    /** Lo que devolvió un comando. `codigo` 0 no siempre significa éxito. */
    data class Salida(val codigo: Int, val stdout: String, val stderr: String, val ms: Long) {
        val exito: Boolean get() = codigo == 0
        val texto: String get() = stdout.ifBlank { stderr }.trim()

        /** Para pintarlo: el código y lo que dijo el comando, sin adornos. */
        fun describir(): String =
            "código $codigo · ${texto.take(300).ifBlank { "sin salida" }}"
    }

    /** ¿Sigue vivo el privilegio? Se comprueba antes de usarlo, no se supone. */
    fun disponible(): Boolean

    /** Ejecuta y espera. Nunca lanza: devuelve la salida o el código de error. */
    fun ejecutar(comando: String, timeoutMs: Long = 30_000): Salida

    /** Igual, pero comprobando que el privilegio está vivo antes. */
    fun ejecutarSiDisponible(comando: String, timeoutMs: Long = 30_000): Salida =
        if (disponible()) ejecutar(comando, timeoutMs)
        else Salida(-1, "", "sin privilegio: ${origen.nombreVisible} no está disponible", 0)

    companion object {
        /**
         * Comillas de shell para un argumento.
         *
         * Sin esto, una ruta con un espacio («/sdcard/Mis Manga/1.cbz») parte el
         * comando en dos y `pm install` recibe una ruta que no existe. Y una
         * ruta con `;` ejecuta un comando distinto del que se quería.
         */
        fun citar(argumento: String): String =
            "'" + argumento.replace("'", "'\\''") + "'"
    }
}
