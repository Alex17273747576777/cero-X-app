package com.ceroX.app.modules.houri

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [], version = 1)
abstract class AppDatabase : RoomDatabase() {
    // Las entidades se añadirán conforme se implementen los módulos
}