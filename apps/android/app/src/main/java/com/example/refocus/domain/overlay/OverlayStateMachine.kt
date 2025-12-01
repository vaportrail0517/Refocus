package com.example.refocus.domain.overlay

import com.example.refocus.core.model.OverlayEvent
import com.example.refocus.core.model.OverlayState

/**
 * Overlay の状態遷移を管理する純粋なステートマシン。
 *
 * 現時点では Idle / Tracking / Disabled を主に利用し、
 * Paused / Suggesting については今後の拡張で扱う。
 *
 * このクラスは内部状態を持たず、呼び出し側（OverlayOrchestrator）が
 * 現在の状態を保持する。
 */
class OverlayStateMachine {

    /**
     * 与えられた [current] と [event] から次の状態を返す。
     */
    fun transition(
        current: OverlayState,
        event: OverlayEvent,
    ): OverlayState {
        return when (current) {
            is OverlayState.Idle -> reduceIdle(current, event)
            is OverlayState.Tracking -> reduceTracking(current, event)
            is OverlayState.Paused -> reducePaused(current, event)
            is OverlayState.Suggesting -> reduceSuggesting(current, event)
            is OverlayState.Disabled -> reduceDisabled(current, event)
        }
    }

    private fun reduceIdle(
        current: OverlayState.Idle,
        event: OverlayEvent,
    ): OverlayState {
        return when (event) {
            is OverlayEvent.EnterTargetApp -> OverlayState.Tracking(event.packageName)

            is OverlayEvent.SettingsChanged -> {
                if (event.settings.overlayEnabled) current else OverlayState.Disabled
            }

            is OverlayEvent.OverlayDisabled -> OverlayState.Disabled

            // Idle 中の ScreenOn/Off や LeaveTargetApp は無視
            else -> current
        }
    }

    private fun reduceTracking(
        current: OverlayState.Tracking,
        event: OverlayEvent,
    ): OverlayState {
        return when (event) {
            is OverlayEvent.LeaveTargetApp -> OverlayState.Idle

            is OverlayEvent.ScreenOff -> {
                // 画面 OFF 中はセッションは猶予扱いになるが、
                // Overlay 側は Idle 相当として扱う（Paused は SessionManager に任せる）。
                OverlayState.Idle
            }

            is OverlayEvent.SettingsChanged -> {
                if (event.settings.overlayEnabled) current else OverlayState.Disabled
            }

            is OverlayEvent.OverlayDisabled -> OverlayState.Disabled

            // EnterTargetApp(同じ package) や ScreenOn は Tracking 継続
            else -> current
        }
    }

    private fun reducePaused(
        current: OverlayState.Paused,
        event: OverlayEvent,
    ): OverlayState {
        // 現段階では Paused を明示的には使っていないので、
        // Idle / Tracking 相当の簡易遷移だけ定義しておく。
        return when (event) {
            is OverlayEvent.EnterTargetApp -> OverlayState.Tracking(event.packageName)
            is OverlayEvent.LeaveTargetApp,
            is OverlayEvent.ScreenOff -> OverlayState.Idle

            is OverlayEvent.SettingsChanged -> {
                if (event.settings.overlayEnabled) current else OverlayState.Disabled
            }

            is OverlayEvent.OverlayDisabled -> OverlayState.Disabled

            else -> current
        }
    }

    private fun reduceSuggesting(
        current: OverlayState.Suggesting,
        event: OverlayEvent,
    ): OverlayState {
        // Suggesting → Tracking / Idle の遷移は今後の拡張で詳細化する。
        return when (event) {
            is OverlayEvent.LeaveTargetApp,
            is OverlayEvent.ScreenOff -> OverlayState.Idle

            is OverlayEvent.SettingsChanged -> {
                if (event.settings.overlayEnabled) current else OverlayState.Disabled
            }

            is OverlayEvent.OverlayDisabled -> OverlayState.Disabled

            else -> current
        }
    }

    private fun reduceDisabled(
        current: OverlayState.Disabled,
        event: OverlayEvent,
    ): OverlayState {
        return when (event) {
            is OverlayEvent.SettingsChanged -> {
                // overlayEnabled が true に戻ったら Idle に遷移
                if (event.settings.overlayEnabled) OverlayState.Idle else current
            }

            else -> current
        }
    }
}
