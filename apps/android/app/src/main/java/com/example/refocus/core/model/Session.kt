package com.example.refocus.core.model

import java.time.Instant
import java.time.LocalDate


data class Session(
    val id: Long? = null,
    val packageName: String,
)

data class SessionEvent(
    val id: Long? = null,
    val sessionId: Long,
    val type: SessionEventType,
    val timestampMillis: Long,
)

enum class SessionEventType {
    Start,
    Pause,
    Resume,
    End,
    SuggestionShown,
    SuggestionSnoozed,
    SuggestionDismissed,
    SuggestionDisabledForSession,
}

/**
 * 1 つのセッションを、日付境界（0:00）ごとに分割した「利用切片」。
 *
 * 例:
 *  2025-01-01 23:50 〜 2025-01-02 00:20 のセッションは、
 *   - 2025-01-01 の 23:50〜24:00
 *   - 2025-01-02 の 00:00〜00:20
 * という 2 つの SessionPart に分割される。
 */
data class SessionPart(
    val sessionId: Long,
    val packageName: String,
    // ローカル日付（セグメントが属する「日」）
    val date: LocalDate,
    // 実際の時間（必要なら統計やデバッグで使用）
    val startDateTime: Instant,
    val endDateTime: Instant,
    // その日の 0:00 を 0 とした分数（0..1440）
    val startMinutesOfDay: Int,
    val endMinutesOfDay: Int,
    // このセグメントの継続時間
    val durationMillis: Long,
)
