package com.example.refocus.domain.overlay.orchestration

import com.example.refocus.core.logging.RefocusLog
import com.example.refocus.core.model.Customize
import com.example.refocus.core.model.SuggestionDecision
import com.example.refocus.core.model.SuggestionMode
import com.example.refocus.core.util.TimeSource
import com.example.refocus.domain.overlay.model.SessionBootstrapFromTimeline
import com.example.refocus.domain.overlay.model.SessionSuggestionGate
import com.example.refocus.domain.overlay.port.OverlayUiGateway
import com.example.refocus.domain.overlay.port.SuggestionOverlayUiModel
import com.example.refocus.domain.repository.SuggestionsRepository
import com.example.refocus.domain.suggestion.SuggestionEngine
import com.example.refocus.domain.suggestion.SuggestionSelector
import com.example.refocus.domain.timeline.EventRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 提案（Suggestion）オーバーレイの表示と，
 * セッション内ゲート（クールダウン，このセッションではもう出さない等）を管理する．
 */
class SuggestionOrchestrator(
    private val scope: CoroutineScope,
    private val timeSource: TimeSource,
    private val sessionElapsedProvider: (packageName: String, nowElapsedRealtime: Long) -> Long?,
    private val suggestionEngine: SuggestionEngine,
    private val suggestionSelector: SuggestionSelector,
    private val suggestionsRepository: SuggestionsRepository,
    private val uiController: OverlayUiGateway,
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
    private var sessionGate: SessionSuggestionGate = SessionSuggestionGate()

    @Volatile
    private var isSuggestionOverlayShown: Boolean = false

    @Volatile
    private var currentSuggestionId: Long? = null

    fun onDisabled() {
        // UI は Coordinator 側でも片付けるが，状態だけはここでも確実に初期化する
        showSuggestionJob?.cancel()
        showSuggestionJob = null
        clearOverlayState()
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

    fun maybeShowSuggestionIfNeeded(
        packageName: String,
        nowMillis: Long,
        elapsedMillis: Long,
        sinceForegroundMillis: Long,
    ) {
        if (showSuggestionJob?.isActive == true) return

        val customize = customizeProvider()

        val input = SuggestionEngine.Input(
            elapsedMillis = elapsedMillis,
            sinceForegroundMillis = sinceForegroundMillis,
            customize = customize,
            lastDecisionElapsedMillis = sessionGate.lastDecisionElapsedMillis,
            isOverlayShown = isSuggestionOverlayShown,
            disabledForThisSession = sessionGate.disabledForThisSession,
        )

        if (!suggestionEngine.shouldShow(input)) return

        showSuggestionJob = scope.launch {
            try {
                val suggestions = suggestionsRepository.getSuggestionsSnapshot()
                val selected = suggestionSelector.select(
                    suggestions = suggestions,
                    nowMillis = nowMillis,
                    elapsedMillis = elapsedMillis,
                )
                val hasSuggestion = selected != null

                if (!hasSuggestion && !customize.restSuggestionEnabled) {
                    RefocusLog.d(TAG) { "No suggestion and restSuggestion disabled, skip overlay" }
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

                val pkg = overlayPackageProvider() ?: packageName
                val shown = uiController.showSuggestion(
                    SuggestionOverlayUiModel(
                        title = title,
                        mode = mode,
                        autoDismissMillis = suggestionTimeoutMillis(customize),
                        interactionLockoutMillis = suggestionInteractionLockoutMillis(customize),
                        onSnoozeLater = { handleSuggestionSnoozeLater() },
                        onDisableThisSession = { handleSuggestionDisableThisSession() },
                        onDismissOnly = { handleSuggestionDismissOnly() },
                    )
                )

                if (!shown) {
                    RefocusLog.w(TAG) { "Suggestion overlay was NOT shown (addView failed etc). Will retry." }
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

    private fun suggestionInteractionLockoutMillis(customize: Customize): Long {
        return customize.suggestionInteractionLockoutMillis.coerceAtLeast(0L)
    }

    private fun handleSuggestionSnooze() {
        clearOverlayState()

        val pkg = overlayPackageProvider()
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
        handleSuggestionSnooze()
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
        handleSuggestionSnooze()
    }

    private fun handleSuggestionDisableThisSession() {
        val packageName = overlayPackageProvider() ?: return
        val suggestionId = currentSuggestionId ?: 0L

        // clearOverlayState() によって currentSuggestionId が null に戻るため，
        // 先に suggestionId を保持してから状態をクリアする．
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
                    e
                ) { "Failed to record SuggestionDisabledForSession for $packageName" }
            }
        }

        sessionGate = sessionGate.copy(disabledForThisSession = true)
    }
}