package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ApiKeyEntity::class,
        DubbingProjectEntity::class,
        CharacterVoiceEntity::class,
        DialogueSegmentEntity::class,
        ExportHistoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun apiKeyDao(): ApiKeyDao
    abstract fun projectDao(): ProjectDao
    abstract fun characterDao(): CharacterDao
    abstract fun segmentDao(): SegmentDao
    abstract fun exportHistoryDao(): ExportHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "khmer_dubber_pro.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
