package com.example.refocus.domain.overlay.orchestration

import com.example.refocus.core.logging.RefocusLog
import com.example.refocus.core.model.Customize
import com.example.refocus.core.model.MiniGameOrder
import com.example.refocus.core.model.SuggestionDecision
import com.example.refocus.core.model.SuggestionMode
import com.example.refocus.core.model.UiInterruptionSource
import com.example.refocus.core.util.TimeSource
import com.example.refocus.domain.overlay.model.SessionBootstrapFromTimeline
import com.example.refocus.domain.overlay.model.SessionSuggestionGate
import com.example.refocus.domain.overlay.port.MiniGameOverlayUiModel
import com.example.refocus.domain.overlay.port.OverlayUiPort
import com.example.refocus.domain.overlay.port.SuggestionOverlayUiModel
import com.example.refocus.domain.repository.SuggestionsRepository
import com.example.refocus.domain.suggestion.SuggestionEngine
import com.example.refocus.domain.suggestion.SuggestionSelector
import com.example.refocus.domain.timeline.EventRecorder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 提案（Suggestion）オーバーレイの表示と，
 * セッション内ゲート（クールダウン，このセッションではもう出さない等）を管理する．
 *
 * ミニゲームが有効な場合は，設定に応じて「提案の前」または「提案の後」にミニゲームを挟む．
 * ミニゲームと提案の表示中は，overlay セッションの計測を一時停止する（= タイマーが伸びない）．
 */
