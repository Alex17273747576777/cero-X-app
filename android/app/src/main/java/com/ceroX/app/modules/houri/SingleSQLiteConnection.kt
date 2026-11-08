package com.ceroX.app.modules.houri

import android.content.Context
import androidx.room.Room
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

    private var database: ReaderRoomDatabase? = null

    fun init(context: Context): ReaderRoomDatabase {
        val actual = database
        if (actual != null) return actual

        val nueva = Room.databaseBuilder(
            context.applicationContext,
            ReaderRoomDatabase::class.java,
            "ceroX_database",
        )
            // Destructiva a propósito: cero-X está en beta y no hay datos de
            // usuario que preservar entre esquemas. Cuando los haya, esto pasa
            // a migraciones reales.
            .fallbackToDestructiveMigration()
            .build()

        database = nueva
        return nueva
    }

    fun getDatabase(): ReaderRoomDatabase? = database

    fun close() {
        database?.close()
        database = null
    }
}
