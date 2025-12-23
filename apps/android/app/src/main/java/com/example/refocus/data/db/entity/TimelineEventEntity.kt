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

    // 任意の追加情報（例: 対象アプリ一覧をカンマ区切りで保存する等）
    val extra: String? = null,

    // key/value 形式の追加情報（例: 設定変更）
    // 以前は extra に "key=value" のように埋め込んでいたが，
    // 将来の拡張や解析を容易にするために正規化して保存する．
    val extraKey: String? = null,
    val extraValue: String? = null,
)
