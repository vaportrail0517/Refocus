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
    private val stateMachine = OverlayStateMachine()

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

    private val customizeFlow: SharedFlow<Customize> =
        settingsRepository.observeOverlaySettings()
            .shareIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                replay = 1,
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
    private var observeSettingsJob: Job? = null

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
        observeOverlaySettings()
        foregroundTrackingOrchestrator.start(
            customizeFlow = customizeFlow,
            screenOnFlow = screenOnFlowInternal,
        )
    }

    /**
     * Service 破棄時に呼ぶ．
     * オーバーレイ表示を片付ける．
     */
    fun stop() {
        presentationTickJob?.cancel()
        presentationTickJob = null

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
     * 並行アクセスを避けるため synchronized にしている．
     */
    @Synchronized
    private fun dispatchEvent(event: OverlayEvent) {
        val oldState = runtimeState.value.overlayState
        val newState = stateMachine.transition(oldState, event)
        if (newState == oldState) {
            return
        }

        runtimeState.update { it.copy(overlayState = newState) }
        RefocusLog.d(TAG) { "overlayState: $oldState -> $newState by $event" }
        handleStateChange(oldState, newState, event)
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
                    onEnterForeground(
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

                onLeaveForeground(
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
                onLeaveForeground(
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

    private fun observeOverlaySettings() {
        if (observeSettingsJob?.isActive == true) return

        observeSettingsJob = scope.launch {
            try {
                customizeFlow.collect { settings ->
                    val oldSettings = runtimeState.value.customize

                    // 先に customize スナップショットを更新（Provider などが最新設定を読めるようにする）
                    runtimeState.update { it.copy(customize = settings) }

                    // タイマー表示モードが変わった場合は，日次集計キャッシュを無効化する
                    if (settings.timerTimeMode != oldSettings.timerTimeMode) {
                        dailyUsageUseCase.invalidate()
                    }

                    // 設定変更イベントとしてタイムラインに記録
                    dispatchEvent(OverlayEvent.SettingsChanged(settings))

                    // UI 側の見た目反映
                    uiController.applySettings(settings)

                    // 停止猶予時間が変わったかどうかを判定
                    val graceChanged = settings.gracePeriodMillis != oldSettings.gracePeriodMillis
                    if (graceChanged) {
                        RefocusLog.d(TAG) {
                            "gracePeriodMillis changed: ${oldSettings.gracePeriodMillis} -> ${settings.gracePeriodMillis}"
                        }

                        // 停止猶予時間が変わると，日次累計の再投影結果も変わり得るためキャッシュを無効化する
                        dailyUsageUseCase.invalidate()

                        // 「今表示中のターゲット」について，変更後の停止猶予で論理セッションを再解釈して追従する
                        val pkg = runtimeState.value.overlayPackage
                        val stateSnapshot = runtimeState.value
                        if (pkg != null && stateSnapshot.overlayState is OverlayState.Tracking && stateSnapshot.isScreenOn) {
                            val nowMillis = timeSource.nowMillis()

                            // 変更前 tracker が残っていても投影したいので force=true で復元
                            val bootstrap = sessionBootstrapper.computeBootstrapFromTimeline(
                                packageName = pkg,
                                customize = settings,
                                nowMillis = nowMillis,
                                force = true,
                                sessionTracker = sessionTracker,
                            )

                            // tracker を入れ替える（timer は provider 経由なので，ここで再注入すれば表示は追従する）
                            sessionTracker.clear()
                            suggestionOrchestrator.resetGate()

                            val initialElapsed = bootstrap?.initialElapsedMillis ?: 0L
                            sessionTracker.onEnterTargetApp(
                                packageName = pkg,
                                gracePeriodMillis = settings.gracePeriodMillis,
                                initialElapsedIfNew = initialElapsed,
                            )
                            suggestionOrchestrator.restoreGateIfOngoing(bootstrap)
                        } else {
                            // 表示中でなければ単純にクリアだけ
                            sessionTracker.clear()
                            suggestionOrchestrator.resetGate()
                        }
                    }
                }
            } catch (e: Exception) {
                RefocusLog.e(TAG, e) { "observeOverlaySettings failed" }
            }
        }
    }

    private suspend fun onEnterForeground(
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

    private fun onLeaveForeground(
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
            uiController.hideTimer()
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
