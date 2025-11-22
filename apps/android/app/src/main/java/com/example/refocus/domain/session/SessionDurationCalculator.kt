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

    fun calculateDurationMillis(
        events: List<SessionEvent>,
        nowMillis: Long,
    ): Long {
        if (events.isEmpty()) return 0L

        val sorted = events.sortedBy { it.timestampMillis }

        var lastStart: Long? = null
        var totalActive = 0L

        for (e in sorted) {
            when (e.type) {
                SessionEventType.Start -> {
                    lastStart = e.timestampMillis
                }

                SessionEventType.Pause -> {
                    if (lastStart != null) {
                        totalActive += (e.timestampMillis - lastStart!!).coerceAtLeast(0L)
                        lastStart = null
                    }
                }

                SessionEventType.Resume -> {
                    if (lastStart == null) {
                        lastStart = e.timestampMillis
                    }
                }

                SessionEventType.End -> {
                    if (lastStart != null) {
                        totalActive += (e.timestampMillis - lastStart!!).coerceAtLeast(0L)
                        lastStart = null
                    }
                }
            }
        }

        // まだ Start されたまま（End が来ていない）場合は now まで加算
        if (lastStart != null) {
            totalActive += (nowMillis - lastStart!!).coerceAtLeast(0L)
        }

        return totalActive.coerceAtLeast(0L)
    }
}
