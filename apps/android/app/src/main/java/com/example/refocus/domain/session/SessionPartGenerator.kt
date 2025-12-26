package com.example.refocus.domain.session

import com.example.refocus.core.model.Session
import com.example.refocus.core.model.SessionEvent
import com.example.refocus.core.model.SessionPart
import com.example.refocus.domain.stats.SessionStatsCalculator
import java.time.Instant
import java.time.ZoneId

/**
 * Session + Event 列から SessionPart を生成するユーティリティ．
 *
 * - 日付境界（ローカルタイムの 0:00）でセッションを分割する
 * - 未終了セッション（RUNNING / GRACE など）も，nowMillis を仮の終端として切片を生成する
 *
 *   統計・日次累計・オーバーレイなど，「いま現在までの使用時間」を扱うために，
 *   FINISHED だけに限定せず切片を作れるようにしている．
 *   将来的に「完了セッションのみ」などの目的別フィルタが必要になったら，
 *   呼び出し側で status を見て制御する．
 */
object SessionPartGenerator {

    fun generateParts(
        sessions: List<Session>,
        eventsBySessionId: Map<Long, List<SessionEvent>>,
        nowMillis: Long,
        zoneId: ZoneId,
    ): List<SessionPart> {
        if (sessions.isEmpty()) return emptyList()

        // stats は主に packageName / status の補助情報として取得
        val statsList = SessionStatsCalculator.buildSessionStats(
            sessions = sessions,
            eventsMap = eventsBySessionId,
            foregroundPackage = null,
            nowMillis = nowMillis,
        )
        val statsById = statsList.associateBy { it.id }

        val parts = mutableListOf<SessionPart>()

        for (session in sessions) {
            val id = session.id ?: continue
            val stats = statsById[id] ?: continue

            // ★ ここで FINISHED だけに絞らないことがポイント
            //    GRACE や RUNNING も含めたいならフィルタ不要
            //    （ステータス別に制御したければここで条件分岐してもよい）

            val events = eventsBySessionId[id].orEmpty()
            if (events.isEmpty()) continue

            // Start / Pause / Resume / End からアクティブ区間リストを算出
            val activeSegments =
                SessionDurationCalculator.buildActiveSegments(events, nowMillis)
            if (activeSegments.isEmpty()) continue

            // 各アクティブ区間を日付境界で切り分けて SessionPart を生成
            for (segment in activeSegments) {
                val startInstant = Instant.ofEpochMilli(segment.startMillis)
                val endInstant = Instant.ofEpochMilli(segment.endMillis)

                var current = startInstant.atZone(zoneId)
                val endZdt = endInstant.atZone(zoneId)

                while (!current.toLocalDate().isAfter(endZdt.toLocalDate())) {
                    val date = current.toLocalDate()

                    val dayStart = date.atStartOfDay(zoneId)
                    val dayEnd = dayStart.plusDays(1)

                    val segStart = maxOf(current, dayStart)
                    val segEnd = minOf(endZdt, dayEnd)

                    if (segStart.isBefore(segEnd)) {
                        val startMinutes =
                            segStart.toLocalTime().hour * 60 + segStart.toLocalTime().minute
                        val endMinutes =
                            segEnd.toLocalTime().hour * 60 + segEnd.toLocalTime().minute
                        val durationMillis =
                            java.time.Duration.between(segStart, segEnd).toMillis()

                        parts += SessionPart(
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
        }

        return parts
    }
}
