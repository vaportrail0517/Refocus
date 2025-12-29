package com.example.refocus.domain.overlay.policy

import com.example.refocus.core.model.TimerVisualTimeBasis
import com.example.refocus.domain.overlay.orchestration.OverlaySessionTracker

/**
 * タイマーの「見た目変化用の時間基準（visual）」を返すための Provider．
 *
 * 目的
 * - TimerVisualTimeBasis の追加に伴う when 分岐の拡散を防ぐ
 */
internal interface TimerVisualBasisProvider {
    /**
     * @param nowElapsedRealtime 呼び出し時点の elapsedRealtime
     * @param displayMillis 同じ時点における「表示値（text）」
     */
    fun visualMillis(
        packageName: String,
        nowElapsedRealtime: Long,
        displayMillis: Long,
    ): Long
}

internal class SessionElapsedVisualBasisProvider(
    private val sessionTracker: OverlaySessionTracker,
) : TimerVisualBasisProvider {
    override fun visualMillis(
        packageName: String,
        nowElapsedRealtime: Long,
        displayMillis: Long,
    ): Long = sessionTracker.computeElapsedFor(packageName, nowElapsedRealtime) ?: 0L
}

internal object FollowDisplayTimeVisualBasisProvider : TimerVisualBasisProvider {
    override fun visualMillis(
        packageName: String,
        nowElapsedRealtime: Long,
        displayMillis: Long,
    ): Long = displayMillis
}

/**
 * TimerVisualTimeBasis -> Provider の対応を一箇所に閉じ込めるための Selector．
 */
internal class TimerVisualBasisProviderSelector(
    sessionTracker: OverlaySessionTracker,
) {
    private val providers: Map<TimerVisualTimeBasis, TimerVisualBasisProvider> =
        mapOf(
            TimerVisualTimeBasis.SessionElapsed to SessionElapsedVisualBasisProvider(sessionTracker),
            TimerVisualTimeBasis.FollowDisplayTime to FollowDisplayTimeVisualBasisProvider,
        )

    fun select(basis: TimerVisualTimeBasis): TimerVisualBasisProvider =
        providers[basis] ?: providers.getValue(TimerVisualTimeBasis.SessionElapsed)
}
