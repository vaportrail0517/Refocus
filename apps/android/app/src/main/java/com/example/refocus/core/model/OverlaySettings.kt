package com.example.refocus.core.model

enum class OverlayTouchMode {
    Drag,        // タイマーをドラッグして動かす
    PassThrough, // タップは背面の UI にそのまま渡す
}

/**
 * オーバーレイと提案機能まわりの設定値。
 *
 * 数値のデフォルトは OverlaySettingsConfig.Defaults に集約しておき、
 * ここではそれを参照するだけにする。
 */
data class OverlaySettings(
    // --- セッション・監視まわり ---
    val gracePeriodMillis: Long = OverlaySettingsConfig.Defaults.GRACE_PERIOD_MILLIS,
    val pollingIntervalMillis: Long = OverlaySettingsConfig.Defaults.POLLING_INTERVAL_MILLIS,

    // --- オーバーレイ見た目 ---
    val minFontSizeSp: Float = OverlaySettingsConfig.Defaults.MIN_FONT_SIZE_SP,
    val maxFontSizeSp: Float = OverlaySettingsConfig.Defaults.MAX_FONT_SIZE_SP,
    val timeToMaxMinutes: Int = OverlaySettingsConfig.Defaults.TIME_TO_MAX_MINUTES,
    val positionX: Int = OverlaySettingsConfig.Defaults.POSITION_X,
    val positionY: Int = OverlaySettingsConfig.Defaults.POSITION_Y,
    val touchMode: OverlayTouchMode = OverlaySettingsConfig.Defaults.TOUCH_MODE,
    // val colorMode: OverlayColorMode = OverlayColorMode.SingleColor,

    // --- 起動・有効/無効 ---
    val overlayEnabled: Boolean = OverlaySettingsConfig.Defaults.OVERLAY_ENABLED,
    val autoStartOnBoot: Boolean = OverlaySettingsConfig.Defaults.AUTO_START_ON_BOOT,

    // --- 提案機能（Suggestion） ---
    val suggestionEnabled: Boolean = OverlaySettingsConfig.Defaults.SUGGESTION_ENABLED,
    val suggestionTriggerSeconds: Int = OverlaySettingsConfig.Defaults.SUGGESTION_TRIGGER_SECONDS,
    val suggestionTimeoutSeconds: Int = OverlaySettingsConfig.Defaults.SUGGESTION_TIMEOUT_SECONDS,
    val suggestionCooldownSeconds: Int = OverlaySettingsConfig.Defaults.SUGGESTION_COOLDOWN_SECONDS,
    val suggestionForegroundStableSeconds: Int = OverlaySettingsConfig.Defaults.SUGGESTION_FOREGROUND_STABLE_SECONDS,
    val restSuggestionEnabled: Boolean = OverlaySettingsConfig.Defaults.REST_SUGGESTION_ENABLED,
    val suggestionInteractionLockoutMillis: Long = OverlaySettingsConfig.Defaults.SUGGESTION_INTERACTION_LOCKOUT_MS,
)
