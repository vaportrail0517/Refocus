package com.example.refocus.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room に保存するセッション1件分。
 * ドメインモデル core/model/Session と 1:1 対応させる想定。
 */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val packageName: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long?, // null の間は「進行中」
    val durationMillis: Long? = null,
)
