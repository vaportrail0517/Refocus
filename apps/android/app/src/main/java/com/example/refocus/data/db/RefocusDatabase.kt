package com.example.refocus.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.refocus.data.db.dao.SuggestionDao
import com.example.refocus.data.db.dao.TimelineEventDao
import com.example.refocus.data.db.entity.SuggestionEntity
import com.example.refocus.data.db.entity.TimelineEventEntity

@Database(
    entities = [
        TimelineEventEntity::class,
        SuggestionEntity::class,
    ],
    version = 7,
    exportSchema = true
)
abstract class RefocusDatabase : RoomDatabase() {

    abstract fun timelineEventDao(): TimelineEventDao
    abstract fun suggestionDao(): SuggestionDao

    companion object {
        @Volatile
        private var INSTANCE: RefocusDatabase? = null

        fun getInstance(context: Context): RefocusDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RefocusDatabase::class.java,
                    "refocus.db"
                )
                    .fallbackToDestructiveMigration(true) // デバッグ用，以前のDBを上書き
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
