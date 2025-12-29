package com.example.refocus.domain.overlay.engine

import com.example.refocus.core.model.Customize

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
    ): OverlayState =
        when (current) {
            is OverlayState.Idle -> reduceIdle(current, event)
            is OverlayState.Tracking -> reduceTracking(current, event)
            is OverlayState.Paused -> reducePaused(current, event)
            is OverlayState.Suggesting -> reduceSuggesting(current, event)
            is OverlayState.Disabled -> reduceDisabled(current, event)
        }

    private fun reduceIdle(
        current: OverlayState.Idle,
        event: OverlayEvent,
    ): OverlayState =
        when (event) {
            is OverlayEvent.EnterTargetApp -> OverlayState.Tracking(event.packageName)

            is OverlayEvent.SettingsChanged -> {
                if (event.customize.overlayEnabled) current else OverlayState.Disabled
            }

            is OverlayEvent.OverlayDisabled -> OverlayState.Disabled

            // Idle 中の ScreenOn/Off や LeaveTargetApp は無視
            else -> current
        }

    private fun reduceTracking(
        current: OverlayState.Tracking,
        event: OverlayEvent,
    ): OverlayState =
        when (event) {
            is OverlayEvent.LeaveTargetApp -> OverlayState.Idle

            is OverlayEvent.ScreenOff -> {
                // 画面 OFF 中はセッションは猶予扱いになるが、
                // Overlay 側は Idle 相当として扱う（Paused は SessionManager に任せる）。
                OverlayState.Idle
            }

            is OverlayEvent.SettingsChanged -> {
                if (event.customize.overlayEnabled) current else OverlayState.Disabled
            }

            is OverlayEvent.OverlayDisabled -> OverlayState.Disabled

            // EnterTargetApp(同じ package) や ScreenOn は Tracking 継続
            else -> current
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
            is OverlayEvent.ScreenOff,
            -> OverlayState.Idle

            is OverlayEvent.SettingsChanged -> {
                if (event.customize.overlayEnabled) current else OverlayState.Disabled
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
            is OverlayEvent.ScreenOff,
            -> OverlayState.Idle

            is OverlayEvent.SettingsChanged -> {
                if (event.customize.overlayEnabled) current else OverlayState.Disabled
            }

            is OverlayEvent.OverlayDisabled -> OverlayState.Disabled

            else -> current
        }
    }

    private fun reduceDisabled(
        current: OverlayState.Disabled,
        event: OverlayEvent,
    ): OverlayState =
        when (event) {
            is OverlayEvent.SettingsChanged -> {
                // overlayEnabled が true に戻ったら Idle に遷移
                if (event.customize.overlayEnabled) OverlayState.Idle else current
            }

            else -> current
        }
}

/**
 * Overlay のトップレベル状態を表すドメインモデル。
 *
 * - Idle: 対象アプリではなく、オーバーレイも出ていない。
 * - Tracking: 対象アプリが前面で、連続使用時間を追跡している。
 * - Paused: 一時的に対象アプリから離脱している（猶予中）状態。
 * - Suggesting: 提案オーバーレイを出している状態。
 * - Disabled: 設定や権限の理由で Overlay 機能自体が無効。
 *
 * 現時点では Idle / Tracking / Disabled を主に利用し、
 * Paused / Suggesting は今後の拡張のためのプレースホルダとする。
 */
sealed class OverlayState {
    object Idle : OverlayState()

    data class Tracking(
        val packageName: String,
    ) : OverlayState()

    data class Paused(
        val packageName: String,
    ) : OverlayState()

    data class Suggesting(
        val packageName: String,
    ) : OverlayState()

    object Disabled : OverlayState()
}

/**
 * OverlayStateMachine に入力されるイベント。
 *
 * - EnterTargetApp: 対象アプリが前面になった。
 * - LeaveTargetApp: 対象アプリが前面でなくなった。
 * - ScreenOff: 画面が OFF になった。パッケージ自体は OverlayState（Tracking の packageName）から取得する想定。
 * - ScreenOn: 画面が ON になった。現時点では状態遷移は行っていないが、将来 Paused → Tracking などに使える。
 * - SettingsChanged: 設定が更新された。overlayEnabled の変化などで Disabled <-> Idle を制御する。
 * - OverlayDisabled: Overlay 機能全体が無効になった（例: overlayEnabled = false など）。直接 Disabled に落としたい場合用のショートカット。
 *
 * OverlayOrchestrator / Service 側から生成し、OverlayStateMachine に渡す。
 */
sealed class OverlayEvent {
    data class EnterTargetApp(
        val packageName: String,
        val nowMillis: Long,
        val nowElapsedRealtime: Long,
    ) : OverlayEvent()

    data class LeaveTargetApp(
        val packageName: String,
        val nowMillis: Long,
        val nowElapsedRealtime: Long,
    ) : OverlayEvent()

    data class ScreenOff(
        val nowMillis: Long,
        val nowElapsedRealtime: Long,
    ) : OverlayEvent()

    data class ScreenOn(
        val nowMillis: Long,
        val nowElapsedRealtime: Long,
    ) : OverlayEvent()

    data class SettingsChanged(
        val customize: Customize,
    ) : OverlayEvent()

    object OverlayDisabled : OverlayEvent()
}
