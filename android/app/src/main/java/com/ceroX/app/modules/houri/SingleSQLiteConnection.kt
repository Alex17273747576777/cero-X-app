package com.ceroX.app.modules.houri

import android.content.Context
import com.ceroX.app.modules.reader.BibliotecaDb
import com.ceroX.app.modules.reader.ReaderRoomDatabase

/**
 * SingleSQLiteConnection — una sola conexión a SQLite para toda la app.
 *
 * POR QUÉ APUNTABA A UNA CLASE QUE NO EXISTE
 * ------------------------------------------
 * Este archivo abría `AppDatabase`, la base de datos de houri. Esa base se
 * eliminó del módulo houri cuando Room se aparcó para poder compilar, y el
 * archivo quedó apuntando a una clase fantasma: ocho errores de compilación
 * por una referencia que ya no existía.
 *
 * La única base Room real que tiene cero-X es ReaderRoomDatabase, la de la
 * biblioteca unificada. Esa es la que se abre aquí. Si algún día houri
 * necesita su propio esquema, será una entidad más en esa misma base —que es
 * justo lo que significa «conexión única»— y no una segunda base de datos.
 */
object SingleSQLiteConnection {

    /**
     * Devuelve la base de la biblioteca.
     *
     * Ya no construye nada: delega en `BibliotecaDb`. Antes había aquí un
     * segundo `Room.databaseBuilder` con el nombre `ceroX_database`, es decir,
     * dos archivos con el mismo esquema. Mantener el objeto es a propósito para
     * no romper a quien lo llame, pero ahora hay una sola conexión de verdad.
     */
    fun init(context: Context): ReaderRoomDatabase = BibliotecaDb.de(context)

    /** La base ya abierta, o `null` si nadie la pidió todavía. */
    fun getDatabase(): ReaderRoomDatabase? = BibliotecaDb.actual()
}
