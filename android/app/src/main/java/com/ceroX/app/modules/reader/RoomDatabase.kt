package com.ceroX.app.modules.reader

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Delete
import androidx.room.OnConflictStrategy
import androidx.room.TypeConverter

@Database(entities = [UnifiedLibraryItem::class], version = 1)
@TypeConverters(Converters::class)
abstract class ReaderRoomDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao
}

@Dao
interface LibraryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: UnifiedLibraryItem)

    @Query("SELECT * FROM unified_library_item")
    suspend fun getAll(): List<UnifiedLibraryItem>

    @Query("SELECT * FROM unified_library_item WHERE id = :id")
    suspend fun getById(id: String): UnifiedLibraryItem?

    @Delete
    suspend fun delete(item: UnifiedLibraryItem)

    @Query("DELETE FROM unified_library_item WHERE id = :id")
    suspend fun deleteById(id: String)
}

class Converters {
    @TypeConverter
    fun listToString(list: List<String>?): String? {
        return list?.joinToString(",")
    }

    @TypeConverter
    fun stringToList(str: String?): List<String> {
        return str?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
    }
}