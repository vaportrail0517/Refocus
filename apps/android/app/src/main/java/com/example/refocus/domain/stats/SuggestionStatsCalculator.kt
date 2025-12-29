package com.example.refocus.domain.stats

import com.example.refocus.core.model.Session
import com.example.refocus.core.model.SessionEvent
import com.example.refocus.core.model.SessionEventType
import com.example.refocus.core.model.SuggestionDailyStats
import com.example.refocus.core.model.SuggestionDecision
import com.example.refocus.core.model.SuggestionInstance
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object SuggestionStatsCalculator {
    private val decisionTypes =
        setOf(
            SessionEventType.SuggestionSnoozed,
            SessionEventType.SuggestionDismissed,
            SessionEventType.SuggestionDisabledForSession,
        )

    /**
     * 1 日分の提案統計を構築する。
     *
     * @param sessions          セッション一覧
     * @param eventsBySessionId sessionId → セッションイベント一覧
     * @param targetDate        集計対象の日
     * @param zoneId            ローカルタイムゾーン
     * @param endSoonThresholdMillis 「短時間で終了」とみなす閾値（デフォルト 2 分）
     */
    fun buildDailyStats(
        sessions: List<Session>,
        eventsBySessionId: Map<Long, List<SessionEvent>>,
        targetDate: LocalDate,
        zoneId: ZoneId,
        endSoonThresholdMillis: Long = 2 * 60_000L,
    ): SuggestionDailyStats? {
        val instances = mutableListOf<SuggestionInstance>()

        for (session in sessions) {
            val sessionId = session.id ?: continue
            val events = eventsBySessionId[sessionId].orEmpty()
            if (events.isEmpty()) continue

            // 1セッション内の SuggestionShown を順に処理
            for ((index, e) in events.withIndex()) {
                if (e.type != SessionEventType.SuggestionShown) continue

                // 日付フィルタ：提示された日付が targetDate のものだけ対象
                val shownDate =
                    Instant
                        .ofEpochMilli(e.timestampMillis)
                        .atZone(zoneId)
                        .toLocalDate()
                if (shownDate != targetDate) continue

                val shownAt = e.timestampMillis

                // 後続イベント
                val following = events.subList(index + 1, events.size)

                // 次の SuggestionShown が出るまでを、この提案の「範囲」とみなす
                val untilNextShown =
                    following.takeWhile {
                        it.type != SessionEventType.SuggestionShown
                    }

                // 決定イベント（あれば）
                val decisionEvent = untilNextShown.firstOrNull { it.type in decisionTypes }
                val decision =
                    when (decisionEvent?.type) {
                        SessionEventType.SuggestionSnoozed -> SuggestionDecision.Snoozed
                        SessionEventType.SuggestionDismissed -> SuggestionDecision.Dismissed
                        SessionEventType.SuggestionDisabledForSession -> SuggestionDecision.DisabledForSession
                        else -> null
                    }

                // セッション終了イベント
                val endEvent = following.firstOrNull { it.type == SessionEventType.End }

                val timeToEnd =
                    endEvent?.let { end ->
                        (end.timestampMillis - shownAt).coerceAtLeast(0L)
                    }

                val endedSoon: Boolean? = timeToEnd?.let { it <= endSoonThresholdMillis }

                instances +=
                    SuggestionInstance(
                        suggestionEventId = e.id ?: 0L,
                        sessionId = sessionId,
                        packageName = session.packageName,
                        shownAtMillis = shownAt,
                        decision = decision,
                        decisionAtMillis = decisionEvent?.timestampMillis,
                        endAtMillis = endEvent?.timestampMillis,
                        timeToEndMillis = timeToEnd,
                        endedSoon = endedSoon,
                    )
            }
        }

        if (instances.isEmpty()) {
            return null
        }

        // 集計
        val totalShown = instances.size
        var snoozed = 0
        var dismissed = 0
        var disabled = 0
        var endedSoon = 0
        var continued = 0
        var noEndYet = 0

        val endedSoonByDecision = mutableMapOf<SuggestionDecision, Int>()

        for (inst in instances) {
            when (inst.decision) {
                SuggestionDecision.Snoozed -> snoozed++
                SuggestionDecision.Dismissed -> dismissed++
                SuggestionDecision.DisabledForSession -> disabled++
                null -> {}
            }

            when (inst.endedSoon) {
                true -> {
                    endedSoon++
                    inst.decision?.let { dec ->
                        endedSoonByDecision[dec] =
                            (endedSoonByDecision[dec] ?: 0) + 1
                    }
                }

                false -> {
                    continued++
                }

                null -> {
                }
            }

            if (inst.endAtMillis == null) {
                noEndYet++
            }
        }

        return SuggestionDailyStats(
            date = targetDate,
            totalShown = totalShown,
            snoozedCount = snoozed,
            dismissedCount = dismissed,
            disabledForSessionCount = disabled,
            endedSoonCount = endedSoon,
            continuedCount = continued,
            noEndYetCount = noEndYet,
            endedSoonByDecision = endedSoonByDecision,
        )
    }
}
