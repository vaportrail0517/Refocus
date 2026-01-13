package com.example.refocus.domain.overlay.runtime

import com.example.refocus.core.logging.RefocusLog
import com.example.refocus.core.util.ResilientCoroutines
import com.example.refocus.core.util.TimeSource
import com.example.refocus.domain.overlay.engine.OverlayEvent
import com.example.refocus.domain.overlay.engine.OverlayState
import com.example.refocus.domain.overlay.model.OverlayPresentationState
import com.example.refocus.domain.overlay.model.OverlayRuntimeState
import com.example.refocus.domain.overlay.orchestration.ForegroundTrackingOrchestrator
import com.example.refocus.domain.overlay.orchestration.OverlaySessionLifecycle
import com.example.refocus.domain.overlay.orchestration.OverlaySessionTracker
import com.example.refocus.domain.overlay.orchestration.OverlaySettingsObserver
import com.example.refocus.domain.overlay.orchestration.SuggestionOrchestrator
import com.example.refocus.domain.overlay.policy.OverlayTimerDisplayCalculator
import com.example.refocus.domain.overlay.port.OverlayHealthStore
import com.example.refocus.domain.overlay.port.OverlayUiPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * - 前面アプリの監視
 * - タイマーオーバーレイ / 提案オーバーレイの制御
 * を担当する．
 *
 * Android Service / Notification / BroadcastReceiver などの OS 依存部分は
 * OverlayService 側に残す．
 */
