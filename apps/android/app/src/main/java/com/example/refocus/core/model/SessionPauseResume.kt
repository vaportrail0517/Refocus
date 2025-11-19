package com.example.refocus.core.model

/**
 * 1つのセッション内の「中断→再開」ペアを表すモデル。
 *
 * - sessionId: 紐づく Session の ID
 * - pausedAtMillis, resumedAtMillis は System.currentTimeMillis() 基準
 * - resumedAtMillis が null の場合、「中断したままセッション終了」などを表現できる
 */
data class SessionPauseResume(
    val id: Long? = null,
    val sessionId: Long,
    val pausedAtMillis: Long,
    val resumedAtMillis: Long? = null,
)
