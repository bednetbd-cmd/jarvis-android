package com.jarvis.assistant.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        NoteEntity::class,
        TaskEntity::class,
        ReminderEntity::class,
        HabitEntity::class,
        RoutineEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class JarvisDatabase : RoomDatabase() {
    abstract fun productivityDao(): ProductivityDao

    companion object {
        @Volatile
        private var instance: JarvisDatabase? = null

        fun getInstance(context: Context): JarvisDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    JarvisDatabase::class.java,
                    "jarvis_productivity.db"
                ).build().also { instance = it }
            }
        }
    }
}
