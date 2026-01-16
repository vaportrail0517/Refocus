package com.example.refocus.domain.session

import com.example.refocus.core.model.SessionEvent
import com.example.refocus.core.model.SessionEventType

/**
 * Start / Pause / Resume / End のイベント列から
 * 「実際にアプリを使っていた時間」の合計を求めるユーティリティ。
 *
 * Repository / DefaultStatsUseCase / Overlay などから共通で利用する。
 */
object SessionDurationCalculator {
    data class ActiveSegment(
        val startMillis: Long,
        val endMillis: Long,
    )

    /**
     * Start / Pause / Resume / End のイベント列から、
     * 実際にアプリが前面で動いていた時間帯（アクティブ区間）のリストを返す。
     *
     * 注意点:
     * - Pause/Resume は「対象アプリが前面ではない（画面 OFF を含む）」状態
     * - UiPause/UiResume は「対象アプリは前面だが Refocus 側 UI により計測だけ止めたい」状態
     *
     * これらは独立に入れ子になり得るため、単一の lastStart だけで扱うと
     * UiPause→Pause→UiResume のような列で「前面ではないのに計測再開」してしまい、
     * 終了時刻より後（nowMillis まで）の区間が足されるバグが起きる。
     */
    fun buildActiveSegments(
        events: List<SessionEvent>,
        nowMillis: Long,
    ): List<ActiveSegment> {
        if (events.isEmpty()) return emptyList()

        val sorted = events.sortedBy { it.timestampMillis }

        var foregroundActive = false
        var uiPaused = false
        var segmentStart: Long? = null

        val result = mutableListOf<ActiveSegment>()

        fun closeSegment(endMillis: Long) {
            val start = segmentStart ?: return
            if (endMillis > start) {
                result += ActiveSegment(start, endMillis)
            }
            segmentStart = null
        }

        fun recomputeSegment(ts: Long) {
            val countable = foregroundActive && !uiPaused
            if (countable) {
                if (segmentStart == null) segmentStart = ts
            } else {
                closeSegment(ts)
            }
        }

        for (e in sorted) {
            when (e.type) {
                SessionEventType.Start -> {
                    // セッション開始時点では「前面で計測可能」とみなす。
                    foregroundActive = true
                    // Start を跨いで UiPause が残る状態は想定外なので、安全側でリセットする。
                    uiPaused = false
                    recomputeSegment(e.timestampMillis)
                }

                SessionEventType.Resume -> {
                    foregroundActive = true
                    recomputeSegment(e.timestampMillis)
                }

                SessionEventType.Pause -> {
                    foregroundActive = false
                    recomputeSegment(e.timestampMillis)
                }

                SessionEventType.UiPause -> {
                    uiPaused = true
                    recomputeSegment(e.timestampMillis)
                }

                SessionEventType.UiResume -> {
                    // 前面でない間に UiResume が来ることがある（提案 UI を閉じた等）。
                    // その場合も UiPause 状態自体は解除しておき、
                    // 「前面に戻ったとき」に正しく計測再開できるようにする。
                    uiPaused = false
                    recomputeSegment(e.timestampMillis)
                }

                SessionEventType.End -> {
                    // セッション終了は hard stop。
                    closeSegment(e.timestampMillis)
                    segmentStart = null
                    break
                }

                // サジェスト関連イベントは時間集計には影響しない
                SessionEventType.SuggestionShown,
                SessionEventType.SuggestionSnoozed,
                SessionEventType.SuggestionDismissed,
                SessionEventType.SuggestionOpened,
                SessionEventType.SuggestionDisabledForSession,
                -> Unit
            }
        }

        // End が来ていない場合のみ、RUNNING セッションとして「今まで」をアクティブ区間とみなす。
        if (segmentStart != null) {
            closeSegment(nowMillis)
        }

        return result
    }

    /**
     * 既存の合計時間計算は buildActiveSegments を足し合わせるだけにする。
     */
    fun calculateDurationMillis(
        events: List<SessionEvent>,
        nowMillis: Long,
    ): Long =
        buildActiveSegments(events, nowMillis)
            .sumOf { (it.endMillis - it.startMillis).coerceAtLeast(0L) }
            .coerceAtLeast(0L)
}
