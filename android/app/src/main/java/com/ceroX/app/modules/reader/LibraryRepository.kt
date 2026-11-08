package com.ceroX.app.modules.reader

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LibraryRepository(private val context: Context) {

    private val database: ReaderRoomDatabase = Room.databaseBuilder(
        context.applicationContext,
        ReaderRoomDatabase::class.java,
        "reader_database"
    ).fallbackToDestructiveMigration().build()

    private val dao = database.libraryDao()

    suspend fun addItem(item: UnifiedLibraryItem) {
        dao.insert(item)
    }

    suspend fun getAllItems(): List<UnifiedLibraryItem> {
        return dao.getAll()
    }

    suspend fun getItemById(id: String): UnifiedLibraryItem? {
        return dao.getById(id)
    }

    suspend fun removeItem(id: String) {
        dao.deleteById(id)
    }

    fun addItemAsync(item: UnifiedLibraryItem, scope: CoroutineScope) {
        scope.launch {
            withContext(Dispatchers.IO) {
                dao.insert(item)
            }
        }
    }

    fun getAllItemsAsync(scope: CoroutineScope, callback: (List<UnifiedLibraryItem>) -> Unit) {
        scope.launch {
            val items = withContext(Dispatchers.IO) { dao.getAll() }
            withContext(Dispatchers.Main) {
                callback(items)
            }
        }
    }

    fun close() {
        database.close()
    }
}