package com.example.refocus.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.refocus.data.db.dao.SessionDao
import com.example.refocus.data.db.dao.SessionEventDao
import com.example.refocus.data.db.dao.SuggestionDao
import com.example.refocus.data.db.entity.SessionEntity
import com.example.refocus.data.db.entity.SessionEventEntity
import com.example.refocus.data.db.entity.SuggestionEntity

@Database(
    entities = [
        SessionEntity::class,
        SessionEventEntity::class,
        SuggestionEntity::class,
    ],
    version = 3,
    exportSchema = true
)
abstract class RefocusDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao
    abstract fun sessionEventDao(): SessionEventDao
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
