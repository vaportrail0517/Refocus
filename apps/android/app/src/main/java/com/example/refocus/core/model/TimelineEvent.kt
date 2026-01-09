package com.example.refocus.core.model

/**
 * Refocus 内の「時刻を持った出来事」をすべて表現するイベント。
 *
 * - DB には TimelineEventEntity として 1 行ずつ保存される
 * - セッション / 監視期間 / 提案 / 統計 などはこのイベント列から再構成する
 */
sealed interface TimelineEvent {
    val id: Long?
    val timestampMillis: Long
}

/** サービスのライフサイクル（OverlayService の起動/停止） */
data class ServiceLifecycleEvent(
    override val id: Long? = null,
    override val timestampMillis: Long,
    val state: ServiceState,
) : TimelineEvent

enum class ServiceState {
    Started,
    Stopped,
}

/** サービス設定（有効/無効・再起動時自動起動など） */
data class ServiceConfigEvent(
    override val id: Long? = null,
    override val timestampMillis: Long,
    val config: ServiceConfigKind,
    val state: ServiceConfigState,
    /** source/reason などのメタ情報（解析用）。必要なければ null */
    val meta: String? = null,
) : TimelineEvent

enum class ServiceConfigKind {
    OverlayEnabled,
    AutoStartOnBoot,
}

enum class ServiceConfigState {
    Enabled,
    Disabled,
}

/** 権限の状態変化 (UsageStats / Overlay / Notification など) */
data class PermissionEvent(
    override val id: Long? = null,
    override val timestampMillis: Long,
    val permission: PermissionKind,
    val state: PermissionState,
) : TimelineEvent

enum class PermissionKind {
    UsageStats,
    Overlay,
    Notification,
}

enum class PermissionState {
    Granted,
    Revoked,
}

/** 画面 ON/OFF */
data class ScreenEvent(
    override val id: Long? = null,
    override val timestampMillis: Long,
    val state: ScreenState,
) : TimelineEvent

enum class ScreenState {
    On,
    Off,
}

/** フォアグラウンドアプリの変更 */
data class ForegroundAppEvent(
    override val id: Long? = null,
    override val timestampMillis: Long,
    val packageName: String?, // null = ホーム等
) : TimelineEvent

/** 対象アプリのリスト変更 */
data class TargetAppsChangedEvent(
    override val id: Long? = null,
    override val timestampMillis: Long,
    val targetPackages: Set<String>,
) : TimelineEvent

/** 提案カードが表示された */
data class SuggestionShownEvent(
    override val id: Long? = null,
    override val timestampMillis: Long,
    val packageName: String,
    val suggestionId: Long,
) : TimelineEvent

/** 提案カードに対する操作 (スヌーズ / 閉じる / 受け入れなど) */
data class SuggestionDecisionEvent(
    override val id: Long? = null,
    override val timestampMillis: Long,
    val packageName: String,
    val suggestionId: Long,
    val decision: SuggestionDecision,
) : TimelineEvent

/**
 * Refocus の UI（提案やミニゲーム）を表示している間の「計測中断」を表すイベント。
 *
 * - セッション自体は継続しているが，利用時間計測やオーバーレイの表示値を一時停止するために使う
 * - 結果（成功/失敗など）は持たず，「開始/終了」と「何を表示していたか」だけを記録する
 */
data class UiInterruptionEvent(
    override val id: Long? = null,
    override val timestampMillis: Long,
    /** 中断対象となっている前面アプリ（対象アプリ） */
    val packageName: String,
    val source: UiInterruptionSource,
    val state: UiInterruptionState,
) : TimelineEvent

enum class UiInterruptionSource {
    Suggestion,
    MiniGame,
}

enum class UiInterruptionState {
    Start,
    End,
}

/**
 * 設定変更イベント。
 *
 * - 今回の設計では「過去を現在の設定で再解釈する」ので、
 *   設定値そのものはこのイベントからは参照しない。
 * - ログ用途やタイムライン表示用。
 */
data class SettingsChangedEvent(
    override val id: Long? = null,
    override val timestampMillis: Long,
    val key: String,
    val newValueDescription: String?,
) : TimelineEvent
