package com.example.refocus.domain.overlay

import android.util.Log
import com.example.refocus.core.model.OverlayEvent
import com.example.refocus.core.model.OverlayState
import com.example.refocus.core.model.OverlaySuggestionMode
import com.example.refocus.core.model.Settings
import com.example.refocus.core.model.SuggestionDecision
import com.example.refocus.core.util.TimeSource
import com.example.refocus.data.repository.SettingsRepository
import com.example.refocus.data.repository.SuggestionsRepository
import com.example.refocus.data.repository.TargetsRepository
import com.example.refocus.data.repository.TimelineRepository
import com.example.refocus.domain.session.SessionDurationCalculator
import com.example.refocus.domain.suggestion.SuggestionEngine
import com.example.refocus.domain.suggestion.SuggestionSelector
import com.example.refocus.domain.timeline.EventRecorder
import com.example.refocus.domain.timeline.SessionProjector
import com.example.refocus.system.monitor.ForegroundAppMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * - 前面アプリの監視
 * - タイマーオーバーレイ / 提案オーバーレイの制御
 * を担当する。
 *
 * Android Service / Notification / BroadcastReceiver などの OS 依存部分は
 * OverlayService 側に残す。
 */
class OverlayCoordinator(
    private val scope: CoroutineScope,
    private val timeSource: TimeSource,
    private val targetsRepository: TargetsRepository,
    private val settingsRepository: SettingsRepository,
    private val suggestionsRepository: SuggestionsRepository,
    private val foregroundAppMonitor: ForegroundAppMonitor,
    private val suggestionEngine: SuggestionEngine,
    private val suggestionSelector: SuggestionSelector,
    private val uiController: OverlayUiGateway,
    private val eventRecorder: EventRecorder,
    private val timelineRepository: TimelineRepository,
) {
    companion object {
        private const val TAG = "OverlayCoordinator"
    }

    private val sessionTracker = OverlaySessionTracker(timeSource)

    @Volatile
    private var currentForegroundPackage: String? = null

    @Volatile
    private var overlayPackage: String? = null

    @Volatile
    private var overlaySettings: Settings = Settings()

    @Volatile
    private var suggestionSnoozedUntilMillis: Long? = null

    @Volatile
    private var isSuggestionOverlayShown: Boolean = false

    @Volatile
    private var currentSuggestionId: Long? = null

    /**
     * 「このセッション中は提案を出さない」が押されたかどうか
     */
    @Volatile
    private var suggestionDisabledForCurrentSession: Boolean = false

    @Volatile
    private var overlayState: OverlayState = OverlayState.Idle

    private val stateMachine = OverlayStateMachine()

    // SettingsDataStore からの設定 Flow を shareIn して共有する
    private val settingsFlow: SharedFlow<Settings> =
        settingsRepository.observeOverlaySettings()
            .shareIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                replay = 1
            )

    private val screenOnState = MutableStateFlow(true)

    val screenOnFlow: StateFlow<Boolean> get() = screenOnState

    /**
     * 外側から「画面ON/OFF」を伝えるためのメソッド。
     * BroadcastReceiver 側から呼ばれる。
     */
    fun setScreenOn(isOn: Boolean) {
        screenOnState.value = isOn
    }

    /**
     * 画面OFF時に呼び出す。前面にいた対象アプリを「離脱」として扱う。
     */
    fun onScreenOff() {
        val nowMillis = timeSource.nowMillis()
        val nowElapsed = timeSource.elapsedRealtime()
        Log.d(TAG, "onScreenOff: screen off event")
        dispatchEvent(
            OverlayEvent.ScreenOff(
                nowMillis = nowMillis,
                nowElapsedRealtime = nowElapsed,
            )
        )
        // 現在の前面アプリ情報はリセット
        currentForegroundPackage = null
    }

    /**
     * 監視と設定購読を開始。
     * OverlayService.onCreate から呼ばれる想定。
     */
    fun start() {
        observeOverlaySettings()
        startMonitoringForeground()
    }

    /**
     * Service 破棄時に呼ぶ。
     * オーバーレイ表示を片付ける。
     */
    fun stop() {
        uiController.hideTimer()
        uiController.hideSuggestion()
        clearSuggestionOverlayState()
        overlayState = OverlayState.Idle
        currentForegroundPackage = null
        overlayPackage = null
        suggestionSnoozedUntilMillis = null
        suggestionDisabledForCurrentSession = false
        // オーバーレイ用セッションもクリア
        sessionTracker.clear()
        // scope 自体のキャンセルは Service 側で行う
    }

    /**
     * OverlayStateMachine へのイベント送出。
     * 並行アクセスを避けるため synchronized にしている。
     */
    @Synchronized
    private fun dispatchEvent(event: OverlayEvent) {
        val oldState = overlayState
        val newState = stateMachine.transition(oldState, event)
        if (newState == oldState) {
            // 状態変化がない場合は何もしない
            return
        }
        overlayState = newState
        Log.d(TAG, "overlayState: $oldState -> $newState by $event")
        handleStateChange(oldState, newState, event)
    }

    /**
     * 状態変化に応じて OverlayController に副作用を打つ。
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
                overlayPackage = null
                uiController.hideTimer()
                uiController.hideSuggestion()
                clearSuggestionOverlayState()

                sessionTracker.clear()
                suggestionDisabledForCurrentSession = false
            }

            // Disabled -> Idle（overlayEnabled が true に戻った）
            oldState is OverlayState.Disabled &&
                    newState is OverlayState.Idle &&
                    event is OverlayEvent.SettingsChanged -> {
                Log.d(TAG, "Overlay re-enabled by settings")
            }

            else -> {
                // その他の組み合わせでは特別な副作用は発生させない
            }
        }
    }

    private fun observeOverlaySettings() {
        scope.launch {
            try {
                settingsFlow.collect { settings ->
                    // 変更前の設定を保存
                    val oldSettings = overlaySettings
                    // 先に overlaySettings を更新
                    overlaySettings = settings
                    // 設定変更イベントとしてタイムラインに記録
                    dispatchEvent(OverlayEvent.SettingsChanged(settings))
                    // UI 側の見た目反映
                    uiController.applySettings(settings)
                    // 停止猶予時間が変わったかどうかを判定
                    val graceChanged = settings.gracePeriodMillis != oldSettings.gracePeriodMillis
                    if (graceChanged) {
                        Log.d(
                            TAG,
                            "gracePeriodMillis changed: ${oldSettings.gracePeriodMillis} -> ${settings.gracePeriodMillis}"
                        )
                        // 1) ランタイムのセッション解釈を一旦リセット
                        sessionTracker.clear()
                        suggestionDisabledForCurrentSession = false
                        // 2) 今出ているオーバーレイは一度全部閉じる
                        overlayPackage?.let {
                            uiController.hideTimer()
                            uiController.hideSuggestion()
                            clearSuggestionOverlayState()
                            overlayPackage = null
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "observeOverlaySettings failed", e)
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun startMonitoringForeground() {
        scope.launch {
            val targetsFlow = targetsRepository.observeTargets()
            val foregroundFlow = settingsFlow
                .flatMapLatest { settings ->
                    foregroundAppMonitor.foregroundAppFlow(
                        pollingIntervalMs = settings.pollingIntervalMillis
                    )
                }
            val screenOnFlow = screenOnState

            var lastForegroundRaw: String? = null

            combine(
                targetsFlow,
                foregroundFlow,
                screenOnFlow
            ) { targets, foregroundRaw, isScreenOn ->
                Triple(targets, foregroundRaw, isScreenOn)
            }.collectLatest { (targets, foregroundRaw, isScreenOn) ->
                val foregroundPackage = if (isScreenOn) foregroundRaw else null
                Log.d(
                    TAG,
                    "combine: raw=$foregroundRaw, screenOn=$isScreenOn, effective=$foregroundPackage, targets=$targets"
                )

                if (foregroundRaw != lastForegroundRaw) {
                    lastForegroundRaw = foregroundRaw
                    scope.launch {
                        try {
                            eventRecorder.onForegroundAppChanged(foregroundRaw)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to record foreground app change: $foregroundRaw", e)
                        }
                    }
                }

                try {
                    val previous = currentForegroundPackage
                    val prevIsTarget = previous != null && previous in targets
                    val nowIsTarget = foregroundPackage != null && foregroundPackage in targets
                    val nowMillis = timeSource.nowMillis()
                    val nowElapsed = timeSource.elapsedRealtime()

                    currentForegroundPackage = foregroundPackage

                    when {
                        // 非対象 → 対象
                        !prevIsTarget && nowIsTarget -> {
                            dispatchEvent(
                                OverlayEvent.EnterTargetApp(
                                    packageName = foregroundPackage!!,
                                    nowMillis = nowMillis,
                                    nowElapsedRealtime = nowElapsed,
                                )
                            )
                        }

                        // 対象 → 非対象
                        prevIsTarget && !nowIsTarget -> {
                            dispatchEvent(
                                OverlayEvent.LeaveTargetApp(
                                    packageName = previous!!,
                                    nowMillis = nowMillis,
                                    nowElapsedRealtime = nowElapsed,
                                )
                            )
                        }

                        // 対象A → 対象B
                        prevIsTarget && previous != foregroundPackage -> {
                            dispatchEvent(
                                OverlayEvent.LeaveTargetApp(
                                    packageName = previous!!,
                                    nowMillis = nowMillis,
                                    nowElapsedRealtime = nowElapsed,
                                )
                            )
                            dispatchEvent(
                                OverlayEvent.EnterTargetApp(
                                    packageName = foregroundPackage!!,
                                    nowMillis = nowMillis,
                                    nowElapsedRealtime = nowElapsed,
                                )
                            )
                        }

                        else -> {
                            // 非対象→非対象 / 対象→同じ対象 は何もしない
                        }
                    }

                    // Suggestion は「Tracking 中だけ」評価する
                    val stateSnapshot = overlayState
                    if (stateSnapshot is OverlayState.Tracking && foregroundPackage != null) {
                        maybeShowSuggestionIfNeeded(
                            packageName = foregroundPackage,
                            nowMillis = nowMillis,
                            nowElapsedRealtime = nowElapsed
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in startMonitoring loop", e)
                    withContext(Dispatchers.Main) {
                        uiController.hideTimer()
                        uiController.hideSuggestion()
                    }
                    overlayPackage = null
                    clearSuggestionOverlayState()
                    suggestionDisabledForCurrentSession = false
                }
            }
        }
    }

    private suspend fun onEnterForeground(
        packageName: String,
        nowMillis: Long,
        nowElapsed: Long
    ) {
        val settings = overlaySettings ?: return
        val grace = settings.gracePeriodMillis

        // まだ OverlaySessionTracker がこの app を知らない場合だけ、
        //    Timeline から「論理セッションの累積時間」を初期値として取得
        val initialFromTimeline = computeInitialElapsedFromTimelineIfNeeded(
            packageName = packageName,
            settings = settings,
            nowMillis = nowMillis,
        )

        val isNewSession = sessionTracker.onEnterTargetApp(
            packageName = packageName,
            gracePeriodMillis = grace,
            initialElapsedIfNew = initialFromTimeline,
        )

        if (isNewSession) {
            // 「論理的に新しいセッション」のときだけ提案抑制フラグをリセット
            suggestionDisabledForCurrentSession = false
        }

        overlayPackage = packageName

        val elapsedProvider: (Long) -> Long = { nowElapsedRealtime ->
            sessionTracker.computeElapsedFor(packageName, nowElapsedRealtime) ?: 0L
        }

        uiController.showTimer(
            elapsedMillisProvider = elapsedProvider,
            onPositionChanged = ::onOverlayPositionChanged
        )
    }


    private fun onLeaveForeground(
        packageName: String,
        nowMillis: Long,
        nowElapsed: Long
    ) {
        // 先にオーバーレイを閉じる
        if (overlayPackage == packageName) {
            overlayPackage = null
            uiController.hideTimer()
            uiController.hideSuggestion()
            clearSuggestionOverlayState()
        }

        // ランタイムのセッション情報を更新
        sessionTracker.onLeaveTargetApp(packageName)

        // ここでは suggestionDisabledForCurrentSession はリセットしない
        //    （猶予時間内の一時離脱は同一セッション扱いにしたい）
    }


    private fun onOverlayPositionChanged(x: Int, y: Int) {
        scope.launch {
            try {
                settingsRepository.updateOverlaySettings { current ->
                    current.copy(positionX = x, positionY = y)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save overlay position", e)
            }
        }
    }

    private fun suggestionTimeoutMillis(settings: Settings): Long {
        val seconds = settings.suggestionTimeoutSeconds.coerceAtLeast(0)
        return seconds.toLong() * 1_000L
    }

    private fun suggestionCooldownMillis(settings: Settings): Long {
        val seconds = settings.suggestionCooldownSeconds.coerceAtLeast(0)
        return seconds.toLong() * 1_000L
    }

    private fun suggestionInteractionLockoutMillis(settings: Settings): Long {
        return settings.suggestionInteractionLockoutMillis.coerceAtLeast(0L)
    }

    private fun handleSuggestionSnooze() {
        clearSuggestionOverlayState()
        val now = timeSource.nowMillis()
        val cooldownMs = suggestionCooldownMillis(overlaySettings)
        suggestionSnoozedUntilMillis = now + cooldownMs
        Log.d(TAG, "Suggestion snoozed until $suggestionSnoozedUntilMillis")
    }

    private fun handleSuggestionSnoozeLater() {
        val pkg = overlayPackage
        val suggestionId = currentSuggestionId ?: 0L

        if (pkg != null) {
            scope.launch {
                try {
                    eventRecorder.onSuggestionDecision(
                        packageName = pkg,
                        suggestionId = suggestionId,
                        decision = SuggestionDecision.Snoozed,
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to record SuggestionSnoozed for $pkg", e)
                }
            }
        }
        handleSuggestionSnooze()
    }

    private fun handleSuggestionDismissOnly() {
        val pkg = overlayPackage
        val suggestionId = currentSuggestionId ?: 0L

        if (pkg != null) {
            scope.launch {
                try {
                    eventRecorder.onSuggestionDecision(
                        packageName = pkg,
                        suggestionId = suggestionId,
                        decision = SuggestionDecision.Dismissed,
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to record SuggestionDismissed for $pkg", e)
                }
            }
        }
        handleSuggestionSnooze()
    }

    private fun handleSuggestionDisableThisSession() {
        clearSuggestionOverlayState()
        val packageName = overlayPackage ?: return
        val suggestionId = currentSuggestionId ?: 0L

        scope.launch {
            try {
                eventRecorder.onSuggestionDecision(
                    packageName = packageName,
                    suggestionId = suggestionId,
                    decision = SuggestionDecision.DisabledForSession,
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to record SuggestionDisabledForSession for $packageName", e)
            }
        }

        // 「このセッション中は提案を出さない」を Coordinator 内で保持
        suggestionDisabledForCurrentSession = true
    }

    private fun maybeShowSuggestionIfNeeded(
        packageName: String,
        nowMillis: Long,
        nowElapsedRealtime: Long
    ) {
        // SessionTracker から「セッション累積経過時間」を取得
        val elapsed = sessionTracker.computeElapsedFor(
            packageName = packageName,
            nowElapsedRealtime = nowElapsedRealtime,
        ) ?: return

        // 「最後に前面になってからの経過時間」もトラッカーから取得
        val sinceForegroundMs = sessionTracker.sinceForegroundMillis(
            packageName = packageName,
            nowElapsedRealtime = nowElapsedRealtime,
        ) ?: elapsed

        val input = SuggestionEngine.Input(
            elapsedMillis = elapsed,
            sinceForegroundMillis = sinceForegroundMs,
            settings = overlaySettings,
            nowMillis = nowMillis,
            snoozedUntilMillis = suggestionSnoozedUntilMillis,
            isOverlayShown = isSuggestionOverlayShown,
            disabledForThisSession = suggestionDisabledForCurrentSession,
        )

        if (!suggestionEngine.shouldShow(input)) {
            return
        }

        // （この先は今の実装のままで OK: suggestionsRepository / suggestionSelector / eventRecorder など）
        scope.launch {
            try {
                val suggestions = suggestionsRepository.observeSuggestions().firstOrNull().orEmpty()
                val selected = suggestionSelector.select(
                    suggestions = suggestions,
                    nowMillis = nowMillis,
                    elapsedMillis = elapsed,
                )
                val hasSuggestion = selected != null

                if (!hasSuggestion && !overlaySettings.restSuggestionEnabled) {
                    Log.d(TAG, "No suggestion and restSuggestion disabled, skip overlay")
                    return@launch
                }

                val (title, mode, suggestionId) = if (selected != null) {
                    Triple(
                        selected.title,
                        OverlaySuggestionMode.Goal,
                        selected.id,
                    )
                } else {
                    Triple(
                        "画面から少し離れて休憩する",
                        OverlaySuggestionMode.Rest,
                        0L,
                    )
                }

                isSuggestionOverlayShown = true
                currentSuggestionId = suggestionId

                val pkg = overlayPackage ?: packageName

                scope.launch {
                    try {
                        eventRecorder.onSuggestionShown(
                            packageName = pkg,
                            suggestionId = suggestionId,
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to record SuggestionShown for $pkg", e)
                    }
                }

                uiController.showSuggestion(
                    SuggestionOverlayUiModel(
                        title = title,
                        mode = mode,
                        autoDismissMillis = suggestionTimeoutMillis(overlaySettings),
                        interactionLockoutMillis = suggestionInteractionLockoutMillis(
                            overlaySettings
                        ),
                        onSnoozeLater = { handleSuggestionSnoozeLater() },
                        onDisableThisSession = { handleSuggestionDisableThisSession() },
                        onDismissOnly = { handleSuggestionDismissOnly() },
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show suggestion overlay for $packageName", e)
                isSuggestionOverlayShown = false
            }
        }
    }


    private fun clearSuggestionOverlayState() {
        isSuggestionOverlayShown = false
        currentSuggestionId = null
        // suggestionSnoozedUntilMillis はここでは触らない
    }

    /**
     * まだ OverlaySessionTracker に state がないパッケージについて、
     * 自前の Timeline + SessionProjector を使って
     * 「今の設定に従った論理セッションの累積時間」を計算する。
     *
     * - 停止猶予時間を伸ばした結果、昔のセッションが「くっついて 1 本になった」場合、
     *   その全体の duration を返す。
     * - 過去 24 時間分だけ見れば十分、など horizon は任意に絞ってよい。
     */
    private suspend fun computeInitialElapsedFromTimelineIfNeeded(
        packageName: String,
        settings: Settings,
        nowMillis: Long,
    ): Long {
        // すでにランタイムの tracker が何か知っているなら、再注入は不要
        val nowElapsed = timeSource.elapsedRealtime()
        val already = sessionTracker.computeElapsedFor(packageName, nowElapsed)
        if (already != null) return 0L

        // どれくらい遡るかはトレードオフ：とりあえず最近 24h を見る例
        val horizonHours = 24L
        val startMillis = (nowMillis - horizonHours * 60L * 60L * 1000L)
            .coerceAtLeast(0L)

        val events = timelineRepository.getEvents(
            startMillis = startMillis,
            endMillis = nowMillis,
        )
        if (events.isEmpty()) return 0L

        val sessionsWithEvents = SessionProjector.projectSessions(
            events = events,
            targetPackages = setOf(packageName),
            stopGracePeriodMillis = settings.gracePeriodMillis,
            nowMillis = nowMillis,
        )
        val last = sessionsWithEvents.lastOrNull() ?: return 0L

        // 「論理セッション last に含まれる実際のアクティブ時間」を計算
        val duration = SessionDurationCalculator.calculateDurationMillis(
            events = last.events,
            nowMillis = nowMillis,
        )

        return duration.coerceAtLeast(0L)
    }
}
