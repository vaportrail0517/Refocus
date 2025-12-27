package com.example.refocus.domain.overlay.policy

import com.example.refocus.core.model.TimerTimeMode
import com.example.refocus.domain.overlay.orchestration.OverlaySessionTracker
import com.example.refocus.domain.overlay.usecase.DailyUsageUseCase

/**
 * タイマーの「表示値（text）」を返すための Provider．
 *
 * 目的
 * - 表示値モード追加時に when 分岐が散らばらないよう，責務を局所化する
 * - Provider 単体をテストしやすくする
 */
internal interface TimerDisplayValueProvider {

    /**
     * @param nowElapsedRealtime 呼び出し時点の elapsedRealtime
     */
    fun displayMillis(
        packageName: String,
        nowElapsedRealtime: Long,
    ): Long
}

internal class SessionElapsedDisplayValueProvider(
    private val sessionTracker: OverlaySessionTracker,
) : TimerDisplayValueProvider {
    override fun displayMillis(packageName: String, nowElapsedRealtime: Long): Long {
        return sessionTracker.computeElapsedFor(packageName, nowElapsedRealtime) ?: 0L
    }
}

internal class TodayThisTargetDisplayValueProvider(
    private val dailyUsageUseCase: DailyUsageUseCase,
) : TimerDisplayValueProvider {
    override fun displayMillis(packageName: String, nowElapsedRealtime: Long): Long {
        return dailyUsageUseCase.getTodayThisTargetMillis(packageName)
    }
}

internal class TodayAllTargetsDisplayValueProvider(
    private val dailyUsageUseCase: DailyUsageUseCase,
) : TimerDisplayValueProvider {
    override fun displayMillis(packageName: String, nowElapsedRealtime: Long): Long {
        return dailyUsageUseCase.getTodayAllTargetsMillis()
    }
}

/**
 * TimerTimeMode -> Provider の対応を一箇所に閉じ込めるための Selector．
 */
internal class TimerDisplayValueProviderSelector(
    sessionTracker: OverlaySessionTracker,
    dailyUsageUseCase: DailyUsageUseCase,
) {
    private val providers: Map<TimerTimeMode, TimerDisplayValueProvider> = mapOf(
        TimerTimeMode.SessionElapsed to SessionElapsedDisplayValueProvider(sessionTracker),
        TimerTimeMode.TodayThisTarget to TodayThisTargetDisplayValueProvider(dailyUsageUseCase),
        TimerTimeMode.TodayAllTargets to TodayAllTargetsDisplayValueProvider(dailyUsageUseCase),
    )

    fun select(mode: TimerTimeMode): TimerDisplayValueProvider {
        return providers[mode] ?: providers.getValue(TimerTimeMode.SessionElapsed)
    }
}