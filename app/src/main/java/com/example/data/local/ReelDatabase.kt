package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ReelEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ReelDatabase : RoomDatabase() {
    abstract fun reelDao(): ReelDao

    companion object {
        @Volatile
        private var INSTANCE: ReelDatabase? = null

        fun getInstance(context: Context): ReelDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ReelDatabase::class.java,
                    "reelcraft_library.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
