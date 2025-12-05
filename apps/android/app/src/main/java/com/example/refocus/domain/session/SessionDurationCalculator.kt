package com.example.refocus.domain.session

import com.example.refocus.core.model.SessionEvent
import com.example.refocus.core.model.SessionEventType

/**
 * Start / Pause / Resume / End のイベント列から
 * 「実際にアプリを使っていた時間」の合計を求めるユーティリティ。
 *
 * Repository / Stats / Overlay などから共通で利用する。
 */
object SessionDurationCalculator {

    data class ActiveSegment(
        val startMillis: Long,
        val endMillis: Long,
    )

    /**
     * Start / Pause / Resume / End のイベント列から、
     * 実際にアプリが前面で動いていた時間帯（アクティブ区間）のリストを返す。
     */
    fun buildActiveSegments(
        events: List<SessionEvent>,
        nowMillis: Long,
    ): List<ActiveSegment> {
        if (events.isEmpty()) return emptyList()

        val sorted = events.sortedBy { it.timestampMillis }

        var lastStart: Long? = null
        val result = mutableListOf<ActiveSegment>()

        for (e in sorted) {
            when (e.type) {
                SessionEventType.Start -> {
                    // セッション開始 → 新しいアクティブ区間の開始
                    lastStart = e.timestampMillis
                }

                SessionEventType.Resume -> {
                    // 一時停止からの再開 → 新しいアクティブ区間の開始
                    if (lastStart == null) {
                        lastStart = e.timestampMillis
                    }
                }

                SessionEventType.Pause -> {
                    // 一時停止 → アクティブ区間を閉じる
                    if (lastStart != null) {
                        val end = e.timestampMillis
                        if (end > lastStart!!) {
                            result += ActiveSegment(lastStart!!, end)
                        }
                        lastStart = null
                    }
                }

                SessionEventType.End -> {
                    // セッション終了 → アクティブ区間を閉じる
                    if (lastStart != null) {
                        val end = e.timestampMillis
                        if (end > lastStart!!) {
                            result += ActiveSegment(lastStart!!, end)
                        }
                        lastStart = null
                    }
                }

                // サジェスト関連イベントは時間集計には影響しない
                SessionEventType.SuggestionShown,
                SessionEventType.SuggestionSnoozed,
                SessionEventType.SuggestionDismissed,
                SessionEventType.SuggestionDisabledForSession -> Unit
            }
        }

        // Start / Resume されたまま End が来ていない場合
        // → RUNNING セッションとして「今まで」をアクティブ区間とみなす
        if (lastStart != null) {
            val end = nowMillis
            if (end > lastStart!!) {
                result += ActiveSegment(lastStart!!, end)
            }
        }

        return result
    }

    /**
     * 既存の合計時間計算は buildActiveSegments を足し合わせるだけにする。
     */
    fun calculateDurationMillis(
        events: List<SessionEvent>,
        nowMillis: Long,
    ): Long {
        return buildActiveSegments(events, nowMillis)
            .sumOf { (it.endMillis - it.startMillis).coerceAtLeast(0L) }
            .coerceAtLeast(0L)
    }
}
