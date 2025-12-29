package com.example.refocus.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 一度でも対象にしたアプリの catalog．
 * - 初回に確定した表示名を保持し，アンインストール後も履歴表示が崩れないようにする．
 * - lastKnownLabel は表示名が変化する可能性に備え，フォールバック用として更新可能にする．
 */
@Entity(tableName = "app_catalog")
data class AppCatalogEntity(
    @PrimaryKey
    val packageName: String,
    val firstTargetedAtMillis: Long,
    val firstTargetedLabel: String,
    val lastKnownLabel: String,
    val lastUpdatedAtMillis: Long,
)
