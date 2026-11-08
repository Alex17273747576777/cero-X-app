package com.ceroX.app.modules.reader

import android.content.Context
import androidx.room.Room

/**
 * La única puerta a la base de datos de la biblioteca.
 *
 * EL FALLO QUE ARREGLA
 * --------------------
 * Había **dos** constructores de `Room.databaseBuilder` sobre el mismo esquema y
 * con nombres de archivo distintos: `LibraryRepository` abría `reader_database` y
 * `SingleSQLiteConnection` abría `ceroX_database`. Dos archivos, el mismo
 * esquema, ninguno de los dos sabe del otro. Eso no es «una sola conexión»: es
 * una base partida en dos donde lo que se guarda por un camino no aparece por el
 * otro, y en el teléfono ocupa el doble.
 *
 * Aquí queda **un** nombre de archivo y **una** instancia, con doble comprobación
 * para que dos hilos que lleguen a la vez no abran dos conexiones.
 *
 * DÓNDE VIVE, Y POR QUÉ ES DEUDA
 * -----------------------------
 * `Room` guarda el archivo en el almacenamiento interno de la app y acepta una
 * ruta absoluta en `databaseBuilder`, así que se puede mover a
 * `getExternalFilesDir()` cuando se aborde la migración. Hoy sigue en el interno
 * y está declarado como deuda en `core/InventoryStorage`: un archivo con este
 * comentario no cumple la regla, sólo la documenta. Lo que sí se ha hecho aquí es
 * dejar **un** sitio que cambiar en vez de dos.
 */
object BibliotecaDb {

    /** El único nombre de archivo de base de datos de la app. */
    const val NOMBRE = "biblioteca.db"

    @Volatile
    private var instancia: ReaderRoomDatabase? = null

    fun de(contexto: Context): ReaderRoomDatabase {
        instancia?.let { return it }
        return synchronized(this) {
            instancia ?: Room.databaseBuilder(
                contexto.applicationContext,
                ReaderRoomDatabase::class.java,
                NOMBRE,
            )
                // Destructiva a propósito mientras cero-X esté en beta: no hay
                // datos de usuario que preservar entre esquemas. Cuando los haya,
                // esto pasa a migraciones reales.
                .fallbackToDestructiveMigration()
                .build()
                .also { instancia = it }
        }
    }

    /** La instancia ya abierta, sin abrirla si no existe. */
    fun actual(): ReaderRoomDatabase? = instancia

    /** Solo para pruebas: olvida la instancia. */
    fun olvidar() {
        synchronized(this) { instancia = null }
    }
}
