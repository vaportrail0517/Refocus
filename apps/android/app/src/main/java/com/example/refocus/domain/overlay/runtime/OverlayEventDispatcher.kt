package com.example.refocus.domain.overlay.runtime

import com.example.refocus.domain.overlay.engine.OverlayEvent
import com.example.refocus.domain.overlay.engine.OverlayState
import com.example.refocus.domain.overlay.engine.OverlayStateTransitioner
import com.example.refocus.domain.overlay.model.OverlayRuntimeState
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * OverlayStateMachine へのイベント送出を 1 箇所に集約するための薄いラッパ．
 *
 * - 状態遷移は [OverlayStateTransitioner] が担う
 * - 遷移後の副作用は呼び出し側が [onStateChanged] として注入する
 */
internal class OverlayEventDispatcher(
    runtimeState: MutableStateFlow<OverlayRuntimeState>,
    onStateChanged: (oldState: OverlayState, newState: OverlayState, event: OverlayEvent) -> Unit,
) {
    private val stateTransitioner =
        OverlayStateTransitioner(
            runtimeState = runtimeState,
            onStateChanged = onStateChanged,
        )

    fun dispatch(event: OverlayEvent) {
        stateTransitioner.dispatch(event)
    }
}
