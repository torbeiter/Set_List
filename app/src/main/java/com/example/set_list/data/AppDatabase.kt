package com.example.set_list.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [DrumKit::class, Setlist::class, Song::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun drumKitDao(): DrumKitDao
    abstract fun setlistDao(): SetlistDao
    abstract fun songDao(): SongDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // No schema changes, empty migration
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "setlist_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .addCallback(DatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // Initialize default drum kits when database is created
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        populateDatabase(database.drumKitDao())
                    }
                }
            }
        }

        /**
         * Pre-populate the database with 200 Roland V71 drum kits
         */
        private suspend fun populateDatabase(drumKitDao: DrumKitDao) {
            // Roland V71 has 200 kit slots (1-200)
            val defaultKits = (1..200).map { kitNum ->
                DrumKit(
                    kitNumber = kitNum,
                    name = "Kit $kitNum",
                    customName = null,
                    isFavorite = false
                )
            }
            drumKitDao.insertKits(defaultKits)
        }
    }
}