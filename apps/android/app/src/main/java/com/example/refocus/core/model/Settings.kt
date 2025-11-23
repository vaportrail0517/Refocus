package com.example.refocus.core.model

enum class OverlayTouchMode {
    Drag,        // タイマーをドラッグして動かす
    PassThrough, // タップは背面の UI にそのまま渡す
}

/**
 * オーバーレイと提案機能まわりの設定値。
 *
 * 数値のデフォルトは SettingsConfig.Defaults に集約しておき、
 * ここではそれを参照するだけにする。
 */
data class Settings(
    // --- セッション・監視まわり ---
    val gracePeriodMillis: Long = SettingsConfig.Defaults.GRACE_PERIOD_MILLIS,
    val pollingIntervalMillis: Long = SettingsConfig.Defaults.POLLING_INTERVAL_MILLIS,

    // --- オーバーレイ見た目 ---
    val minFontSizeSp: Float = SettingsConfig.Defaults.MIN_FONT_SIZE_SP,
    val maxFontSizeSp: Float = SettingsConfig.Defaults.MAX_FONT_SIZE_SP,
    val timeToMaxMinutes: Int = SettingsConfig.Defaults.TIME_TO_MAX_MINUTES,
    val positionX: Int = SettingsConfig.Defaults.POSITION_X,
    val positionY: Int = SettingsConfig.Defaults.POSITION_Y,
    val touchMode: OverlayTouchMode = SettingsConfig.Defaults.TOUCH_MODE,
    // val colorMode: OverlayColorMode = OverlayColorMode.SingleColor,

    // --- 起動・有効/無効 ---
    val overlayEnabled: Boolean = SettingsConfig.Defaults.OVERLAY_ENABLED,
    val autoStartOnBoot: Boolean = SettingsConfig.Defaults.AUTO_START_ON_BOOT,

    // --- 提案機能（Suggestion） ---
    val suggestionEnabled: Boolean = SettingsConfig.Defaults.SUGGESTION_ENABLED,
    val suggestionTriggerSeconds: Int = SettingsConfig.Defaults.SUGGESTION_TRIGGER_SECONDS,
    val suggestionTimeoutSeconds: Int = SettingsConfig.Defaults.SUGGESTION_TIMEOUT_SECONDS,
    val suggestionCooldownSeconds: Int = SettingsConfig.Defaults.SUGGESTION_COOLDOWN_SECONDS,
    val suggestionForegroundStableSeconds: Int = SettingsConfig.Defaults.SUGGESTION_FOREGROUND_STABLE_SECONDS,
    val restSuggestionEnabled: Boolean = SettingsConfig.Defaults.REST_SUGGESTION_ENABLED,
    val suggestionInteractionLockoutMillis: Long = SettingsConfig.Defaults.SUGGESTION_INTERACTION_LOCKOUT_MS,
)
