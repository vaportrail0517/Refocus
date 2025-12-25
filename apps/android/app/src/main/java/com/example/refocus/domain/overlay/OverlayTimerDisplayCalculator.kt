package com.example.refocus.domain.overlay

import com.example.refocus.core.model.Customize
import com.example.refocus.core.model.TimerTimeMode
import com.example.refocus.core.model.TimerVisualTimeBasis
import com.example.refocus.core.util.TimeSource

internal data class TimerMillisProviders(
    val displayMillisProvider: (nowElapsedRealtime: Long) -> Long,
    val visualMillisProvider: (nowElapsedRealtime: Long) -> Long,
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
    fun currentTimerDisplayMillis(trackingPackage: String?): Long? {
        val pkg = trackingPackage ?: return null
        val customize = customizeProvider()
        val nowMillis = timeSource.nowMillis()

        return when (customize.timerTimeMode) {
            TimerTimeMode.SessionElapsed -> {
                sessionTracker.computeElapsedFor(pkg, timeSource.elapsedRealtime())
            }

            TimerTimeMode.TodayThisTarget -> {
                dailyUsageUseCase.requestRefreshIfNeeded(
                    customize = customize,
                    targetPackages = lastTargetPackagesProvider(),
                    nowMillis = nowMillis,
                )
                dailyUsageUseCase.getTodayThisTargetMillis(pkg)
            }

            TimerTimeMode.TodayAllTargets -> {
                dailyUsageUseCase.requestRefreshIfNeeded(
                    customize = customize,
                    targetPackages = lastTargetPackagesProvider(),
                    nowMillis = nowMillis,
                )
                dailyUsageUseCase.getTodayAllTargetsMillis()
            }
        }
    }

    fun createProviders(packageName: String): TimerMillisProviders {
        val displayMillisProvider: (Long) -> Long = { nowElapsedRealtime ->
            val customize = customizeProvider()
            when (customize.timerTimeMode) {
                TimerTimeMode.SessionElapsed -> {
                    sessionTracker.computeElapsedFor(packageName, nowElapsedRealtime) ?: 0L
                }

                TimerTimeMode.TodayThisTarget -> {
                    dailyUsageUseCase.getTodayThisTargetMillis(packageName)
                }

                TimerTimeMode.TodayAllTargets -> {
                    dailyUsageUseCase.getTodayAllTargetsMillis()
                }
            }
        }

        val visualMillisProvider: (Long) -> Long = { nowElapsedRealtime ->
            val customize = customizeProvider()
            when (customize.timerVisualTimeBasis) {
                TimerVisualTimeBasis.SessionElapsed -> {
                    sessionTracker.computeElapsedFor(packageName, nowElapsedRealtime) ?: 0L
                }

                TimerVisualTimeBasis.FollowDisplayTime -> {
                    displayMillisProvider(nowElapsedRealtime)
                }
            }
        }

        return TimerMillisProviders(
            displayMillisProvider = displayMillisProvider,
            visualMillisProvider = visualMillisProvider,
        )
    }
}
