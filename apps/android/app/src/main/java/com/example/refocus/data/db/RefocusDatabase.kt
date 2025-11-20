package com.example.refocus.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.refocus.data.db.dao.SessionDao
import com.example.refocus.data.db.dao.SessionEventDao
import com.example.refocus.data.db.entity.SessionEntity
import com.example.refocus.data.db.entity.SessionEventEntity

@Database(
    entities = [
        SessionEntity::class,
        SessionEventEntity::class,
    ],
    version = 2,
    exportSchema = true
)
abstract class RefocusDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao
    abstract fun sessionEventDao(): SessionEventDao

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
                    .fallbackToDestructiveMigration(false)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