class SuggestionOrchestrator(
    private val scope: CoroutineScope,
    private val timeSource: TimeSource,
    private val sessionElapsedProvider: (packageName: String, nowElapsedRealtime: Long) -> Long?,
    private val onUiPause: (packageName: String, nowElapsedRealtime: Long) -> Unit,
    private val onUiResume: (packageName: String, nowElapsedRealtime: Long) -> Unit,
    private val suggestionEngine: SuggestionEngine,
    private val suggestionSelector: SuggestionSelector,
    private val suggestionsRepository: SuggestionsRepository,
    private val uiController: OverlayUiPort,
    private val eventRecorder: EventRecorder,
    private val overlayPackageProvider: () -> String?,
    private val customizeProvider: () -> Customize,
) {
    companion object {
        private const val TAG = "SuggestionOrchestrator"
    }

    @Volatile
    private var showSuggestionJob: Job? = null

    @Volatile
    private var showMiniGameJob: Job? = null

    @Volatile
    private var sessionGate: SessionSuggestionGate = SessionSuggestionGate()

    @Volatile
    private var isSuggestionOverlayShown: Boolean = false

    @Volatile
    private var isMiniGameOverlayShown: Boolean = false

    @Volatile
    private var currentSuggestionId: Long? = null

    @Volatile
    private var suggestionEpoch: Long = 0L

    @Volatile
    private var miniGameEpoch: Long = 0L

    @Volatile
    private var suggestionUiInterruptionPackageName: String? = null

    @Volatile
    private var miniGameUiInterruptionPackageName: String? = null

    private fun bumpSuggestionEpoch(): Long =
        synchronized(this) {
            suggestionEpoch += 1L
            suggestionEpoch
        }

    private fun bumpMiniGameEpoch(): Long =
        synchronized(this) {
            miniGameEpoch += 1L
            miniGameEpoch
        }

    fun onDisabled() {
        invalidatePendingAllAndHide()
        resetGate()
    }

    fun resetGate() {
        sessionGate = SessionSuggestionGate()
    }

    /**
     * セッション開始時に呼ばれる。
     * Timeline 投影で「継続セッション」だと判定された場合は，ゲート状態も復元する。
     */
    fun onNewSession(bootstrap: SessionBootstrapFromTimeline?) {
        sessionGate =
            if (bootstrap?.isOngoingSession == true) bootstrap.gate else SessionSuggestionGate()
    }

    /**
     * 停止猶予時間の変更などで tracker を再注入するとき，
     * 「いまのセッションが継続扱い」ならゲートも復元する。
     */
    fun restoreGateIfOngoing(bootstrap: SessionBootstrapFromTimeline?) {
        if (bootstrap?.isOngoingSession == true) {
            sessionGate = bootstrap.gate
        }
    }

    fun clearOverlayState() {
        isSuggestionOverlayShown = false
        currentSuggestionId = null
        // クールダウン / disabledForThisSession は sessionGate 側に保持する
    }

    private fun clearMiniGameOverlayState() {
        isMiniGameOverlayShown = false
    }

    /**
     * 進行中の show を無効化し，表示中の提案 / ミニゲームを確実に閉じる．
     */
    private fun invalidatePendingAllAndHide() {
        invalidatePendingSuggestionAndHide()
        invalidatePendingMiniGameAndHide()
    }

    /**
     * 進行中の show を無効化し，表示中の提案オーバーレイを確実に閉じる．
     *
     * showSuggestion は token を持たないため，epoch をインクリメントして
     * 進行中コルーチンの "遅れて show" を無効化する．
     */
    fun invalidatePendingSuggestionAndHide() {
        bumpSuggestionEpoch()
        showSuggestionJob?.cancel()
        showSuggestionJob = null
        endSuggestionUiInterruptionIfActive()
        uiController.hideSuggestion()
        clearOverlayState()
    }

    /**
     * 進行中のミニゲーム表示を無効化し，表示中なら確実に閉じる．
     */
    fun invalidatePendingMiniGameAndHide() {
        bumpMiniGameEpoch()
        showMiniGameJob?.cancel()
        showMiniGameJob = null
        endMiniGameUiInterruptionIfActive()
        uiController.hideMiniGame()
        clearMiniGameOverlayState()
    }

    /**
     * タイマーが『このセッションでは非表示』になったときに呼ぶ．
     *
     * - 進行中の show 処理をキャンセル
     * - 表示中の提案 / ミニゲームを閉じる
     * - 内部の overlay 状態をクリア
     */
    fun stopForTimerHidden() {
        invalidatePendingAllAndHide()
    }

    fun maybeShowSuggestionIfNeeded(
        packageName: String,
        nowMillis: Long,
        elapsedMillis: Long,
        sinceForegroundMillis: Long,
    ) {
        if (showSuggestionJob?.isActive == true) return
        if (showMiniGameJob?.isActive == true) return

        val customize = customizeProvider()

        val input =
            SuggestionEngine.Input(
                elapsedMillis = elapsedMillis,
                sinceForegroundMillis = sinceForegroundMillis,
                customize = customize,
                lastDecisionElapsedMillis = sessionGate.lastDecisionElapsedMillis,
                isOverlayShown = isSuggestionOverlayShown || isMiniGameOverlayShown,
                disabledForThisSession = sessionGate.disabledForThisSession,
            )

        if (!suggestionEngine.shouldShow(input)) return

        val epochAtLaunch = suggestionEpoch
        showSuggestionJob =
            scope.launch {
                try {
                    val suggestions = suggestionsRepository.getSuggestionsSnapshot()
                    val selected =
                        suggestionSelector.select(
                            suggestions = suggestions,
                            nowMillis = nowMillis,
                            elapsedMillis = elapsedMillis,
                        )
                    val hasSuggestion = selected != null

                    if (!hasSuggestion && !customize.restSuggestionEnabled) {
                        RefocusLog.d(TAG) { "No suggestion and restSuggestion disabled, skip overlay" }
                        return@launch
                    }

                    val (title, mode, suggestionId) =
                        if (selected != null) {
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

                    if (epochAtLaunch != suggestionEpoch || !currentCoroutineContext().isActive) {
                        RefocusLog.d(TAG) { "Suggestion show aborted (epoch changed or cancelled)" }
                        return@launch
                    }

                    val pkg = overlayPackageProvider() ?: packageName

                    // ミニゲームが「提案の前」の場合は，ここで実行してから提案を表示する
                    if (customize.miniGameEnabled &&
                        customize.miniGameOrder == MiniGameOrder.BeforeSuggestion
                    ) {
                        runMiniGameBlocking(
                            packageName = pkg,
                            customize = customize,
                            seed = nowMillis,
                            epochAtLaunch = miniGameEpoch,
                        )
                        if (epochAtLaunch != suggestionEpoch || !currentCoroutineContext().isActive) {
                            RefocusLog.d(TAG) { "Suggestion show aborted after minigame (epoch changed or cancelled)" }
                            return@launch
                        }
                    }

                    val shown =
                        uiController.showSuggestion(
                            SuggestionOverlayUiModel(
                                title = title,
                                mode = mode,
                                autoDismissMillis = suggestionTimeoutMillis(customize),
                                interactionLockoutMillis = suggestionInteractionLockoutMillis(customize),
                                onSnoozeLater = { handleSuggestionSnoozeLater() },
                                onDisableThisSession = { handleSuggestionDisableThisSession() },
                                onDismissOnly = { handleSuggestionDismissOnly() },
                            ),
                        )

                    if (!shown) {
                        RefocusLog.w(TAG) { "Suggestion overlay was NOT shown (addView failed etc). Will retry." }
                        return@launch
                    }

                    if (epochAtLaunch != suggestionEpoch || !currentCoroutineContext().isActive) {
                        // タイマー非表示やセッション離脱などで無効化された後に show され得るため，ここで必ず閉じる
                        uiController.hideSuggestion()
                        clearOverlayState()
                        RefocusLog.d(TAG) { "Suggestion overlay shown after invalidation -> force hide" }
                        return@launch
                    }

                    isSuggestionOverlayShown = true
                    currentSuggestionId = suggestionId
                    beginSuggestionUiInterruptionIfNeeded(pkg)
                    try {
                        eventRecorder.onSuggestionShown(
                            packageName = pkg,
                            suggestionId = suggestionId,
                        )
                    } catch (e: Exception) {
                        RefocusLog.e(TAG, e) { "Failed to record SuggestionShown for $pkg" }
                    }
                } catch (e: Exception) {
                    RefocusLog.e(TAG, e) { "Failed to show suggestion overlay for $packageName" }
                    isSuggestionOverlayShown = false
                    currentSuggestionId = null
                } finally {
                    showSuggestionJob = null
                }
            }
    }

    private fun suggestionTimeoutMillis(customize: Customize): Long {
        val seconds = customize.suggestionTimeoutSeconds.coerceAtLeast(0)
        return seconds.toLong() * 1_000L
    }

    private fun suggestionInteractionLockoutMillis(customize: Customize): Long =
        customize.suggestionInteractionLockoutMillis.coerceAtLeast(0L)

    private fun handleSuggestionSnoozeAndUpdateGate(packageName: String?) {
        endSuggestionUiInterruptionIfActive()
        clearOverlayState()

        val pkg = packageName
        if (pkg == null) {
            RefocusLog.w(TAG) { "handleSuggestionSnooze: overlayPackage=null; gate not updated" }
            return
        }

        val nowElapsed = timeSource.elapsedRealtime()
        val elapsed = sessionElapsedProvider(pkg, nowElapsed)
        if (elapsed != null) {
            sessionGate = sessionGate.copy(lastDecisionElapsedMillis = elapsed)
            RefocusLog.d(TAG) { "Suggestion decision recorded at sessionElapsed=$elapsed ms" }
        } else {
            RefocusLog.w(TAG) { "handleSuggestionSnooze: no session elapsed for $pkg" }
        }
    }

    private fun maybeStartMiniGameAfterSuggestionIfNeeded(packageName: String) {
        val customize = customizeProvider()
        if (!customize.miniGameEnabled) return
        if (customize.miniGameOrder != MiniGameOrder.AfterSuggestion) return
        if (showMiniGameJob?.isActive == true) return

        val epochAtLaunch = miniGameEpoch
        showMiniGameJob =
            scope.launch {
                try {
                    runMiniGameBlocking(
                        packageName = packageName,
                        customize = customize,
                        seed = timeSource.nowMillis(),
                        epochAtLaunch = epochAtLaunch,
                    )
                } finally {
                    showMiniGameJob = null
                }
            }
    }

    private suspend fun runMiniGameBlocking(
        packageName: String,
        customize: Customize,
        seed: Long,
        epochAtLaunch: Long,
    ) {
        if (epochAtLaunch != miniGameEpoch || !currentCoroutineContext().isActive) return

        val finished = CompletableDeferred<Unit>()

        val shown =
            uiController.showMiniGame(
                MiniGameOverlayUiModel(
                    kind = customize.miniGameKind,
                    seed = seed,
                    onFinished = {
                        if (!finished.isCompleted) finished.complete(Unit)
                    },
                ),
            )

        if (!shown) {
            RefocusLog.w(TAG) { "MiniGame overlay was NOT shown (addView failed etc). skip." }
            return
        }

        if (epochAtLaunch != miniGameEpoch || !currentCoroutineContext().isActive) {
            uiController.hideMiniGame()
            clearMiniGameOverlayState()
            RefocusLog.d(TAG) { "MiniGame overlay shown after invalidation -> force hide" }
            return
        }

        isMiniGameOverlayShown = true
        beginMiniGameUiInterruptionIfNeeded(packageName)
        try {
            finished.await()
        } finally {
            endMiniGameUiInterruptionIfActive()
            clearMiniGameOverlayState()
        }
    }

    private fun handleSuggestionSnoozeLater() {
        val pkg = overlayPackageProvider()
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
                    RefocusLog.e(TAG, e) { "Failed to record SuggestionSnoozed for $pkg" }
                }
            }
        }

        handleSuggestionSnoozeAndUpdateGate(pkg)
        if (pkg != null) maybeStartMiniGameAfterSuggestionIfNeeded(pkg)
    }

    private fun handleSuggestionDismissOnly() {
        val pkg = overlayPackageProvider()
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
                    RefocusLog.e(TAG, e) { "Failed to record SuggestionDismissed for $pkg" }
                }
            }
        }

        handleSuggestionSnoozeAndUpdateGate(pkg)
        if (pkg != null) maybeStartMiniGameAfterSuggestionIfNeeded(pkg)
    }

    private fun handleSuggestionDisableThisSession() {
        val packageName = overlayPackageProvider() ?: return
        val suggestionId = currentSuggestionId ?: 0L

        // clearOverlayState() によって currentSuggestionId が null に戻るため，
        // 先に suggestionId を保持してから状態をクリアする．
        endSuggestionUiInterruptionIfActive()
        clearOverlayState()

        scope.launch {
            try {
                eventRecorder.onSuggestionDecision(
                    packageName = packageName,
                    suggestionId = suggestionId,
                    decision = SuggestionDecision.DisabledForSession,
                )
            } catch (e: Exception) {
                RefocusLog.e(
                    TAG,
                    e,
                ) { "Failed to record SuggestionDisabledForSession for $packageName" }
            }
        }

        sessionGate = sessionGate.copy(disabledForThisSession = true)
        maybeStartMiniGameAfterSuggestionIfNeeded(packageName)
    }

    private fun beginSuggestionUiInterruptionIfNeeded(packageName: String) {
        if (suggestionUiInterruptionPackageName != null) return
        suggestionUiInterruptionPackageName = packageName

        val nowElapsed = timeSource.elapsedRealtime()
        try {
            onUiPause(packageName, nowElapsed)
        } catch (e: Exception) {
            RefocusLog.e(TAG, e) { "Failed to pause overlay session tracker for $packageName" }
        }

        scope.launch {
            try {
                eventRecorder.onUiInterruptionStart(
                    packageName = packageName,
                    source = UiInterruptionSource.Suggestion,
                )
            } catch (e: Exception) {
                RefocusLog.e(TAG, e) { "Failed to record UiInterruptionStart(Suggestion) for $packageName" }
            }
        }
    }

    private fun endSuggestionUiInterruptionIfActive() {
        val pkg = suggestionUiInterruptionPackageName ?: return
        suggestionUiInterruptionPackageName = null

        val nowElapsed = timeSource.elapsedRealtime()
        try {
            onUiResume(pkg, nowElapsed)
        } catch (e: Exception) {
            RefocusLog.e(TAG, e) { "Failed to resume overlay session tracker for $pkg" }
        }

        scope.launch {
            try {
                eventRecorder.onUiInterruptionEnd(
                    packageName = pkg,
                    source = UiInterruptionSource.Suggestion,
                )
            } catch (e: Exception) {
                RefocusLog.e(TAG, e) { "Failed to record UiInterruptionEnd(Suggestion) for $pkg" }
            }
        }
    }

    private fun beginMiniGameUiInterruptionIfNeeded(packageName: String) {
        if (miniGameUiInterruptionPackageName != null) return
        miniGameUiInterruptionPackageName = packageName

        val nowElapsed = timeSource.elapsedRealtime()
        try {
            onUiPause(packageName, nowElapsed)
        } catch (e: Exception) {
            RefocusLog.e(TAG, e) { "Failed to pause overlay session tracker for $packageName" }
        }

        scope.launch {
            try {
                eventRecorder.onUiInterruptionStart(
                    packageName = packageName,
                    source = UiInterruptionSource.MiniGame,
                )
            } catch (e: Exception) {
                RefocusLog.e(TAG, e) { "Failed to record UiInterruptionStart(MiniGame) for $packageName" }
            }
        }
    }

    private fun endMiniGameUiInterruptionIfActive() {
        val pkg = miniGameUiInterruptionPackageName ?: return
        miniGameUiInterruptionPackageName = null

        val nowElapsed = timeSource.elapsedRealtime()
        try {
            onUiResume(pkg, nowElapsed)
        } catch (e: Exception) {
            RefocusLog.e(TAG, e) { "Failed to resume overlay session tracker for $pkg" }
        }

        scope.launch {
            try {
                eventRecorder.onUiInterruptionEnd(
                    packageName = pkg,
                    source = UiInterruptionSource.MiniGame,
                )
            } catch (e: Exception) {
                RefocusLog.e(TAG, e) { "Failed to record UiInterruptionEnd(MiniGame) for $pkg" }
            }
        }
    }
}
