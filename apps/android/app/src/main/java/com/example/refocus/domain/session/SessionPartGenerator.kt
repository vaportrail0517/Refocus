package com.example.refocus.domain.session

import com.example.refocus.core.model.Session
import com.example.refocus.core.model.SessionEvent
import com.example.refocus.core.model.SessionPart
import com.example.refocus.core.model.SessionStats
import com.example.refocus.domain.stats.SessionStatsCalculator
import java.time.Instant
import java.time.ZoneId

/**
 * Session + Event 列から SessionPart を生成するユーティリティ。
 *
 * - 日付境界（ローカルタイムの 0:00）でセッションを分割する
 * - 未終了セッション（FINISHED ではないもの）は統計対象外とする
 */
object SessionPartGenerator {

    /**
     * 全セッションの SessionPart を生成する。
     *
     * @param sessions Repository から取得した Session 一覧
     * @param eventsBySessionId 同じく Repository から取得した sessionId -> events のマップ
     * @param nowMillis 未終了セッションの duration 計算に使う現在時刻（SessionStatsCalculator と同じ）
     * @param zoneId 日付計算に使うタイムゾーン（通常は systemDefault）
     */
    fun generateParts(
        sessions: List<Session>,
        eventsBySessionId: Map<Long, List<SessionEvent>>,
        nowMillis: Long,
        zoneId: ZoneId,
    ): List<SessionPart> {
        if (sessions.isEmpty()) return emptyList()

        // 1. まず SessionStats を作って startedAt / endedAt を確定させる
        val statsList: List<SessionStats> =
            SessionStatsCalculator.buildSessionStats(
                sessions = sessions,
                eventsMap = eventsBySessionId,
                foregroundPackage = null, // 統計用途なので foregroundPackage は使わない
                nowMillis = nowMillis,
            )

        val statsById = statsList.associateBy { it.id }

        val result = mutableListOf<SessionPart>()

        // 2. 各セッションについて、日付境界で分割した SessionPart を生成
        for (session in sessions) {
            val id = session.id ?: continue
            val stats = statsById[id] ?: continue

            // 統計は「完了したセッション（End まで行ったもの）」だけを対象にする
            val endedAtMillis = stats.endedAtMillis ?: continue

            val startInstant = Instant.ofEpochMilli(stats.startedAtMillis)
            val endInstant = Instant.ofEpochMilli(endedAtMillis)

            var current = startInstant.atZone(zoneId)
            val endZdt = endInstant.atZone(zoneId)

            while (!current.toLocalDate().isAfter(endZdt.toLocalDate())) {
                val date = current.toLocalDate()

                // その日の 0:00〜24:00 の境界
                val dayStart = date.atStartOfDay(zoneId)
                val dayEnd = dayStart.plusDays(1)

                // セッション区間との交差
                val segStart = maxOf(current, dayStart)
                val segEnd = minOf(endZdt, dayEnd)

                if (segStart.isBefore(segEnd)) {
                    val startMinutes =
                        segStart.toLocalTime().hour * 60 + segStart.toLocalTime().minute
                    val endMinutes =
                        segEnd.toLocalTime().hour * 60 + segEnd.toLocalTime().minute
                    val durationMillis =
                        java.time.Duration.between(segStart, segEnd).toMillis()

                    result += SessionPart(
                        sessionId = id,
                        packageName = session.packageName,
                        date = date,
                        startDateTime = segStart.toInstant(),
                        endDateTime = segEnd.toInstant(),
                        startMinutesOfDay = startMinutes,
                        endMinutesOfDay = endMinutes,
                        durationMillis = durationMillis,
                    )
                }

                // 次の日へ
                current = date.plusDays(1).atStartOfDay(zoneId)
            }
        }
        return result
    }
}
