package com.example.refocus.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * セッションの中断/再開イベントを保持するテーブル。
 */
@Entity(tableName = "session_pause_resume_events")
data class SessionPauseResumeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val sessionId: Long,
    val pausedAtMillis: Long,
    val resumedAtMillis: Long? = null,
)
