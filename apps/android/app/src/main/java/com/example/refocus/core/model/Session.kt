package com.example.refocus.core.model

/**
 * 対象アプリの「連続使用」ひとまとまりを表すドメインモデル。
 *
 * - startedAtMillis / endedAtMillis は System.currentTimeMillis() 基準。
 * - id は Room 導入後に自動採番される想定（導入前は null でもよい）。
 */
data class Session(
    val id: Long? = null,
    val packageName: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long? = null,
    val durationMillis: Long? = null,
)
