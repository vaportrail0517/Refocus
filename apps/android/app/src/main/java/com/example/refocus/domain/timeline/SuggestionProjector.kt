package com.example.refocus.domain.timeline

import com.example.refocus.core.model.Session
import com.example.refocus.core.model.SessionEvent
import com.example.refocus.core.model.SuggestionDailyStats
import com.example.refocus.domain.stats.SuggestionStatsCalculator
import java.time.LocalDate
import java.time.ZoneId

/**
 * セッション＋イベント列から「その日の提案の出方/受け止め方」を集計する Projector。
 *
 * 実際の集計ロジックは SuggestionStatsCalculator に委譲する。
 */
object SuggestionProjector {
    /**
     * 指定した日付について SuggestionDailyStats を構築する。
     *
     * @param sessions      全セッション
     * @param eventsBySessionId  sessionId -> イベント一覧
     * @param targetDate    集計対象日（ローカル日付）
     * @param zoneId        タイムゾーン
     * @param endSoonThresholdMillis 「そろそろ終わりそう」と判定するセッション残り時間の閾値
     */
    fun buildDailySuggestionStats(
        sessions: List<Session>,
        eventsBySessionId: Map<Long, List<SessionEvent>>,
        targetDate: LocalDate,
        zoneId: ZoneId,
        endSoonThresholdMillis: Long = 2 * 60_000L,
    ): SuggestionDailyStats? =
        SuggestionStatsCalculator.buildDailyStats(
            sessions = sessions,
            eventsBySessionId = eventsBySessionId,
            targetDate = targetDate,
            zoneId = zoneId,
            endSoonThresholdMillis = endSoonThresholdMillis,
        )
}
