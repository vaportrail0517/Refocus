package com.example.refocus.core.model

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
        val settings: Settings,
    ) : OverlayEvent()

    object OverlayDisabled : OverlayEvent()
}

enum class OverlaySuggestionMode {
    Goal,   // 「やりたいこと」モード
    Rest    // 「休憩」モード
}

enum class OverlayTouchMode {
    Drag,        // タイマーをドラッグして動かす
    PassThrough, // タップは背面の UI にそのまま渡す
}

/**
 * タイマーの成長モード。
 * p: 0〜1（timeToMaxMinutes に対する経過割合）
 *
 * - Linear: 線形
 * - FastToSlow: 初めは速く、大きくなるにつれてゆっくり
 * - SlowToFast: 初めはゆっくり、後半は速く
 * - SlowFastSlow: 真ん中あたりで一番速い（スローインアウト）
 */
enum class OverlayGrowthMode {
    Linear,
    FastToSlow,
    SlowToFast,
    SlowFastSlow,
}

/**
 * タイマー背景色のモード。
 *
 * - Fixed: 単色
 * - GradientTwo: 2色グラデーション
 * - GradientThree: 3色グラデーション
 */
enum class OverlayColorMode {
    Fixed,
    GradientTwo,
    GradientThree,
}