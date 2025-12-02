package com.example.refocus.domain.stats

import com.example.refocus.core.model.PauseResumeStats
import com.example.refocus.core.model.Session
import com.example.refocus.core.model.SessionEvent
import com.example.refocus.core.model.SessionEventType
import com.example.refocus.core.model.SessionStats
import com.example.refocus.core.model.SessionStatus
import com.example.refocus.domain.session.SessionDurationCalculator

/**
 * Session + Event 列から統計情報を組み立てるためのユーティリティ。
 *
 * ViewModel や Repository から使い回せるよう、純粋関数に寄せている。
 */
object SessionStatsCalculator {

    /**
     * セッション一覧とイベントマップから、UI に依存しない SessionStats のリストを構築する。
     *
     * @param sessions         DB などから取得した Session のリスト
     * @param eventsMap        sessionId → SessionEvent のリスト
     * @param foregroundPackage 現在 foreground のパッケージ名（なければ null）
     * @param nowMillis        「今」の時刻（未終了セッションの duration 計算に使用）
     */
    fun buildSessionStats(
        sessions: List<Session>,
        eventsMap: Map<Long, List<SessionEvent>>,
        foregroundPackage: String?,
        nowMillis: Long,
    ): List<SessionStats> {
        if (sessions.isEmpty()) return emptyList()

        return sessions
            // 「最後のイベント時刻」が新しい順に並べる
            .sortedByDescending { session ->
                val events = eventsMap[session.id] ?: emptyList()
                events.maxOfOrNull { it.timestampMillis } ?: 0L
            }
            .mapNotNull { session ->
                val id = session.id ?: return@mapNotNull null
                val events = eventsMap[id] ?: emptyList()
                if (events.isEmpty()) return@mapNotNull null

                val startedAt = events.firstOrNull {
                    it.type == SessionEventType.Start
                }?.timestampMillis ?: events.first().timestampMillis

                val endedAt = events.lastOrNull {
                    it.type == SessionEventType.End
                }?.timestampMillis

                val status = when {
                    endedAt != null -> SessionStatus.FINISHED
                    session.packageName == foregroundPackage -> SessionStatus.RUNNING
                    else -> SessionStatus.GRACE
                }

                val durationMillis =
                    SessionDurationCalculator.calculateDurationMillis(events, nowMillis)
                val pauseStats = buildPauseResumeStats(events)

                SessionStats(
                    id = id,
                    packageName = session.packageName,
                    startedAtMillis = startedAt,
                    endedAtMillis = endedAt,
                    durationMillis = durationMillis,
                    status = status,
                    pauseResumeEvents = pauseStats,
                )
            }
    }

    /**
     * イベント列から Pause/Resume のペアを統計用モデルに組み立てる。
     * - Pause → Resume の順に現れたもののみペアにする
     * - Resume が無い Pause は「未再開」として resumedAtMillis = null で残す
     */
    private fun buildPauseResumeStats(
        events: List<SessionEvent>
    ): List<PauseResumeStats> {
        if (events.isEmpty()) return emptyList()
        val sorted = events.sortedBy { it.timestampMillis }

        val result = mutableListOf<PauseResumeStats>()
        var currentPause: Long? = null

        for (e in sorted) {
            when (e.type) {
                SessionEventType.Pause -> {
                    // すでに Pause 中なら上書き（異常系は雑に潰す）
                    currentPause = e.timestampMillis
                }

                SessionEventType.Resume -> {
                    if (currentPause != null) {
                        result.add(
                            PauseResumeStats(
                                pausedAtMillis = currentPause!!,
                                resumedAtMillis = e.timestampMillis,
                            )
                        )
                        currentPause = null
                    }
                }

                else -> {
                    // Start / End はここでは何もしない
                }
            }
        }
        // Pause されたまま終わっている場合も、未再開として 1 行出しておく
        if (currentPause != null) {
            result.add(
                PauseResumeStats(
                    pausedAtMillis = currentPause!!,
                    resumedAtMillis = null,
                )
            )
        }
        return result
    }
}
