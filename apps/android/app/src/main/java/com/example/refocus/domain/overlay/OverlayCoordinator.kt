package com.example.refocus.domain.overlay

import android.util.Log
import com.example.refocus.core.model.Customize
import com.example.refocus.core.model.SessionEventType
import com.example.refocus.core.model.SuggestionDecision
import com.example.refocus.core.model.SuggestionMode
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.isActive
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

        // 「停止猶予を含む論理セッション」を復元するためにどれだけ遡るか（必要なら後で調整）
        private const val BOOTSTRAP_LOOKBACK_HOURS = 48L
    }

    private val sessionTracker = OverlaySessionTracker(timeSource)

    /**
     * 「この論理セッションに対する提案ゲート」。
     * - disabledForThisSession: このセッションではもう提案しない
     * - lastDecisionAtMillis: Snooze/Dismiss が最後に行われた時刻（クールダウンは Customize から再解釈）
     */
    private data class SessionSuggestionGate(
        val disabledForThisSession: Boolean = false,
        val lastDecisionElapsedMillis: Long? = null,
    )

    private data class SessionBootstrapFromTimeline(
        val initialElapsedMillis: Long,
        val isOngoingSession: Boolean,
        val gate: SessionSuggestionGate,
    )

    @Volatile
    private var currentForegroundPackage: String? = null

    @Volatile
    private var overlayPackage: String? = null

    @Volatile
    private var overlayCustomize: Customize = Customize()

    @Volatile
    private var showSuggestionJob: Job? = null

    @Volatile
    private var sessionSuggestionGate: SessionSuggestionGate = SessionSuggestionGate()

    @Volatile
    private var isSuggestionOverlayShown: Boolean = false

    @Volatile
    private var currentSuggestionId: Long? = null

    @Volatile
    private var overlayState: OverlayState = OverlayState.Idle

    private val stateMachine = OverlayStateMachine()

    // SettingsDataStore からの設定 Flow を shareIn して共有する
    private val customizeFlow: SharedFlow<Customize> =
        settingsRepository.observeOverlaySettings()
            .shareIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                replay = 1
            )

    private val screenOnState = MutableStateFlow(true)

    val screenOnFlow: StateFlow<Boolean> get() = screenOnState


    private val trackingPackageState = MutableStateFlow<String?>(null)
    val trackingPackageFlow: StateFlow<String?> get() = trackingPackageState

    private val timerVisibleState = MutableStateFlow(false)
    val timerVisibleFlow: StateFlow<Boolean> get() = timerVisibleState

    /**
     * 「この論理セッションだけ」オーバーレイタイマーを非表示にするためのフラグ。
     * キーは packageName。
     */
    private val timerSuppressedForSession: MutableMap<String, Boolean> = mutableMapOf()

    /**
     * 外側から「画面ON/OFF」を伝えるためのメソッド。
     * BroadcastReceiver 側から呼ばれる。
     */
    fun setScreenOn(isOn: Boolean) {
        screenOnState.value = isOn
    }


    fun currentTrackingPackage(): String? = trackingPackageState.value

    fun currentElapsedMillis(): Long? {
        val pkg = trackingPackageState.value ?: return null
        return sessionTracker.computeElapsedFor(pkg, timeSource.elapsedRealtime())
    }

    /**
     * 現在計測中の論理セッションに対して、オーバーレイタイマーの表示をトグルする。
     *
     * 戻り値: トグル後に「表示」なら true。
     */
    @Synchronized
    fun toggleTimerVisibilityForCurrentSession(): Boolean {
        val pkg = overlayPackage ?: return false
        val suppressed = timerSuppressedForSession[pkg] == true
        val newSuppressed = !suppressed
        timerSuppressedForSession[pkg] = newSuppressed

        return if (newSuppressed) {
            uiController.hideTimer()
            timerVisibleState.value = false
            false
        } else {
            showTimerForPackage(pkg)
            true
        }
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
        trackingPackageState.value = null
        timerVisibleState.value = false
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
        trackingPackageState.value = null
        timerVisibleState.value = false
        timerSuppressedForSession.clear()
        sessionSuggestionGate = SessionSuggestionGate()
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
                trackingPackageState.value = null
                timerVisibleState.value = false
                uiController.hideTimer()
                uiController.hideSuggestion()
                clearSuggestionOverlayState()

                timerSuppressedForSession.clear()
                sessionTracker.clear()
                sessionSuggestionGate = SessionSuggestionGate()
            }

            // Disabled -> Idle（overlayEnabled が true に戻った）
            oldState is OverlayState.Disabled &&
                    newState is OverlayState.Idle &&
                    event is OverlayEvent.SettingsChanged -> {
                Log.d(TAG, "Overlay re-enabled by customize")
            }

            else -> {
                // その他の組み合わせでは特別な副作用は発生させない
            }
        }
    }

    private fun observeOverlaySettings() {
        scope.launch {
            try {
                customizeFlow.collect { settings ->
                    // 変更前の設定を保存
                    val oldSettings = overlayCustomize
                    // 先に overlayCustomize を更新
                    overlayCustomize = settings
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
                        // 「今表示中のターゲット」について、変更後の停止猶予で論理セッションを再解釈して追従する
                        val pkg = overlayPackage
                        if (pkg != null && overlayState is OverlayState.Tracking && screenOnState.value) {
                            val nowMillis = timeSource.nowMillis()
                            // 変更前 tracker が残っていても投影したいので force=true で復元
                            val bootstrap = computeBootstrapFromTimeline(
                                packageName = pkg,
                                customize = settings,
                                nowMillis = nowMillis,
                                force = true,
                            )
                            // tracker を入れ替える（timer は provider 経由なので、ここで再注入すれば表示は追従する）
                            sessionTracker.clear()
                            sessionSuggestionGate = SessionSuggestionGate()
                            val initialElapsed = bootstrap?.initialElapsedMillis ?: 0L
                            sessionTracker.onEnterTargetApp(
                                packageName = pkg,
                                gracePeriodMillis = settings.gracePeriodMillis,
                                initialElapsedIfNew = initialElapsed,
                            )
                            sessionSuggestionGate =
                                if (bootstrap?.isOngoingSession == true) bootstrap.gate else SessionSuggestionGate()
                        } else {
                            // 表示中でなければ単純にクリアだけ
                            sessionTracker.clear()
                            sessionSuggestionGate = SessionSuggestionGate()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "observeOverlaySettings failed", e)
            }
        }
    }

    private fun tickerFlow(periodMs: Long): Flow<Unit> = flow {
        while (currentCoroutineContext().isActive) {
            emit(Unit)
            delay(periodMs)
        }
    }.onStart { emit(Unit) }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun startMonitoringForeground() {
        scope.launch {
            val targetsFlow = targetsRepository.observeTargets()
            val foregroundSampleFlow = customizeFlow
                .map { it.pollingIntervalMillis }
                .distinctUntilChanged()
                .flatMapLatest { interval ->
                    foregroundAppMonitor.foregroundSampleFlow(
                        pollingIntervalMs = interval
                    )
                }
            val screenOnFlow = screenOnState

            var lastForegroundRaw: String? = null
            var lastSample: ForegroundAppMonitor.ForegroundSample? = null

            val tickFlow = tickerFlow(periodMs = 1_000L)
            combine(
                targetsFlow,
                foregroundSampleFlow,
                screenOnFlow,
                tickFlow
            ) { targets, sample, isScreenOn, _ ->
                Triple(targets, sample, isScreenOn)
            }.collect { (targets, sample, isScreenOn) ->
                val foregroundRaw = sample.packageName
                val foregroundPackage = if (isScreenOn) foregroundRaw else null
                Log.d(
                    TAG,
                    "combine: raw=$foregroundRaw, gen=${sample.generation}, screenOn=$isScreenOn, effective=$foregroundPackage, targets=$targets"
                )

                if (foregroundRaw != lastForegroundRaw) {
                    lastForegroundRaw = foregroundRaw
                    try {
                        // ここを別launchで逃がすと順序が崩れ得るため、直列に記録する
                        // Room/DB書き込みはIOに寄せるが、順序は保持する
                        withContext(Dispatchers.IO) {
                            eventRecorder.onForegroundAppChanged(foregroundRaw)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to record foreground app change: $foregroundRaw", e)
                    }
                }

                try {
                    val nowMillis = timeSource.nowMillis()
                    val nowElapsed = timeSource.elapsedRealtime()
                    // --- 追加: 「同一パッケージだが前面復帰した」を検知して、前面安定だけリセットする ---
                    val prevSample = lastSample
                    lastSample = sample
                    val reconfirmed =
                        isScreenOn &&
                                foregroundRaw != null &&
                                prevSample?.packageName == foregroundRaw &&
                                prevSample.generation != sample.generation
                    if (reconfirmed) {
                        val stateSnapshot = overlayState
                        // Tracking 中かつ、いま見ているターゲットと一致しているときだけ適用
                        if (stateSnapshot is OverlayState.Tracking &&
                            stateSnapshot.packageName == foregroundRaw &&
                            overlayPackage == foregroundRaw
                        ) {
                            sessionTracker.onForegroundReconfirmed(
                                packageName = foregroundRaw,
                                nowElapsedRealtime = nowElapsed
                            )
                            Log.d(
                                TAG,
                                "Foreground reconfirmed for $foregroundRaw -> reset stable timer only"
                            )
                        }
                    }

                    val previous = currentForegroundPackage
                    val prevIsTarget = previous != null && previous in targets
                    val nowIsTarget = foregroundPackage != null && foregroundPackage in targets

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
                    sessionSuggestionGate = SessionSuggestionGate()
                }
            }
        }
    }

    private suspend fun onEnterForeground(
        packageName: String,
        nowMillis: Long,
        nowElapsed: Long
    ) {
        val settings = overlayCustomize
        val grace = settings.gracePeriodMillis

        // まだ OverlaySessionTracker がこの app を知らない場合だけ、
        //    Timeline から「論理セッションの累積時間」を初期値として取得
//        val initialFromTimeline = computeInitialElapsedFromTimelineIfNeeded(
//            packageName = packageName,
//            customize = customize,
//            nowMillis = nowMillis,
//        )

        val bootstrap = computeBootstrapFromTimeline(
            packageName = packageName,
            customize = settings,
            nowMillis = nowMillis,
            force = false,
        )
        val initialElapsed = bootstrap?.initialElapsedMillis ?: 0L
        val isNewSession = sessionTracker.onEnterTargetApp(
            packageName = packageName,
            gracePeriodMillis = grace,
            initialElapsedIfNew = initialElapsed,
        )

        if (isNewSession) {
            // セッション開始時のゲートは「投影された論理セッションが継続ならそのゲートを引き継ぐ」
            sessionSuggestionGate =
                if (bootstrap?.isOngoingSession == true) bootstrap.gate else SessionSuggestionGate()

            // 「このセッションのみ非表示」フラグは新規セッション開始時にリセットする
            timerSuppressedForSession[packageName] = false
        }

        overlayPackage = packageName
        trackingPackageState.value = packageName

        if (isTimerSuppressedForCurrentSession(packageName)) {
            timerVisibleState.value = false
            uiController.hideTimer()
        } else {
            showTimerForPackage(packageName)
        }
    }


    private fun onLeaveForeground(
        packageName: String,
        nowMillis: Long,
        nowElapsed: Long
    ) {
        // 先にオーバーレイを閉じる
        if (overlayPackage == packageName) {
            overlayPackage = null
            trackingPackageState.value = null
            timerVisibleState.value = false
            uiController.hideTimer()
            uiController.hideSuggestion()
            clearSuggestionOverlayState()
        }

        // ランタイムのセッション情報を更新
        sessionTracker.onLeaveTargetApp(packageName)

        // ここでは suggestionDisabledForCurrentSession はリセットしない
        //    （猶予時間内の一時離脱は同一セッション扱いにしたい）
    }


    private fun showTimerForPackage(packageName: String) {
        val elapsedProvider: (Long) -> Long = { nowElapsedRealtime ->
            sessionTracker.computeElapsedFor(packageName, nowElapsedRealtime) ?: 0L
        }
        timerVisibleState.value = true
        uiController.showTimer(
            elapsedMillisProvider = elapsedProvider,
            onPositionChanged = ::onOverlayPositionChanged
        )
    }

    private fun isTimerSuppressedForCurrentSession(packageName: String): Boolean {
        return timerSuppressedForSession[packageName] == true
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

    private fun suggestionTimeoutMillis(customize: Customize): Long {
        val seconds = customize.suggestionTimeoutSeconds.coerceAtLeast(0)
        return seconds.toLong() * 1_000L
    }

    private fun suggestionCooldownMillis(customize: Customize): Long {
        val seconds = customize.suggestionCooldownSeconds.coerceAtLeast(0)
        return seconds.toLong() * 1_000L
    }

    private fun suggestionInteractionLockoutMillis(customize: Customize): Long {
        return customize.suggestionInteractionLockoutMillis.coerceAtLeast(0L)
    }

    private fun handleSuggestionSnooze() {
        clearSuggestionOverlayState()

        val pkg = overlayPackage
        if (pkg == null) {
            Log.w(TAG, "handleSuggestionSnooze: overlayPackage=null; gate not updated")
            return
        }

        val nowElapsed = timeSource.elapsedRealtime()
        val elapsed = sessionTracker.computeElapsedFor(pkg, nowElapsed)
        if (elapsed != null) {
            sessionSuggestionGate = sessionSuggestionGate.copy(lastDecisionElapsedMillis = elapsed)
            Log.d(TAG, "Suggestion decision recorded at sessionElapsed=$elapsed ms")
        } else {
            Log.w(TAG, "handleSuggestionSnooze: no tracker state for $pkg; gate not updated")
        }
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
        sessionSuggestionGate = sessionSuggestionGate.copy(disabledForThisSession = true)
    }

    private fun maybeShowSuggestionIfNeeded(
        packageName: String,
        nowMillis: Long,
        nowElapsedRealtime: Long
    ) {
        if (showSuggestionJob?.isActive == true) return

        val elapsed = sessionTracker.computeElapsedFor(packageName, nowElapsedRealtime) ?: return
        val sinceForegroundMs =
            sessionTracker.sinceForegroundMillis(packageName, nowElapsedRealtime) ?: return

        val input = SuggestionEngine.Input(
            elapsedMillis = elapsed,
            sinceForegroundMillis = sinceForegroundMs,
            customize = overlayCustomize,
            lastDecisionElapsedMillis = sessionSuggestionGate.lastDecisionElapsedMillis,
            isOverlayShown = isSuggestionOverlayShown,
            disabledForThisSession = sessionSuggestionGate.disabledForThisSession,
        )

        if (!suggestionEngine.shouldShow(input)) return

        showSuggestionJob = scope.launch {
            try {
                val suggestions = suggestionsRepository.getSuggestionsSnapshot()
                val selected = suggestionSelector.select(
                    suggestions = suggestions,
                    nowMillis = nowMillis,
                    elapsedMillis = elapsed,
                )
                val hasSuggestion = selected != null

                if (!hasSuggestion && !overlayCustomize.restSuggestionEnabled) {
                    Log.d(TAG, "No suggestion and restSuggestion disabled, skip overlay")
                    return@launch
                }

                val (title, mode, suggestionId) = if (selected != null) {
                    Triple(
                        selected.title,
                        SuggestionMode.Generic,
                        selected.id,
                    )
                } else {
                    Triple(
                        "画面から少し離れて休憩する",
                        SuggestionMode.Rest,
                        0L,
                    )
                }

                val pkg = overlayPackage ?: packageName
                val shown = uiController.showSuggestion(
                    SuggestionOverlayUiModel(
                        title = title,
                        mode = mode,
                        autoDismissMillis = suggestionTimeoutMillis(overlayCustomize),
                        interactionLockoutMillis = suggestionInteractionLockoutMillis(
                            overlayCustomize
                        ),
                        onSnoozeLater = { handleSuggestionSnoozeLater() },
                        onDisableThisSession = { handleSuggestionDisableThisSession() },
                        onDismissOnly = { handleSuggestionDismissOnly() },
                    )
                )
                if (!shown) {
                    Log.w(TAG, "Suggestion overlay was NOT shown (addView failed etc). Will retry.")
                    return@launch
                }
                isSuggestionOverlayShown = true
                currentSuggestionId = suggestionId
                try {
                    eventRecorder.onSuggestionShown(
                        packageName = pkg,
                        suggestionId = suggestionId,
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to record SuggestionShown for $pkg", e)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show suggestion overlay for $packageName", e)
                isSuggestionOverlayShown = false
                currentSuggestionId = null
            } finally {
                showSuggestionJob = null
            }
        }
    }


    private fun clearSuggestionOverlayState() {
        isSuggestionOverlayShown = false
        currentSuggestionId = null
        // クールダウン/セッション抑制は sessionSuggestionGate 側で保持する
    }

    /**
     * まだ OverlaySessionTracker に state がないパッケージについて、
     * 自前の Timeline SessionProjector を使って
     * 「今の設定に従った論理セッションの累積時間」を計算する。
     *
     * - 停止猶予時間を伸ばした結果、昔のセッションが「くっついて 1 本になった」場合、
     *   その全体の duration を返す。
     * - 過去 24 時間分だけ見れば十分、など horizon は任意に絞ってよい。
     */
    private suspend fun computeBootstrapFromTimeline(
        packageName: String,
        customize: Customize,
        nowMillis: Long,
        force: Boolean,
    ): SessionBootstrapFromTimeline? {
        if (!force) {
            // すでにランタイムの tracker が何か知っているなら、再注入は不要
            val nowElapsed = timeSource.elapsedRealtime()
            val already = sessionTracker.computeElapsedFor(packageName, nowElapsed)
            if (already != null) return null
        }
        val startMillis = (nowMillis - BOOTSTRAP_LOOKBACK_HOURS * 60L * 60L * 1000L)
            .coerceAtLeast(0L)
        val events = timelineRepository.getEvents(startMillis = startMillis, endMillis = nowMillis)
        if (events.isEmpty()) return null
        val sessionsWithEvents = SessionProjector.projectSessions(
            events = events,
            targetPackages = setOf(packageName),
            stopGracePeriodMillis = customize.gracePeriodMillis,
            nowMillis = nowMillis,
        )
        val last = sessionsWithEvents.lastOrNull() ?: return null
        // 終了イベントが入っている = すでに論理セッションとしては閉じている
        val ended = last.events.any { it.type == SessionEventType.End }
        if (ended) {
            return SessionBootstrapFromTimeline(
                initialElapsedMillis = 0L,
                isOngoingSession = false,
                gate = SessionSuggestionGate(),
            )
        }
        val duration = SessionDurationCalculator.calculateDurationMillis(
            events = last.events,
            nowMillis = nowMillis,
        ).coerceAtLeast(0L)
        val disabled = last.events.any { it.type == SessionEventType.SuggestionDisabledForSession }
        val lastDecisionAt = last.events
            .filter {
                it.type == SessionEventType.SuggestionSnoozed ||
                        it.type == SessionEventType.SuggestionDismissed
            }
            .maxOfOrNull { it.timestampMillis }

        val lastDecisionElapsed = lastDecisionAt?.let { at ->
            val truncated = last.events.filter { it.timestampMillis <= at }
            SessionDurationCalculator.calculateDurationMillis(
                events = truncated,
                nowMillis = at
            ).coerceAtLeast(0L)
        }

        return SessionBootstrapFromTimeline(
            initialElapsedMillis = duration,
            isOngoingSession = true,
            gate = SessionSuggestionGate(
                disabledForThisSession = disabled,
                lastDecisionElapsedMillis = lastDecisionElapsed,
            )
        )
    }
}
