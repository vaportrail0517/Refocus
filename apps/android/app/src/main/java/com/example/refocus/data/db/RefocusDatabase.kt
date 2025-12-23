package com.example.refocus.data.db


import androidx.room.Database
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
    version = REFOCUS_DB_VERSION,
    exportSchema = true
)
abstract class RefocusDatabase : RoomDatabase() {

    abstract fun timelineEventDao(): TimelineEventDao
    abstract fun suggestionDao(): SuggestionDao
}
