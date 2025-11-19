package com.example.refocus.core.model

/**
 * 日付跨ぎを扱いやすくするための「日別パート」。
 *
 * 1つの Session を、日付ごとに分割したもの。
 * 日別の集計は基本的にこちらを使う。
 */
data class SessionPart(
    val id: Long? = null,
    val sessionId: Long,
    val dateEpochDay: Long,      // LocalDate.toEpochDay() 相当
    val startedAtMillis: Long,
    val endedAtMillis: Long,
)
