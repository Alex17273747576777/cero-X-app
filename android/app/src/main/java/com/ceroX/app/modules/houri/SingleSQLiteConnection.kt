package com.ceroX.app.modules.houri

import android.content.Context
import androidx.room.Room

/**
 * SingleSQLiteConnection — Conexión única de SQLite vía delegación Metro.
 * Basado en PineappleTwilight/houri single SQLite connection pattern.
 */
object SingleSQLiteConnection {

    private var database: AppDatabase? = null

    fun init(context: Context): AppDatabase {
        if (database == null) {
            database = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "ceroX_database"
            )
                .fallbackToDestructiveMigration()
                .build()
        }
        return database!!
    }

    fun getDatabase(): AppDatabase? = database

    fun close() {
        database?.close()
        database = null
    }
}