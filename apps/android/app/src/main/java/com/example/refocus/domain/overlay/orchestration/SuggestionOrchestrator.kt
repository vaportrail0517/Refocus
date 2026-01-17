package com.example.refocus.domain.overlay.orchestration

import com.example.refocus.core.logging.RefocusLog
import com.example.refocus.core.model.Customize
import com.example.refocus.core.model.MiniGameKind
import com.example.refocus.core.model.MiniGameOrder
import com.example.refocus.core.model.SuggestionDecision
import com.example.refocus.core.model.SuggestionAction
import com.example.refocus.core.model.SuggestionMode
import com.example.refocus.core.model.UiInterruptionSource
import com.example.refocus.core.util.TimeSource
import com.example.refocus.domain.overlay.model.SessionBootstrapFromTimeline
import com.example.refocus.domain.overlay.model.SessionSuggestionGate
import com.example.refocus.domain.overlay.port.MiniGameOverlayUiModel
import com.example.refocus.domain.overlay.port.OverlayUiPort
import com.example.refocus.domain.overlay.port.SuggestionOverlayUiModel
import com.example.refocus.domain.overlay.port.SuggestionActionLauncherPort
import com.example.refocus.domain.repository.SuggestionsRepository
import com.example.refocus.domain.suggestion.SuggestionEngine
import com.example.refocus.domain.suggestion.SuggestionSelector
import com.example.refocus.domain.timeline.EventRecorder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

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
    private val actionLauncher: SuggestionActionLauncherPort,
    private val eventRecorder: EventRecorder,
    private val overlayPackageProvider: () -> String?,
    private val customizeProvider: () -> Customize,
) {
    companion object {
        private const val TAG = "SuggestionOrchestrator"

        // ミニゲーム復帰の show が一時的に失敗するケース（overlay remove/add のレース等）を吸収するためのリトライ設定
        private const val MINI_GAME_RESUME_RETRY_INTERVAL_MILLIS = 400L
        private const val MINI_GAME_MAX_SHOW_ATTEMPTS_PER_CYCLE = 6
    }

    private enum class MiniGameCycleState {
        NotStarted,
        InProgress,
        Completed,
    }

    @Volatile
    private var nextCycleId: Long = 0L

    @Volatile
    private var activeCycleId: Long? = null

    @Volatile
    private var activeCyclePackageName: String? = null

    /**
     * サイクル開始時点の設定をスナップショットして固定する．
     * 表示中に設定が変わっても，そのサイクル内の挙動が崩れないようにするため．
     */
    @Volatile
    private var cycleMiniGameOrder: MiniGameOrder? = null

    @Volatile
    private var miniGameCycleState: MiniGameCycleState = MiniGameCycleState.NotStarted

    private fun beginNewSuggestionCycle(
        packageName: String,
        customize: Customize,
    ): Long {
        // 新しいサイクル開始時は，前サイクル由来の pending を必ず破棄する
        // （古い pending が残ると，復帰ロジックで即時再表示が起き得る）
        clearPendingMiniGameGate()

        val id =
            synchronized(this) {
                nextCycleId += 1L
                nextCycleId
            }

        activeCycleId = id
        activeCyclePackageName = packageName
        cycleMiniGameOrder = if (customize.miniGameEnabled) customize.miniGameOrder else null
        miniGameCycleState = MiniGameCycleState.NotStarted

        return id
    }

    private fun ensureSuggestionCycle(
        packageName: String,
        customize: Customize,
    ): Long {
        val id = activeCycleId
        if (id != null && activeCyclePackageName == packageName) return id
        return beginNewSuggestionCycle(
            packageName = packageName,
            customize = customize,
        )
    }

    private fun endSuggestionCycleIfActive() {
        activeCycleId = null
        activeCyclePackageName = null
        cycleMiniGameOrder = null
        miniGameCycleState = MiniGameCycleState.NotStarted
        clearPendingMiniGameGate()
    }

    private fun isActiveCycleFor(
        packageName: String,
        cycleId: Long,
    ): Boolean = activeCycleId == cycleId && activeCyclePackageName == packageName

    private fun completeMiniGameForActiveCycle(
        packageName: String,
        cycleId: Long,
    ) {
        if (!isActiveCycleFor(packageName, cycleId)) return

        // ここで確定させることで，runMiniGameBlocking のコルーチンがキャンセルされても
        // pending が残って復帰→即再表示，というループを避ける
        clearPendingMiniGameGate()
        miniGameCycleState = MiniGameCycleState.Completed

        // 「提案の後にミニゲーム」の場合のみ，ミニゲーム完了でサイクル終了
        if (cycleMiniGameOrder == MiniGameOrder.AfterSuggestion) {
            endSuggestionCycleIfActive()
        }
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

    /**
     * ミニゲーム Overlay の「表示インスタンス」単位のトークン。
     *
     * - Home 一時離脱などで hide と show が短時間に並ぶと，Main スレッド上での実行順が前後し得る。
     * - そのとき，古い hide が新しい show を消してしまうと「表示されないのに待ち続ける」状態になる。
     *
     * これを避けるため，show ごとに新しい token を発行し，hide 側は token が一致するときだけ実行する。
     */
    @Volatile
    private var miniGameOverlayToken: Long? = null

    @Volatile
    private var miniGameOverlayTokenSeq: Long = 0L

    private fun nextMiniGameOverlayToken(): Long =
        synchronized(this) {
            miniGameOverlayTokenSeq += 1L
            miniGameOverlayTokenSeq
        }

    private data class PendingMiniGameGate(
        val cycleId: Long,
        val packageName: String,
        val kind: MiniGameKind,
        val seed: Long,
        val triggeredAtElapsedMillis: Long,
        // showMiniGame の試行回数（復帰時の一時的失敗に備えて上限を設ける）
        val attemptCount: Int,
        // 最後に showMiniGame を試行した時刻（elapsedRealtime）
        val lastAttemptElapsedRealtime: Long,
    )

    @Volatile
    private var pendingMiniGameGate: PendingMiniGameGate? = null

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

    /**
     * 対象アプリが前面に戻ったタイミングで呼ぶ．
     *
     * ミニゲーム表示中に Home などへ一時離脱した場合，OverlaySessionLifecycle で UI を閉じる．
     * その後に同一論理セッションとして対象アプリへ戻ったら，pending を見てミニゲームを復帰表示する．
     *
     * Foreground 監視ループ（maybeShowSuggestionIfNeeded）に依存すると，
     * - タイマー非表示中
     * - Enter/Leave のイベント直後
     * などで復帰が遅延・欠落し得るため，Enter 側で明示的に呼べる API を用意する．
     */
    fun onEnterTargetAppMaybeResumeMiniGame(
        packageName: String,
        nowElapsedRealtime: Long,
    ) {
        val elapsed = sessionElapsedProvider(packageName, nowElapsedRealtime) ?: return
        maybeResumePendingMiniGameIfNeeded(
            packageName = packageName,
            elapsedMillis = elapsed,
        )
    }

    fun onDisabled() {
        endSuggestionCycleIfActive()
        invalidatePendingOverlaysAndHide()
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
        // 新規セッション開始時は，提案サイクル状態を必ずリセットする
        // （同一論理セッション継続の場合は gate を復元するが，UI サイクルは復元しない）
        endSuggestionCycleIfActive()

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
        // クールダウン（lastDecisionElapsedMillis）は sessionGate 側に保持する
    }

    private fun clearMiniGameOverlayState(expectedToken: Long? = null) {
        // 古いコルーチンの finally が，新しい overlay 状態を消してしまうのを防ぐ。
        if (expectedToken != null && miniGameOverlayToken != expectedToken) return
        isMiniGameOverlayShown = false
        miniGameOverlayToken = null
    }

    private fun markMiniGameOverlayShown(token: Long?) {
        miniGameOverlayToken = token
        isMiniGameOverlayShown = true
    }

    private fun clearPendingMiniGameGate() {
        pendingMiniGameGate = null
    }

    private fun cancelPendingMiniGameAndHide() {
        clearPendingMiniGameGate()
        invalidatePendingMiniGameAndHide()
    }

    /**
     * 進行中の show を無効化し，表示中の提案 / ミニゲームを確実に閉じる．
     */
    fun invalidatePendingOverlaysAndHide() {
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
        val token = miniGameOverlayToken
        uiController.hideMiniGame(token = token)
        clearMiniGameOverlayState(expectedToken = token)
    }

    /**
     * タイマーが『このセッションでは非表示』になったときに呼ぶ．
     *
     * - 進行中の show 処理をキャンセル
     * - 表示中の提案 / ミニゲームを閉じる
     * - 内部の overlay 状態をクリア
     */
    fun stopForTimerHidden() {
        endSuggestionCycleIfActive()
        invalidatePendingOverlaysAndHide()
    }

    fun maybeShowSuggestionIfNeeded(
        packageName: String,
        nowMillis: Long,
        elapsedMillis: Long,
        sinceForegroundMillis: Long,
    ) {
        if (showSuggestionJob?.isActive == true) return
        if (showMiniGameJob?.isActive == true) return

        val pendingForThis =
            pendingMiniGameGate?.takeIf {
                val cycleId = activeCycleId
                cycleId != null &&
                    it.cycleId == cycleId &&
                    it.packageName == packageName &&
                    activeCyclePackageName == packageName &&
                    miniGameCycleState == MiniGameCycleState.InProgress
            }
        if (pendingForThis != null) {
            val nowElapsed = timeSource.elapsedRealtime()
            if (
                pendingForThis.lastAttemptElapsedRealtime > 0L &&
                nowElapsed - pendingForThis.lastAttemptElapsedRealtime < MINI_GAME_RESUME_RETRY_INTERVAL_MILLIS
            ) {
                return
            }
        }

        // ミニゲーム表示中に Home 等で離脱した場合でも，同じ論理セッションに戻れば再表示する
        maybeResumePendingMiniGameIfNeeded(
            packageName = packageName,
            elapsedMillis = elapsedMillis,
        )
        if (showMiniGameJob?.isActive == true) return

        // まだ pending が残っている（表示に失敗した等）場合は，提案を出さずに次回のリトライへ回す
        pendingMiniGameGate?.let { p ->
            val cycleId = activeCycleId
            if (cycleId != null &&
                p.cycleId == cycleId &&
                p.packageName == packageName &&
                activeCyclePackageName == packageName &&
                miniGameCycleState == MiniGameCycleState.InProgress
            ) {
                return
            }
        }

        val customize = customizeProvider()

        val input =
            SuggestionEngine.Input(
                elapsedMillis = elapsedMillis,
                sinceForegroundMillis = sinceForegroundMillis,
                customize = customize,
                lastDecisionElapsedMillis = sessionGate.lastDecisionElapsedMillis,
                isOverlayShown = isSuggestionOverlayShown || isMiniGameOverlayShown,
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

                    val title: String
                    val mode: SuggestionMode
                    val suggestionId: Long
                    val action: SuggestionAction

                    if (selected != null) {
                        title = selected.title
                        mode = SuggestionMode.Generic
                        suggestionId = selected.id
                        action = selected.action
                    } else {
                        title = "画面から少し離れて休憩する"
                        mode = SuggestionMode.Rest
                        suggestionId = 0L
                        action = SuggestionAction.None
                    }

                    if (epochAtLaunch != suggestionEpoch || !currentCoroutineContext().isActive) {
                        RefocusLog.d(TAG) { "Suggestion show aborted (epoch changed or cancelled)" }
                        return@launch
                    }

                    val pkg = overlayPackageProvider() ?: packageName
                    val cycleId =
                        ensureSuggestionCycle(
                            packageName = pkg,
                            customize = customize,
                        )

                    // ミニゲームが「提案の前」の場合は，ここで実行してから提案を表示する
                    if (cycleMiniGameOrder == MiniGameOrder.BeforeSuggestion &&
                        miniGameCycleState != MiniGameCycleState.Completed
                    ) {
                        miniGameCycleState = MiniGameCycleState.InProgress
                        runMiniGameBlocking(
                            packageName = pkg,
                            seed = nowMillis,
                            epochAtLaunch = miniGameEpoch,
                            cycleId = cycleId,
                        )
                        if (epochAtLaunch != suggestionEpoch || !currentCoroutineContext().isActive) {
                            RefocusLog.d(TAG) {
                                "Suggestion show aborted after minigame (epoch changed or cancelled)"
                            }
                            // Home などへの一時離脱でキャンセルされても，サイクルは維持して復帰できるようにする
                            return@launch
                        }
                    }

                    val shown =
                        uiController.showSuggestion(
                            SuggestionOverlayUiModel(
                                title = title,
                                targetPackageName = pkg,
                                mode = mode,
                                action = action,
                                autoDismissMillis = suggestionTimeoutMillis(customize),
                                interactionLockoutMillis = suggestionInteractionLockoutMillis(customize),
                                onOpenAction = { handleSuggestionOpenAction(suggestionId = suggestionId, action = action) },
                                onSnoozeLater = { handleSuggestionSnoozeLater() },
                                onCloseTargetApp = { handleSuggestionCloseTargetApp() },
                                onDismissOnly = { handleSuggestionDismissOnly() },
                            ),
                        )

                    if (!shown) {
                        RefocusLog.w(TAG) { "Suggestion overlay was NOT shown (addView failed etc). Will retry." }
                        endSuggestionCycleIfActive()
                        return@launch
                    }

                    if (epochAtLaunch != suggestionEpoch || !currentCoroutineContext().isActive) {
                        // タイマー非表示やセッション離脱などで無効化された後に show され得るため，ここで必ず閉じる
                        uiController.hideSuggestion()
                        clearOverlayState()
                        RefocusLog.d(TAG) { "Suggestion overlay shown after invalidation -> force hide" }
                        // Home などへの一時離脱であれば，サイクルは維持して復帰できるようにする
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
                } catch (e: CancellationException) {
                    // Home 一時離脱などで cancel されるのは正常系。
                    RefocusLog.d(TAG) { "Suggestion show cancelled for $packageName" }
                    throw e
                } catch (e: Exception) {
                    RefocusLog.e(TAG, e) { "Failed to show suggestion overlay for $packageName" }
                    isSuggestionOverlayShown = false
                    currentSuggestionId = null
                    endSuggestionCycleIfActive()
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

        // 「提案の後にミニゲーム」の場合は，ミニゲーム完了までサイクルを維持する
        if (cycleMiniGameOrder != MiniGameOrder.AfterSuggestion) {
            endSuggestionCycleIfActive()
        }

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
        val cycleId = activeCycleId ?: return
        if (activeCyclePackageName != packageName) return
        if (cycleMiniGameOrder != MiniGameOrder.AfterSuggestion) return
        if (miniGameCycleState != MiniGameCycleState.NotStarted) return
        if (showMiniGameJob?.isActive == true) return

        miniGameCycleState = MiniGameCycleState.InProgress

        val epochAtLaunch = miniGameEpoch
        showMiniGameJob =
            scope.launch {
                try {
                    runMiniGameBlocking(
                        packageName = packageName,
                        seed = timeSource.nowMillis(),
                        epochAtLaunch = epochAtLaunch,
                        cycleId = cycleId,
                    )
                } finally {
                    showMiniGameJob = null
                }
            }
    }

    private fun maybeResumePendingMiniGameIfNeeded(
        packageName: String,
        elapsedMillis: Long,
    ) {
        val pending = pendingMiniGameGate ?: return

        val cycleId = activeCycleId
        if (cycleId == null) {
            clearPendingMiniGameGate()
            return
        }
        if (pending.cycleId != cycleId) {
            clearPendingMiniGameGate()
            return
        }
        if (activeCyclePackageName != packageName) return
        if (pending.packageName != packageName) return
        if (cycleMiniGameOrder == null) {
            clearPendingMiniGameGate()
            return
        }
        if (miniGameCycleState != MiniGameCycleState.InProgress) {
            clearPendingMiniGameGate()
            return
        }
        if (isMiniGameOverlayShown || isSuggestionOverlayShown) return
        if (showMiniGameJob?.isActive == true) return

        // 以前の pending が残っていても，セッションが切れていれば破棄する
        if (pending.triggeredAtElapsedMillis > 0L &&
            elapsedMillis + 1_000L < pending.triggeredAtElapsedMillis
        ) {
            RefocusLog.d(TAG) {
                "Pending minigame cleared (session restarted?): elapsed=$elapsedMillis, triggered=${pending.triggeredAtElapsedMillis}"
            }
            endSuggestionCycleIfActive()
            return
        }

        val epochAtLaunch = miniGameEpoch
        showMiniGameJob =
            scope.launch {
                try {
                    runMiniGameBlocking(
                        packageName = packageName,
                        seed = pending.seed,
                        epochAtLaunch = epochAtLaunch,
                        cycleId = cycleId,
                    )
                } finally {
                    showMiniGameJob = null
                }
            }
    }

    private suspend fun runMiniGameBlocking(
        packageName: String,
        seed: Long,
        epochAtLaunch: Long,
        cycleId: Long,
    ) {
        if (!isActiveCycleFor(packageName, cycleId)) return
        if (cycleMiniGameOrder == null) return
        if (epochAtLaunch != miniGameEpoch || !currentCoroutineContext().isActive) return
        if (miniGameCycleState == MiniGameCycleState.Completed) return

        // ここに来た時点で，少なくとも「このサイクルではミニゲームを 1 回起動する」ことを確定させる
        if (miniGameCycleState == MiniGameCycleState.NotStarted) {
            miniGameCycleState = MiniGameCycleState.InProgress
        }

        val finished = CompletableDeferred<Unit>()
        val finishedOnce = AtomicBoolean(false)

        val existingPending =
            pendingMiniGameGate?.takeIf { it.packageName == packageName && it.cycleId == cycleId }

        // 以前のサイクル由来の pending が残っていた場合は破棄する
        pendingMiniGameGate?.let { pending ->
            if (pending.cycleId != cycleId || pending.packageName != packageName) {
                clearPendingMiniGameGate()
            }
        }

        val kindToShow = existingPending?.kind ?: pickRandomMiniGameKind(seed)
        val seedToUse = existingPending?.seed ?: seed

        // 初回表示前から pending を作っておく。
        // - show の一時的失敗（remove/add レース等）でも，同じミニゲームで復帰できる
        // - サイクル中にゲーム内容が変わらないよう seed/kind を固定する
        val nowElapsed = timeSource.elapsedRealtime()
        val atElapsed =
            existingPending?.triggeredAtElapsedMillis
                ?: (sessionElapsedProvider(packageName, nowElapsed) ?: 0L)

        val attemptCount = (existingPending?.attemptCount ?: 0) + 1
        pendingMiniGameGate =
            PendingMiniGameGate(
                cycleId = cycleId,
                packageName = packageName,
                kind = kindToShow,
                seed = seedToUse,
                triggeredAtElapsedMillis = atElapsed,
                attemptCount = attemptCount,
                lastAttemptElapsedRealtime = nowElapsed,
            )

        if (attemptCount > MINI_GAME_MAX_SHOW_ATTEMPTS_PER_CYCLE) {
            RefocusLog.w(TAG) { "MiniGame show retry exceeded. abort cycle. attempts=$attemptCount" }
            completeMiniGameForActiveCycle(
                packageName = packageName,
                cycleId = cycleId,
            )
            return
        }

        // この run で表示する Overlay インスタンス用の token を発行する。
        // hide が遅れて実行されても，新しい show を消さないようにする。
        val overlayToken = nextMiniGameOverlayToken()

        val shown =
            uiController.showMiniGame(
                model =
                    MiniGameOverlayUiModel(
                        kind = kindToShow,
                        seed = seedToUse,
                        onFinished = {
                            if (finishedOnce.compareAndSet(false, true)) {
                                completeMiniGameForActiveCycle(
                                    packageName = packageName,
                                    cycleId = cycleId,
                                )
                                if (!finished.isCompleted) finished.complete(Unit)
                            }
                        },
                    ),
                token = overlayToken,
            )

        if (!shown) {
            // pending は保持したまま return する（次の前面更新で復帰を試行できる）
            RefocusLog.w(TAG) { "MiniGame overlay was NOT shown (addView failed etc). will retry." }
            return
        }

        // 現在表示中のミニゲーム Overlay の token / shown 状態を更新
        markMiniGameOverlayShown(overlayToken)

        if (epochAtLaunch != miniGameEpoch || !currentCoroutineContext().isActive) {
            // ここでは pending を消さずに残す（Home 離脱等で復帰できるようにする）
            uiController.hideMiniGame(token = overlayToken)
            clearMiniGameOverlayState(expectedToken = overlayToken)
            RefocusLog.d(TAG) { "MiniGame overlay shown after invalidation -> force hide" }
            return
        }

        // markMiniGameOverlayShown 済み
        beginMiniGameUiInterruptionIfNeeded(packageName)
        try {
            finished.await()
        } finally {
            endMiniGameUiInterruptionIfActive()
            clearMiniGameOverlayState(expectedToken = overlayToken)
        }
    }

    private fun pickRandomMiniGameKind(seed: Long): MiniGameKind {
        val kinds = MiniGameKind.entries
        if (kinds.isEmpty()) return MiniGameKind.FlashAnzan

        // seed から軽く混ぜた値でインデックスを作る（Random 依存を増やさずに済ませる）
        val mixed = seed xor (seed ushr 33) xor (seed shl 11)
        val index = ((mixed and Long.MAX_VALUE) % kinds.size).toInt()
        return kinds[index]
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
                } catch (e: CancellationException) {
                    RefocusLog.d(TAG) { "Suggestion decision recording cancelled for $pkg" }
                    return@launch
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
                } catch (e: CancellationException) {
                    RefocusLog.d(TAG) { "Suggestion decision recording cancelled for $pkg" }
                    return@launch
                } catch (e: Exception) {
                    RefocusLog.e(TAG, e) { "Failed to record SuggestionDismissed for $pkg" }
                }
            }
        }

        handleSuggestionSnoozeAndUpdateGate(pkg)
        if (pkg != null) maybeStartMiniGameAfterSuggestionIfNeeded(pkg)
    }

    private fun handleSuggestionOpenAction(
        suggestionId: Long,
        action: SuggestionAction,
    ) {
        val packageName = overlayPackageProvider() ?: return

        // Close overlay state and resume measurement.
        endSuggestionUiInterruptionIfActive()
        clearOverlayState()
        endSuggestionCycleIfActive()

        // Do not start minigame here, and ensure any pending minigame is cancelled.
        cancelPendingMiniGameAndHide()

        // 直後の即再表示を避けるため，アクション起動より前にクールダウン起点を同期的に更新する
        val nowElapsed = timeSource.elapsedRealtime()
        val elapsed = sessionElapsedProvider(packageName, nowElapsed)
        if (elapsed != null) {
            sessionGate = sessionGate.copy(lastDecisionElapsedMillis = elapsed)
            RefocusLog.d(TAG) { "Suggestion decision recorded at sessionElapsed=$elapsed ms (Opened)" }
        } else {
            RefocusLog.w(TAG) { "handleSuggestionOpenAction: no session elapsed for $packageName" }
        }

        scope.launch {
            try {
                eventRecorder.onSuggestionDecision(
                    packageName = packageName,
                    suggestionId = suggestionId,
                    decision = SuggestionDecision.Opened,
                )
            } catch (e: CancellationException) {
                RefocusLog.d(TAG) { "Suggestion decision recording cancelled for $packageName" }
            } catch (e: Exception) {
                RefocusLog.e(TAG, e) { "Failed to record SuggestionOpened for $packageName" }
            }

            // Execute the action after attempting to record the decision.
            try {
                actionLauncher.launch(action)
            } catch (e: Exception) {
                RefocusLog.e(TAG, e) { "Failed to launch suggestion action for $packageName" }
            }
        }
    }

    private fun handleSuggestionCloseTargetApp() {
        val packageName = overlayPackageProvider() ?: return
        val suggestionId = currentSuggestionId ?: 0L

        // 表示中の提案を閉じ，計測中断を解除する
        endSuggestionUiInterruptionIfActive()
        clearOverlayState()
        endSuggestionCycleIfActive()

        // 「提案の後にミニゲーム」を選んでいても，ここでは起動しない
        // かつ，すでに何らかの理由でミニゲーム表示が進行していれば確実に止める
        cancelPendingMiniGameAndHide()

        // 決定イベントは通常の Dismiss と同等扱いとして記録する（結果や強制終了は記録しない方針）
        scope.launch {
            try {
                eventRecorder.onSuggestionDecision(
                    packageName = packageName,
                    suggestionId = suggestionId,
                    decision = SuggestionDecision.Dismissed,
                )
            } catch (e: Exception) {
                RefocusLog.e(TAG, e) { "Failed to record SuggestionCloseTargetApp(Dismissed) for $packageName" }
            }
        }

        // 直後の即再表示を避けるため，クールダウン起点を更新する（提案停止はしない）
        val nowElapsed = timeSource.elapsedRealtime()
        val elapsed = sessionElapsedProvider(packageName, nowElapsed)
        sessionGate =
            sessionGate.copy(
                lastDecisionElapsedMillis = elapsed ?: sessionGate.lastDecisionElapsedMillis,
            )
    }

    private fun handleSuggestionDisableThisSession() {
        val packageName = overlayPackageProvider() ?: return
        val suggestionId = currentSuggestionId ?: 0L

        // clearOverlayState() によって currentSuggestionId が null に戻るため，
        // 先に suggestionId を保持してから状態をクリアする．
        endSuggestionUiInterruptionIfActive()
        clearOverlayState()
        // disable はここでサイクルを終える（AfterSuggestion の場合もミニゲームを起動しないため）
        endSuggestionCycleIfActive()

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

        // 「このセッションでは提案しない」は廃止し，直後の即再表示のみクールダウンで抑止する
        val nowElapsed = timeSource.elapsedRealtime()
        val elapsed = sessionElapsedProvider(packageName, nowElapsed)
        if (elapsed != null) {
            sessionGate = sessionGate.copy(lastDecisionElapsedMillis = elapsed)
        }
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
