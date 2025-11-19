package com.example.refocus.core.model

/**
 * 「やりたいこと」を表すシンプルなモデル。
 * まずはタイトルだけ。将来タグや期限などを足していく想定。
 */
data class Suggestion(
    val title: String,
    val createdAtMillis: Long,
)
