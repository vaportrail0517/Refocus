package com.example.refocus.domain.overlay.engine

import com.example.refocus.core.logging.RefocusLog
import com.example.refocus.domain.overlay.model.OverlayRuntimeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * OverlayStateMachine の状態遷移を「単一の経路」に集約するための小さなラッパ．
 *
 * - OverlayCoordinator 本体から状態遷移ロジックを分離し，責務を明確化する
 * - synchronized を Coordinator に貼るのではなく，状態遷移だけをロックして直列化する
 *
 * 重要: dispatch は同期的に実行される（呼び出し元が return する前に副作用まで完了する）．
 * BroadcastReceiver 由来の onScreenOff などが，従来どおり安全に動くための仕様．
 */
internal class OverlayStateTransitioner(
    private val runtimeState: MutableStateFlow<OverlayRuntimeState>,
    private val stateMachine: OverlayStateMachine = OverlayStateMachine(),
    private val onStateChanged: (oldState: OverlayState, newState: OverlayState, event: OverlayEvent) -> Unit,
) {
    companion object {
        private const val TAG = "OverlayStateTransitioner"
    }

    private val lock = Any()

    fun dispatch(event: OverlayEvent) {
        val transitionResult: Pair<OverlayState, OverlayState>? =
            synchronized(lock) {
                val oldState = runtimeState.value.overlayState
                val newState = stateMachine.transition(oldState, event)
                if (newState == oldState) {
                    null
                } else {
                    runtimeState.update { it.copy(overlayState = newState) }
                    oldState to newState
                }
            }

        if (transitionResult == null) return

        val (oldState, newState) = transitionResult
        RefocusLog.d(TAG) { "overlayState: $oldState -> $newState by $event" }
        onStateChanged(oldState, newState, event)
    }
}