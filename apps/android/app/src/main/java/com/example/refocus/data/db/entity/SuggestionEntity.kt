package com.example.refocus.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "suggestions")
data class SuggestionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val createdAtMillis: Long,
    val kind: String,
    val timeSlots: String,
    val durationTag: String,
    val priority: String,
)