internal class OverlayCoordinator(
    private val scope: CoroutineScope,
    private val timeSource: TimeSource,
    private val overlayHealthStore: OverlayHealthStore,
    private val uiController: OverlayUiPort,
    private val runtimeState: MutableStateFlow<OverlayRuntimeState>,
    private val sessionTracker: OverlaySessionTracker,
    private val timerDisplayCalculator: OverlayTimerDisplayCalculator,
    private val suggestionOrchestrator: SuggestionOrchestrator,
    private val sessionLifecycle: OverlaySessionLifecycle,
    private val settingsObserver: OverlaySettingsObserver,
    private val foregroundTrackingOrchestrator: ForegroundTrackingOrchestrator,
    private val eventDispatcher: OverlayEventDispatcher,
) {
    companion object {
        private const val TAG = "OverlayCoordinator"

        private const val MONITOR_WATCHDOG_INTERVAL_MS: Long = 30_000L
        private const val MONITOR_STALE_THRESHOLD_MS: Long = 45_000L
        private const val MONITOR_RESTART_GRACE_DELAY_MS: Long = 200L
    }

    private val screenOnFlowInternal: StateFlow<Boolean> =
        runtimeState
            .map { it.isScreenOn }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, runtimeState.value.isScreenOn)
    private val trackingPackageFlowInternal: StateFlow<String?> =
        runtimeState
            .map { it.trackingPackage }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, runtimeState.value.trackingPackage)
    private val timerVisibleFlowInternal: StateFlow<Boolean> =
        runtimeState
            .map { it.timerVisible }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, runtimeState.value.timerVisible)

    /**
     * 通知や system 層の描画は「単一の表示状態」だけを購読する．
     * 時間経過で変化する値（表示時間）は tick により 1 秒ごとに更新される．
     */
    private val presentationTick = MutableStateFlow(0L)

    @Volatile
    private var presentationTickJob: Job? = null

    private val presentationStateFlowInternal: StateFlow<OverlayPresentationState> =
        combine(
            trackingPackageFlowInternal,
            timerVisibleFlowInternal,
            runtimeState.map { it.customize.touchMode }.distinctUntilChanged(),
            runtimeState.map { it.customize.timerTimeMode }.distinctUntilChanged(),
            presentationTick,
        ) { trackingPkg, timerVisible, touchMode, timerTimeMode, _ ->
            val displayMillis =
                trackingPkg?.let { timerDisplayCalculator.currentTimerDisplayMillis(it) }
            OverlayPresentationState(
                trackingPackage = trackingPkg,
                timerDisplayMillis = displayMillis,
                isTimerVisible = trackingPkg != null && timerVisible,
                touchMode = touchMode,
                timerTimeMode = timerTimeMode,
            )
        }.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue =
                OverlayPresentationState(
                    trackingPackage = runtimeState.value.trackingPackage,
                    timerDisplayMillis =
                        runtimeState.value.trackingPackage?.let {
                            timerDisplayCalculator.currentTimerDisplayMillis(it)
                        },
                    isTimerVisible = runtimeState.value.trackingPackage != null && runtimeState.value.timerVisible,
                    touchMode = runtimeState.value.customize.touchMode,
                    timerTimeMode = runtimeState.value.customize.timerTimeMode,
                ),
        )

    val presentationStateFlow: StateFlow<OverlayPresentationState> get() = presentationStateFlowInternal

    fun currentPresentationState(): OverlayPresentationState = presentationStateFlowInternal.value

    @Volatile
    private var foregroundGateJob: Job? = null

    @Volatile
    private var monitorWatchdogJob: Job? = null

    /**
     * 外側から「画面ON/OFF」を伝えるためのメソッド．
     * BroadcastReceiver 側から呼ばれる．
     */
    fun setScreenOn(isOn: Boolean) {
        runtimeState.update { it.copy(isScreenOn = isOn) }
    }

    /**
     * 現在計測中の論理セッションに対して，オーバーレイタイマーの表示をトグルする．
     *
     * 戻り値: トグル後に「表示」なら true．
     */
    @Synchronized
    fun toggleTimerVisibilityForCurrentSession(): Boolean = sessionLifecycle.toggleTimerVisibilityForCurrentSession()

    /**
     * 画面OFF時に呼び出す．前面にいた対象アプリを「離脱」として扱う．
     */
    fun onScreenOff() {
        val nowMillis = timeSource.nowMillis()
        val nowElapsed = timeSource.elapsedRealtime()
        RefocusLog.d(TAG) { "onScreenOff: screen off event" }

        dispatchEvent(
            OverlayEvent.ScreenOff(
                nowMillis = nowMillis,
                nowElapsedRealtime = nowElapsed,
            ),
        )

        // 現在の前面アプリ情報はリセット
        runtimeState.update {
            it.copy(
                currentForegroundPackage = null,
                overlayPackage = null,
                trackingPackage = null,
                timerVisible = false,
            )
        }
    }

    /**
     * 監視と設定購読を開始．
     * OverlayService.onCreate から呼ばれる想定．
     */
    fun start() {
        startPresentationTicker()
        settingsObserver.start()
        startForegroundTrackingGate()
        startMonitorWatchdog()
    }

    private fun startForegroundTrackingGate() {
        // overlayEnabled の変化に合わせて前面監視ループを開始・停止する
        if (foregroundGateJob?.isActive == true) return
        foregroundGateJob =
            ResilientCoroutines.launchResilient(
                scope = scope,
                tag = TAG,
            ) {
                runtimeState
                    .map { it.customize.overlayEnabled }
                    .distinctUntilChanged()
                    .collect { enabled ->
                        try {
                            if (enabled) {
                                foregroundTrackingOrchestrator.start(
                                    screenOnFlow = screenOnFlowInternal,
                                )
                            } else {
                                foregroundTrackingOrchestrator.stop()
                            }
                        } catch (e: Exception) {
                            RefocusLog.e(TAG, e) { "foreground tracking gate failed. continue." }
                        }
                    }
            }
    }

    fun requestForegroundTrackingRestart(reason: String) {
        // 実際に overlay が有効なときだけ復旧を試みる
        if (!runtimeState.value.customize.overlayEnabled) return

        scope.launch {
            try {
                overlayHealthStore.update { current ->
                    current.copy(
                        monitorRestartCount = current.monitorRestartCount + 1,
                        lastErrorSummary = "monitor_restart_requested: $reason",
                    )
                }
            } catch (e: Exception) {
                RefocusLog.w(TAG, e) { "Failed to record monitor restart request" }
            }

            try {
                foregroundTrackingOrchestrator.stop()
                delay(MONITOR_RESTART_GRACE_DELAY_MS)
                foregroundTrackingOrchestrator.start(screenOnFlow = screenOnFlowInternal)
            } catch (e: Exception) {
                RefocusLog.e(TAG, e) { "Failed to restart foreground tracking: $reason" }
            }
        }
    }

    private fun startMonitorWatchdog() {
        if (monitorWatchdogJob?.isActive == true) return
        monitorWatchdogJob =
            ResilientCoroutines.launchResilient(
                scope = scope,
                tag = TAG,
                initialBackoffMs = 5_000L,
                maxBackoffMs = 60_000L,
            ) {
                while (isActive) {
                    delay(MONITOR_WATCHDOG_INTERVAL_MS)

                    val enabled = runtimeState.value.customize.overlayEnabled
                    if (!enabled) continue

                    val nowElapsed = timeSource.elapsedRealtime()
                    val lastElapsed =
                        try {
                            overlayHealthStore.read().lastForegroundSampleElapsedRealtimeMillis
                        } catch (e: Exception) {
                            RefocusLog.w(TAG, e) { "Failed to read overlay health for watchdog" }
                            continue
                        }

                    if (lastElapsed == null) continue
                    val delta = nowElapsed - lastElapsed
                    if (delta > MONITOR_STALE_THRESHOLD_MS) {
                        RefocusLog.w(TAG) {
                            "foreground monitor liveness stale (delta=${delta}ms). restarting tracking."
                        }
                        requestForegroundTrackingRestart(reason = "watchdog_stale")
                    }
                }
            }
    }

    /**
     * Service 破棄時に呼ぶ．
     * オーバーレイ表示を片付ける．
     */
    fun stop() {
        presentationTickJob?.cancel()
        presentationTickJob = null
        settingsObserver.stop()

        foregroundGateJob?.cancel()
        foregroundGateJob = null

        monitorWatchdogJob?.cancel()
        monitorWatchdogJob = null

        foregroundTrackingOrchestrator.stop()

        uiController.hideTimer()
        uiController.hideSuggestion()
        uiController.hideMiniGame()
        suggestionOrchestrator.onDisabled()

        runtimeState.update {
            it.copy(
                currentForegroundPackage = null,
                overlayPackage = null,
                trackingPackage = null,
                timerVisible = false,
                overlayState = OverlayState.Idle,
                lastTargetPackages = emptySet(),
                timerSuppressedForSession = emptyMap(),
            )
        }

        // オーバーレイ用セッションもクリア
        sessionTracker.clear()
        // scope 自体のキャンセルは Service 側で行う
    }

    private fun startPresentationTicker() {
        if (presentationTickJob?.isActive == true) return
        presentationTickJob =
            ResilientCoroutines.launchResilient(
                scope = scope,
                tag = TAG,
                initialBackoffMs = 1_000L,
                maxBackoffMs = 10_000L,
            ) {
                while (isActive) {
                    delay(1_000)
                    // 計測中のみ 1 秒 tick を進める（通知用の表示値を更新するため）
                    if (runtimeState.value.trackingPackage != null) {
                        presentationTick.update { it + 1 }
                    }
                }
            }
    }

    /**
     * OverlayStateMachine へのイベント送出．
     */
    private fun dispatchEvent(event: OverlayEvent) {
        eventDispatcher.dispatch(event)
    }
}
