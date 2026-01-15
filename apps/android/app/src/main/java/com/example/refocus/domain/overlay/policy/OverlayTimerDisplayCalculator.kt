package com.example.refocus.domain.overlay.policy

import com.example.refocus.core.model.Customize
import com.example.refocus.core.model.TimerTimeMode
import com.example.refocus.core.util.TimeSource
import com.example.refocus.domain.overlay.orchestration.OverlaySessionTracker
import com.example.refocus.domain.overlay.usecase.DailyUsageUseCase

internal data class TimerMillisProviders(
    val displayMillisProvider: (nowElapsedRealtime: Long) -> Long,
    val visualMillisProvider: (nowElapsedRealtime: Long) -> Long,
    /**
     * エフェクト（点滅・回転・揺れ）の発火スケジューリングに使う時間基準。
     *
     * - 表示値モードや visual 基準とは独立に，常に「論理セッション経過時間」を返す想定。
     */
    val effectMillisProvider: (nowElapsedRealtime: Long) -> Long,
)

/**
 * タイマーの表示値（text）と見た目変化用の時間基準（visual）を計算する責務を切り出す．
 *
 * - Provider は，呼び出し時点の設定（timerTimeMode / timerVisualTimeBasis）を参照する．
 * - DB 読みや日次スナップショット更新のトリガは，ここでは行わない（高頻度呼び出し経路のため）．
 */
internal class OverlayTimerDisplayCalculator(
    private val timeSource: TimeSource,
    private val sessionTracker: OverlaySessionTracker,
    private val dailyUsageUseCase: DailyUsageUseCase,
    private val customizeProvider: () -> Customize,
    private val lastTargetPackagesProvider: () -> Set<String>,
) {
    private val displayProviderSelector =
        TimerDisplayValueProviderSelector(
            sessionTracker = sessionTracker,
            dailyUsageUseCase = dailyUsageUseCase,
        )

    private val visualBasisProviderSelector =
        TimerVisualBasisProviderSelector(
            sessionTracker = sessionTracker,
        )

    fun currentTimerDisplayMillis(trackingPackage: String?): Long? {
        val pkg = trackingPackage ?: return null
        val customize = customizeProvider()
        val nowMillis = timeSource.nowMillis()

        // 日次累計モードの場合は，必要に応じてスナップショット（DB 参照＋投影）の更新を要求する
        if (customize.timerTimeMode != TimerTimeMode.SessionElapsed) {
            dailyUsageUseCase.requestRefreshIfNeeded(
                customize = customize,
                targetPackages = lastTargetPackagesProvider(),
                nowMillis = nowMillis,
            )
        }

        val provider = displayProviderSelector.select(customize.timerTimeMode)
        return provider.displayMillis(pkg, timeSource.elapsedRealtime())
    }

    fun createProviders(packageName: String): TimerMillisProviders {
        val displayMillisProvider: (Long) -> Long = { nowElapsedRealtime ->
            val customize = customizeProvider()
            val provider = displayProviderSelector.select(customize.timerTimeMode)
            provider.displayMillis(packageName, nowElapsedRealtime)
        }

        val visualMillisProvider: (Long) -> Long = { nowElapsedRealtime ->
            val customize = customizeProvider()
            val displayMillis = displayMillisProvider(nowElapsedRealtime)
            val provider = visualBasisProviderSelector.select(customize.timerVisualTimeBasis)
            provider.visualMillis(
                packageName = packageName,
                nowElapsedRealtime = nowElapsedRealtime,
                displayMillis = displayMillis,
            )
        }

        val effectMillisProvider: (Long) -> Long = { nowElapsedRealtime ->
            sessionTracker.computeElapsedFor(packageName, nowElapsedRealtime) ?: 0L
        }

        return TimerMillisProviders(
            displayMillisProvider = displayMillisProvider,
            visualMillisProvider = visualMillisProvider,
            effectMillisProvider = effectMillisProvider,
        )
    }
}
