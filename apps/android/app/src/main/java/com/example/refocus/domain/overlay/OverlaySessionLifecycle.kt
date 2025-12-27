package com.example.refocus.domain.overlay

import com.example.refocus.core.logging.RefocusLog
import com.example.refocus.core.util.TimeSource
import com.example.refocus.domain.settings.SettingsCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 1つの論理セッションの開始・離脱と，それに紐づく UI 副作用をまとめたクラス．
 *
 * - OverlayCoordinator の巨大化を防ぐ
 * - セッション開始時の bootstrap，日次集計の refresh，提案ゲートなどの繋ぎ込みをここに集約する
 */
internal class OverlaySessionLifecycle(
    private val scope: CoroutineScope,
    private val timeSource: TimeSource,
    private val runtimeState: MutableStateFlow<OverlayRuntimeState>,
    private val settingsCommand: SettingsCommand,
    private val uiController: OverlayUiGateway,
    private val sessionBootstrapper: SessionBootstrapper,
    private val dailyUsageUseCase: DailyUsageUseCase,
    private val sessionTracker: OverlaySessionTracker,
    private val timerDisplayCalculator: OverlayTimerDisplayCalculator,
    private val suggestionOrchestrator: SuggestionOrchestrator,
) {
    companion object {
        private const val TAG = "OverlaySessionLifecycle"
    }

    /**
     * 現在計測中の論理セッションに対して，オーバーレイタイマーの表示をトグルする．
     *
     * 戻り値: トグル後に「表示」なら true．
     */
    fun toggleTimerVisibilityForCurrentSession(): Boolean {
        val pkg = runtimeState.value.overlayPackage ?: return false
        val suppressed = runtimeState.value.timerSuppressedForSession[pkg] == true
        val newSuppressed = !suppressed

        runtimeState.update {
            it.copy(timerSuppressedForSession = it.timerSuppressedForSession + (pkg to newSuppressed))
        }

        return if (newSuppressed) {
            uiController.hideTimer()
            runtimeState.update { it.copy(timerVisible = false) }
            false
        } else {
            showTimerForPackage(pkg)
            true
        }
    }

    suspend fun onEnterForeground(
        packageName: String,
        nowMillis: Long,
        nowElapsed: Long,
    ) {
        val settings = runtimeState.value.customize
        val grace = settings.gracePeriodMillis

        val bootstrap = sessionBootstrapper.computeBootstrapFromTimeline(
            packageName = packageName,
            customize = settings,
            nowMillis = nowMillis,
            force = false,
            sessionTracker = sessionTracker,
        )

        val initialElapsed = bootstrap?.initialElapsedMillis ?: 0L
        val isNewSession = sessionTracker.onEnterTargetApp(
            packageName = packageName,
            gracePeriodMillis = grace,
            initialElapsedIfNew = initialElapsed,
        )

        if (isNewSession) {
            suggestionOrchestrator.onNewSession(bootstrap)

            // 「このセッションのみ非表示」フラグは新規セッション開始時にリセットする
            runtimeState.update {
                it.copy(timerSuppressedForSession = it.timerSuppressedForSession + (packageName to false))
            }
        }

        runtimeState.update {
            it.copy(
                overlayPackage = packageName,
                trackingPackage = packageName,
            )
        }

        // 日次集計表示モードの場合のみ，必要に応じてスナップショット更新を要求する（非同期）
        dailyUsageUseCase.requestRefreshIfNeeded(
            customize = settings,
            targetPackages = runtimeState.value.lastTargetPackages,
            nowMillis = nowMillis,
        )

        if (isTimerSuppressedForCurrentSession(packageName)) {
            runtimeState.update { it.copy(timerVisible = false) }
            uiController.hideTimer()
        } else {
            showTimerForPackage(packageName)
        }
    }

    fun onLeaveForeground(
        packageName: String,
        nowMillis: Long,
        nowElapsed: Long,
    ) {
        // 先にオーバーレイを閉じる
        if (runtimeState.value.overlayPackage == packageName) {
            runtimeState.update {
                it.copy(
                    overlayPackage = null,
                    trackingPackage = null,
                    timerVisible = false,
                )
            }
            // show/hide が非同期に Main へ投げられるため，並び替わりが起きても古い hide が新しい show を消さないようにする
            uiController.hideTimer(token = packageName)
            uiController.hideSuggestion()
            suggestionOrchestrator.clearOverlayState()
        }

        // ランタイムのセッション情報を更新
        sessionTracker.onLeaveTargetApp(packageName)

        // ここでは「このセッションでは提案しない」などのゲートはリセットしない
        // （猶予時間内の一時離脱は同一セッション扱いにしたい）
    }

    private fun showTimerForPackage(packageName: String) {
        val providers = timerDisplayCalculator.createProviders(packageName)

        runtimeState.update { it.copy(timerVisible = true) }
        uiController.showTimer(
            token = packageName,
            displayMillisProvider = providers.displayMillisProvider,
            visualMillisProvider = providers.visualMillisProvider,
            onPositionChanged = ::onOverlayPositionChanged,
        )
    }

    private fun isTimerSuppressedForCurrentSession(packageName: String): Boolean {
        return runtimeState.value.timerSuppressedForSession[packageName] == true
    }

    private fun onOverlayPositionChanged(x: Int, y: Int) {
        scope.launch {
            try {
                settingsCommand.setOverlayPosition(
                    x = x,
                    y = y,
                    source = "overlay",
                    reason = "drag",
                    recordEvent = false,
                )
            } catch (e: Exception) {
                RefocusLog.e(TAG, e) { "Failed to save overlay position" }
            }
        }
    }
}
