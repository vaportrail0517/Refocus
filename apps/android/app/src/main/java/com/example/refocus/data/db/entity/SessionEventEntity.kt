package com.example.refocus.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "session_events")
data class SessionEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val sessionId: Long,
    val type: String,
    val timestampMillis: Long,
)
