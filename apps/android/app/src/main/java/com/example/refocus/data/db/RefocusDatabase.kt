package com.example.refocus.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.refocus.data.db.dao.SessionDao
import com.example.refocus.data.db.entity.SessionEntity
import com.example.refocus.data.db.dao.SessionPauseResumeDao
import com.example.refocus.data.db.entity.SessionPauseResumeEntity

/**
 * Room のメインDB。
 * 今は SessionEntity だけ。将来 SessionPartEntity など追加していく。
 */
@Database(
    entities = [
        SessionEntity::class,
        SessionPauseResumeEntity::class,
    ],
    version = 2,
    exportSchema = true
)
abstract class RefocusDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao
    abstract fun sessionPauseResumeDao(): SessionPauseResumeDao

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
                    .fallbackToDestructiveMigration() // 最初は楽をして、後で適切にマイグレーション
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
