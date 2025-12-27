package com.example.refocus.domain.overlay

import com.example.refocus.core.logging.RefocusLog
import com.example.refocus.core.model.Customize
import com.example.refocus.core.model.TimerTimeMode
import com.example.refocus.core.model.TimerVisualTimeBasis
import com.example.refocus.core.util.TimeSource
import com.example.refocus.domain.gateway.ForegroundAppObserver
import com.example.refocus.domain.repository.SettingsRepository
import com.example.refocus.domain.repository.SuggestionsRepository
import com.example.refocus.domain.repository.TargetsRepository
import com.example.refocus.domain.repository.TimelineRepository
import com.example.refocus.domain.settings.SettingsCommand
import com.example.refocus.domain.suggestion.SuggestionEngine
import com.example.refocus.domain.suggestion.SuggestionSelector
import com.example.refocus.domain.timeline.EventRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collect
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
class OverlayCoordinator(
    private val scope: CoroutineScope,
    private val timeSource: TimeSource,
    private val targetsRepository: TargetsRepository,
    private val settingsRepository: SettingsRepository,
    private val settingsCommand: SettingsCommand,
    private val suggestionsRepository: SuggestionsRepository,
    private val foregroundAppObserver: ForegroundAppObserver,
    private val suggestionEngine: SuggestionEngine,
    private val suggestionSelector: SuggestionSelector,
    private val uiController: OverlayUiGateway,
    private val eventRecorder: EventRecorder,
    private val timelineRepository: TimelineRepository,
) {
    companion object {
        private const val TAG = "OverlayCoordinator"

        // 「停止猶予を含む論理セッション」を復元するためにどれだけ遡るか（必要なら後で調整）
        private const val BOOTSTRAP_LOOKBACK_HOURS = 48L
    }

    private val sessionTracker = OverlaySessionTracker(timeSource)

    private val sessionBootstrapper = SessionBootstrapper(
        timeSource = timeSource,
        timelineRepository = timelineRepository,
        lookbackHours = BOOTSTRAP_LOOKBACK_HOURS,
    )

    private val dailyUsageUseCase = DailyUsageUseCase(
        scope = scope,
        timeSource = timeSource,
        timelineRepository = timelineRepository,
        lookbackHours = BOOTSTRAP_LOOKBACK_HOURS,
    )

    private val runtimeState = MutableStateFlow(OverlayRuntimeState())
    val runtimeStateFlow: StateFlow<OverlayRuntimeState> get() = runtimeState

    private val stateTransitioner = OverlayStateTransitioner(
        runtimeState = runtimeState,
        onStateChanged = ::handleStateChange,
    )

    private val timerDisplayCalculator = OverlayTimerDisplayCalculator(
        timeSource = timeSource,
        sessionTracker = sessionTracker,
        dailyUsageUseCase = dailyUsageUseCase,
        customizeProvider = { runtimeState.value.customize },
        lastTargetPackagesProvider = { runtimeState.value.lastTargetPackages },
    )

    private val suggestionOrchestrator = SuggestionOrchestrator(
        scope = scope,
        timeSource = timeSource,
        sessionElapsedProvider = { pkg, nowElapsed ->
            sessionTracker.computeElapsedFor(pkg, nowElapsed)
        },
        suggestionEngine = suggestionEngine,
        suggestionSelector = suggestionSelector,
        suggestionsRepository = suggestionsRepository,
        uiController = uiController,
        eventRecorder = eventRecorder,
        overlayPackageProvider = { runtimeState.value.overlayPackage },
        customizeProvider = { runtimeState.value.customize },
    )



    private val sessionLifecycle = OverlaySessionLifecycle(
        scope = scope,
        timeSource = timeSource,
        runtimeState = runtimeState,
        settingsCommand = settingsCommand,
        uiController = uiController,
        sessionBootstrapper = sessionBootstrapper,
        dailyUsageUseCase = dailyUsageUseCase,
        sessionTracker = sessionTracker,
        timerDisplayCalculator = timerDisplayCalculator,
        suggestionOrchestrator = suggestionOrchestrator,
    )
    private val customizeFlow: SharedFlow<Customize> =
        settingsRepository.observeOverlaySettings()
            .shareIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                replay = 1,
            )


    private val settingsObserver = OverlaySettingsObserver(
        scope = scope,
        timeSource = timeSource,
        customizeFlow = customizeFlow,
        runtimeState = runtimeState,
        dailyUsageUseCase = dailyUsageUseCase,
        uiController = uiController,
        sessionBootstrapper = sessionBootstrapper,
        sessionTracker = sessionTracker,
        suggestionOrchestrator = suggestionOrchestrator,
        dispatchEvent = ::dispatchEvent,
    )

    private val screenOnFlowInternal: StateFlow<Boolean> =
        runtimeState
            .map { it.isScreenOn }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, runtimeState.value.isScreenOn)

    val screenOnFlow: StateFlow<Boolean> get() = screenOnFlowInternal

    private val trackingPackageFlowInternal: StateFlow<String?> =
        runtimeState
            .map { it.trackingPackage }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, runtimeState.value.trackingPackage)

    val trackingPackageFlow: StateFlow<String?> get() = trackingPackageFlowInternal

    private val timerVisibleFlowInternal: StateFlow<Boolean> =
        runtimeState
            .map { it.timerVisible }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, runtimeState.value.timerVisible)

    val timerVisibleFlow: StateFlow<Boolean> get() = timerVisibleFlowInternal

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
            customizeFlow.map { it.touchMode }.distinctUntilChanged(),
            customizeFlow.map { it.timerTimeMode }.distinctUntilChanged(),
            presentationTick,
        ) { trackingPkg, timerVisible, touchMode, timerTimeMode, _ ->
            val displayMillis = trackingPkg?.let { timerDisplayCalculator.currentTimerDisplayMillis(it) }
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
            initialValue = OverlayPresentationState(
                trackingPackage = runtimeState.value.trackingPackage,
                timerDisplayMillis = runtimeState.value.trackingPackage?.let {
                    timerDisplayCalculator.currentTimerDisplayMillis(it)
                },
                isTimerVisible = runtimeState.value.trackingPackage != null && runtimeState.value.timerVisible,
                touchMode = runtimeState.value.customize.touchMode,
                timerTimeMode = runtimeState.value.customize.timerTimeMode,
            ),
        )

    val presentationStateFlow: StateFlow<OverlayPresentationState> get() = presentationStateFlowInternal

    fun currentPresentationState(): OverlayPresentationState = presentationStateFlowInternal.value

    private val foregroundTrackingOrchestrator = ForegroundTrackingOrchestrator(
        scope = scope,
        timeSource = timeSource,
        targetsRepository = targetsRepository,
        foregroundAppObserver = foregroundAppObserver,
        runtimeState = runtimeState,
        sessionTracker = sessionTracker,
        dailyUsageUseCase = dailyUsageUseCase,
        suggestionOrchestrator = suggestionOrchestrator,
        uiController = uiController,
        eventRecorder = eventRecorder,
        dispatchEvent = ::dispatchEvent,
    )

    @Volatile
    private var foregroundGateJob: Job? = null

    /**
     * 外側から「画面ON/OFF」を伝えるためのメソッド．
     * BroadcastReceiver 側から呼ばれる．
     */
    fun setScreenOn(isOn: Boolean) {
        runtimeState.update { it.copy(isScreenOn = isOn) }
    }

    fun currentTrackingPackage(): String? = runtimeState.value.trackingPackage

    fun currentElapsedMillis(): Long? {
        val pkg = runtimeState.value.trackingPackage ?: return null
        return sessionTracker.computeElapsedFor(pkg, timeSource.elapsedRealtime())
    }

    /**
     * 通知など，オーバーレイ表示以外から参照される「タイマー表示時間」．
     * 現在の timerTimeMode に応じて，オーバーレイと同じ意味の時間を返す．
     */
    fun currentTimerDisplayMillis(): Long? =
        timerDisplayCalculator.currentTimerDisplayMillis(runtimeState.value.trackingPackage)



    /**
     * 現在計測中の論理セッションに対して，オーバーレイタイマーの表示をトグルする．
     *
     * 戻り値: トグル後に「表示」なら true．
     */
    @Synchronized
    fun toggleTimerVisibilityForCurrentSession(): Boolean =
        sessionLifecycle.toggleTimerVisibilityForCurrentSession()


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
    }

    private fun startForegroundTrackingGate() {
        // overlayEnabled の変化に合わせて前面監視ループを開始・停止する
        if (foregroundGateJob?.isActive == true) return
        foregroundGateJob = scope.launch {
            customizeFlow
                .map { it.overlayEnabled }
                .distinctUntilChanged()
                .collect { enabled ->
                    if (enabled) {
                        foregroundTrackingOrchestrator.start(
                            customizeFlow = customizeFlow,
                            screenOnFlow = screenOnFlowInternal,
                        )
                    } else {
                        foregroundTrackingOrchestrator.stop()
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

        foregroundTrackingOrchestrator.stop()

        uiController.hideTimer()
        uiController.hideSuggestion()
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
        presentationTickJob = scope.launch {
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
     * 状態遷移は OverlayStateTransitioner に集約して直列化する．
     */
    private fun dispatchEvent(event: OverlayEvent) {
        stateTransitioner.dispatch(event)
    }

    /**
     * 状態変化に応じて OverlayController に副作用を打つ．
     */
    private fun handleStateChange(
        oldState: OverlayState,
        newState: OverlayState,
        event: OverlayEvent,
    ) {
        when {
            // Idle -> Tracking（対象アプリに入った）
            oldState is OverlayState.Idle &&
                    newState is OverlayState.Tracking &&
                    event is OverlayEvent.EnterTargetApp -> {

                scope.launch {
                    sessionLifecycle.onEnterForeground(
                        packageName = event.packageName,
                        nowMillis = event.nowMillis,
                        nowElapsed = event.nowElapsedRealtime,
                    )
                }
            }

            // Tracking -> Idle（対象アプリから出た）
            oldState is OverlayState.Tracking &&
                    newState is OverlayState.Idle &&
                    event is OverlayEvent.LeaveTargetApp -> {

                sessionLifecycle.onLeaveForeground(
                    packageName = event.packageName,
                    nowMillis = event.nowMillis,
                    nowElapsed = event.nowElapsedRealtime,
                )
            }

            // Tracking -> Idle（画面 OFF による離脱）
            oldState is OverlayState.Tracking &&
                    newState is OverlayState.Idle &&
                    event is OverlayEvent.ScreenOff -> {

                val packageName = oldState.packageName
                sessionLifecycle.onLeaveForeground(
                    packageName = packageName,
                    nowMillis = event.nowMillis,
                    nowElapsed = event.nowElapsedRealtime,
                )
            }

            // 何らかの理由で Disabled に落ちたとき（overlayEnabled=false など）
            newState is OverlayState.Disabled -> {
                runtimeState.update {
                    it.copy(
                        overlayPackage = null,
                        trackingPackage = null,
                        timerVisible = false,
                        timerSuppressedForSession = emptyMap(),
                    )
                }
                uiController.hideTimer()
                uiController.hideSuggestion()
                suggestionOrchestrator.onDisabled()

                sessionTracker.clear()
            }

            // Disabled -> Idle（overlayEnabled が true に戻った）
            oldState is OverlayState.Disabled &&
                    newState is OverlayState.Idle &&
                    event is OverlayEvent.SettingsChanged -> {
                RefocusLog.d(TAG) { "Overlay re-enabled by customize" }
            }

            else -> {
                // その他の組み合わせでは特別な副作用は発生させない
            }
        }
    }

}
