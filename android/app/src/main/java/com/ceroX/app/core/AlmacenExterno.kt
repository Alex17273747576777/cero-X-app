package com.ceroX.app.core

import android.content.Context
import android.os.Environment
import java.io.File

/**
 * Dónde van los datos de cero-X cuando no son tokens.
 *
 * LA REGLA QUE IMPLEMENTA
 * -----------------------
 * «Prohibido almacenar cualquier recurso en el almacenamiento **interno**; única
 * excepción: los tokens.»
 *
 * EL DETALLE QUE HAY QUE DECIR SIN ADORNOS
 * ----------------------------------------
 * `Context.getSharedPreferences()` y `Room.databaseBuilder()` con un nombre
 * relativo **siempre** escriben en `/data/data/<paquete>/`, que es almacenamiento
 * interno. No hay bandera que lo cambie. Así que cumplir la regla exige:
 *
 *   1. **No usar preferencias** para los datos de la app: se usa un JSON propio
 *      en el almacenamiento externo de la app ([Prefs]).
 *   2. **Pasar una ruta absoluta** a Room, porque con nombre relativo ignora
 *      cualquier directorio y usa el interno.
 *
 * Este objeto resuelve el directorio una vez y lo crea si no está. Si el
 * almacenamiento externo no está disponible, **no se cae al interno**: se
 * devuelve `null` y quien llama tiene que decidir qué hacer —normalmente,
 * decirlo—.
 *
 * NADA DE PERMISOS
 * ----------------
 * `getExternalFilesDirs()` es el directorio propio de la app dentro del
 * almacenamiento compartido. No necesita ni un permiso, ni `MANAGE_EXTERNAL_STORAGE`,
 * ni tocar archivos de nadie. Es la carpeta de la app, pero **no** es la interna.
 */
object AlmacenExterno {

    /** Subcarpeta propia dentro del directorio externo de la app. */
    const val SUBCARPETA = "datos"

    @Volatile
    private var cache: File? = null

    /**
     * El directorio de datos, o `null` si el almacenamiento externo no se puede
     * usar. Se crea si no existe; no se crea nada fuera de la carpeta de la app.
     */
    fun directorio(contexto: Context): File? {
        cache?.let { return it }
        return synchronized(this) {
            cache ?: runCatching {
                // `getExternalFilesDirs` devuelve varias entradas cuando hay
                // tarjeta SD; se elige la primera que sea escribible de verdad.
                val candidatos = contexto.getExternalFilesDirs(null).filterNotNull()
                val base = candidatos.firstOrNull { it.isDirectory || it.mkdirs() } ?: return null
                val dir = File(base, SUBCARPETA)
                if (!dir.isDirectory && !dir.mkdirs()) return null
                // Comprobación real de escritura: en algunos dispositivos el
                // volumen está montado en solo lectura y `mkdirs` miente.
                val prueba = File(dir, ".escritura")
                if (!prueba.createNewFile() && !prueba.exists()) return null
                prueba.delete()
                dir.also { cache = it }
            }.getOrNull()
        }
    }

    /** Un archivo de datos dentro del directorio externo, o `null` si no hay. */
    fun archivo(contexto: Context, nombre: String): File? =
        directorio(contexto)?.let {
            val f = File(it, nombre)
            f.parentFile?.mkdirs()
            f
        }

    /** Ruta absoluta para Room, o `null` si no hay almacenamiento externo. */
    fun rutaBaseDeDatos(contexto: Context, nombre: String): String? =
        archivo(contexto, nombre)?.absolutePath

    /** Estado legible para la pantalla de diagnóstico. No dice «ok» si no lo está. */
    fun estado(contexto: Context): String {
        val d = directorio(contexto)
            ?: return "SIN almacenamiento externo disponible: no se puede escribir fuera del interno"
        val libre = runCatching { d.usableSpace / (1024L * 1024L) }.getOrDefault(-1)
        val raiz = if (Environment.isExternalStorageEmulated()) "volumen emulado" else "almacenamiento externo real"
        return "${d.absolutePath} ($raiz, $libre MiB libres)"
    }

    /** Solo para pruebas. */
    fun olvidar() {
        synchronized(this) { cache = null }
    }
}
