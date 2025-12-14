package com.example.refocus.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * TimelineEvent の永続化形。
 *
 * - kind: イベント種別を表す文字列
 * - 以下のカラムは種別に応じて使う（使わないカラムは null）
 */
@Entity(
    tableName = "timeline_events",
    indices = [
        Index(value = ["timestampMillis"]),
        Index(value = ["kind", "timestampMillis"]),
        Index(value = ["packageName", "timestampMillis"]),
    ]
)
data class TimelineEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val timestampMillis: Long,
    val kind: String,

    // 共通でよく使う属性
    val packageName: String? = null,

    // Suggestion 関連
    val suggestionId: Long? = null,
    val suggestionDecision: String? = null,

    // Service / Permission / Screen 状態
    val serviceState: String? = null,
    val permissionKind: String? = null,
    val permissionState: String? = null,
    val screenState: String? = null,

    // その他の情報（設定変更内容など）は文字列でざっくり保持
    val extra: String? = null,
)
