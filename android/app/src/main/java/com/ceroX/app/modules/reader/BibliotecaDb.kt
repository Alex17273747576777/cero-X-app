package com.ceroX.app.modules.reader

import android.content.Context
import androidx.room.Room

/**
 * La única puerta a la base de datos de la biblioteca.
 *
 * EL FALLO QUE ARREGLÓ (primera parte)
 * ------------------------------------
 * Había **dos** constructores de `Room.databaseBuilder` sobre el mismo esquema y
 * con nombres de archivo distintos: `LibraryRepository` abría `reader_database` y
 * `SingleSQLiteConnection` abría `ceroX_database`. Dos archivos, el mismo
 * esquema, ninguno de los dos sabe del otro.
 *
 * LA SEGUNDA PARTE, QUE ES LA IMPORTANTE
 * --------------------------------------
 * Aunque hubiera una sola base, `databaseBuilder(contexto, Clase, "biblioteca.db")`
 * con un **nombre relativo** la crea siempre en `/data/data/<paquete>/databases`,
 * es decir, en el almacenamiento interno. Room ignora cualquier directorio
 * configurado en otro sitio mientras el nombre no lleve ruta.
 *
 * La regla prohíbe guardar recursos en el interno salvo los tokens, así que ahora
 * se pasa una **ruta absoluta** en el almacenamiento externo propio de la app
 * ([com.ceroX.app.core.AlmacenExterno]).
 *
 * MIGRACIÓN
 * ---------
 * Si existe la base vieja en el interno y la nueva no, se **copian los tres
 * archivos** (`-wal` y `-shm` incluidos, o se pierde lo que esté sin volcar) y se
 * borran los del interno. Borrar sin copiar habría sido perder la biblioteca del
 * usuario al actualizar.
 */
object BibliotecaDb {

    /** Nombre del archivo, dentro del almacenamiento externo de la app. */
    const val NOMBRE = "biblioteca.db"

    /** Sufijos que SQLite crea al lado del archivo principal. */
    private val SUFIJOS = listOf("", "-wal", "-shm", "-journal")

    @Volatile
    private var instancia: ReaderRoomDatabase? = null

    fun de(contexto: Context): ReaderRoomDatabase {
        instancia?.let { return it }
        return synchronized(this) {
            instancia ?: crear(contexto).also { instancia = it }
        }
    }

    private fun crear(contexto: Context): ReaderRoomDatabase {
        val ruta = com.ceroX.app.core.AlmacenExterno.rutaBaseDeDatos(contexto, NOMBRE)
            ?: throw IllegalStateException(
                "no hay almacenamiento externo disponible: la biblioteca no se abre en el " +
                    "almacenamiento interno (regla del proyecto)",
            )
        migrarDesdeInterno(contexto, java.io.File(ruta))
        return Room.databaseBuilder(
            contexto.applicationContext,
            ReaderRoomDatabase::class.java,
            ruta,   // ruta ABSOLUTA: con un nombre relativo Room usa el directorio interno
        )
            // Destructiva a propósito mientras cero-X esté en beta: no hay datos de
            // usuario que preservar entre esquemas. Cuando los haya, migraciones reales.
            .fallbackToDestructiveMigration()
            .build()
    }

    /** Copia la base de versiones anteriores y la borra del almacenamiento interno. */
    private fun migrarDesdeInterno(contexto: Context, destino: java.io.File) {
        val viejo = contexto.getDatabasePath(NOMBRE) ?: return
        if (!viejo.isFile || destino.exists()) return
        runCatching {
            destino.parentFile?.mkdirs()
            for (sufijo in SUFIJOS) {
                val origen = java.io.File(viejo.absolutePath + sufijo)
                if (!origen.isFile) continue
                val copia = java.io.File(destino.absolutePath + sufijo)
                copia.writeBytes(origen.readBytes())
                origen.delete()
            }
        }
    }

    /** La instancia ya abierta, sin abrirla si no existe. */
    fun actual(): ReaderRoomDatabase? = instancia

    /** Ruta del archivo, para la pantalla de estado. Null si no hay almacén. */
    fun ruta(contexto: Context): String? =
        com.ceroX.app.core.AlmacenExterno.rutaBaseDeDatos(contexto, NOMBRE)

    /** Solo para pruebas: olvida la instancia. */
    fun olvidar() {
        synchronized(this) { instancia = null }
    }
}
