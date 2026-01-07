package com.example.refocus.core.settings

/**
 * タイムラインに記録する「設定変更イベント（SettingsChangedEvent）」のキー定義．
 *
 * このキー文字列は DB に永続化され，履歴画面などで参照されるため，後方互換性のある形で管理する．
 *
 * - 既存キーの文字列は原則として変更しない
 * - 新規追加は必ずこのファイルへ追記する
 */
@JvmInline
value class SettingsChangeKey(
    val value: String,
) {
    override fun toString(): String = value
}

object SettingsChangeKeys {
    // --- 基本設定 ---
    val OVERLAY_ENABLED = SettingsChangeKey("overlayEnabled")
    val AUTO_START_ON_BOOT = SettingsChangeKey("autoStartOnBoot")
    val TIMER_TIME_MODE = SettingsChangeKey("timerTimeMode")
    val TIMER_VISUAL_TIME_BASIS = SettingsChangeKey("timerVisualTimeBasis")
    val TOUCH_MODE = SettingsChangeKey("touchMode")
    val SETTINGS_PRESET = SettingsChangeKey("settingsPreset")

    // --- セッション・監視 ---
    val GRACE_PERIOD_MILLIS = SettingsChangeKey("gracePeriodMillis")
    val POLLING_INTERVAL_MILLIS = SettingsChangeKey("pollingIntervalMillis")

    // --- オーバーレイ見た目 ---
    val MIN_FONT_SIZE_SP = SettingsChangeKey("minFontSizeSp")
    val MAX_FONT_SIZE_SP = SettingsChangeKey("maxFontSizeSp")
    val TIME_TO_MAX_SECONDS = SettingsChangeKey("timeToMaxSeconds")

    // 旧キー: 既存のタイムラインイベント互換のため残す
    val TIME_TO_MAX_MINUTES = SettingsChangeKey("timeToMaxMinutes")
    val GROWTH_MODE = SettingsChangeKey("growthMode")
    val COLOR_MODE = SettingsChangeKey("colorMode")
    val FIXED_COLOR_ARGB = SettingsChangeKey("fixedColorArgb")
    val GRADIENT_START_COLOR_ARGB = SettingsChangeKey("gradientStartColorArgb")
    val GRADIENT_MIDDLE_COLOR_ARGB = SettingsChangeKey("gradientMiddleColorArgb")
    val GRADIENT_END_COLOR_ARGB = SettingsChangeKey("gradientEndColorArgb")

    // --- 提案 ---
    val SUGGESTION_ENABLED = SettingsChangeKey("suggestionEnabled")
    val REST_SUGGESTION_ENABLED = SettingsChangeKey("restSuggestionEnabled")
    val SUGGESTION_TRIGGER_SECONDS = SettingsChangeKey("suggestionTriggerSeconds")
    val SUGGESTION_FOREGROUND_STABLE_SECONDS = SettingsChangeKey("suggestionForegroundStableSeconds")
    val SUGGESTION_COOLDOWN_SECONDS = SettingsChangeKey("suggestionCooldownSeconds")
    val SUGGESTION_TIMEOUT_SECONDS = SettingsChangeKey("suggestionTimeoutSeconds")
    val SUGGESTION_INTERACTION_LOCKOUT_MILLIS = SettingsChangeKey("suggestionInteractionLockoutMillis")

    // --- ミニゲーム ---
    val MINI_GAME_ENABLED = SettingsChangeKey("miniGameEnabled")
    val MINI_GAME_ORDER = SettingsChangeKey("miniGameOrder")
    val MINI_GAME_KIND = SettingsChangeKey("miniGameKind")
    // --- その他 ---
    val RESET_TO_DEFAULTS = SettingsChangeKey("resetToDefaults")
    val OVERLAY_POSITION = SettingsChangeKey("overlayPosition")
}
