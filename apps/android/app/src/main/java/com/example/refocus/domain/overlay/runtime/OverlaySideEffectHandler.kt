package com.example.refocus.domain.overlay.runtime

import com.example.refocus.core.logging.RefocusLog
import com.example.refocus.domain.overlay.engine.OverlayEvent
import com.example.refocus.domain.overlay.engine.OverlayState
import com.example.refocus.domain.overlay.model.OverlayRuntimeState
import com.example.refocus.domain.overlay.orchestration.OverlaySessionLifecycle
import com.example.refocus.domain.overlay.orchestration.OverlaySessionTracker
import com.example.refocus.domain.overlay.orchestration.SuggestionOrchestrator
import com.example.refocus.domain.overlay.port.OverlayUiPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Overlay の状態遷移に応じた副作用を 1 箇所に集約する．
 *
 * - 状態遷移は [com.example.refocus.domain.overlay.engine.OverlayStateMachine] が決める
 * - ここでは「遷移した結果として何をするか」を定義する
 */
internal class OverlaySideEffectHandler(
    private val scope: CoroutineScope,
    private val runtimeState: MutableStateFlow<OverlayRuntimeState>,
    private val uiController: OverlayUiPort,
    private val suggestionOrchestrator: SuggestionOrchestrator,
    private val sessionLifecycle: OverlaySessionLifecycle,
    private val sessionTracker: OverlaySessionTracker,
) {
    companion object {
        private const val TAG = "OverlaySideEffectHandler"
    }

    fun handle(
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
