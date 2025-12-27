package com.example.refocus.core.model

data class Customize(
    // --- セッション・監視まわり ---
    val gracePeriodMillis: Long = CustomizeDefaults.GRACE_PERIOD_MILLIS,
    val pollingIntervalMillis: Long = CustomizeDefaults.POLLING_INTERVAL_MILLIS,

    // --- オーバーレイ見た目 ---
    val minFontSizeSp: Float = CustomizeDefaults.MIN_FONT_SIZE_SP,
    val maxFontSizeSp: Float = CustomizeDefaults.MAX_FONT_SIZE_SP,
    val timeToMaxSeconds: Int = CustomizeDefaults.TIME_TO_MAX_SECONDS,
    val positionX: Int = CustomizeDefaults.POSITION_X,
    val positionY: Int = CustomizeDefaults.POSITION_Y,
    val touchMode: TimerTouchMode = CustomizeDefaults.TOUCH_MODE,
    val timerTimeMode: TimerTimeMode = CustomizeDefaults.TIMER_TIME_MODE,
    val timerVisualTimeBasis: TimerVisualTimeBasis = CustomizeDefaults.TIMER_VISUAL_TIME_BASIS,

    val growthMode: TimerGrowthMode = CustomizeDefaults.GROWTH_MODE,
    val colorMode: TimerColorMode = CustomizeDefaults.COLOR_MODE,
    val fixedColorArgb: Int = CustomizeDefaults.FIXED_COLOR_ARGB,
    val gradientStartColorArgb: Int = CustomizeDefaults.GRADIENT_START_COLOR_ARGB,
    val gradientMiddleColorArgb: Int = CustomizeDefaults.GRADIENT_MIDDLE_COLOR_ARGB,
    val gradientEndColorArgb: Int = CustomizeDefaults.GRADIENT_END_COLOR_ARGB,

    // --- 起動・有効/無効 ---
    val overlayEnabled: Boolean = CustomizeDefaults.OVERLAY_ENABLED,
    val autoStartOnBoot: Boolean = CustomizeDefaults.AUTO_START_ON_BOOT,

    // --- 提案機能（Suggestion） ---
    val suggestionEnabled: Boolean = CustomizeDefaults.SUGGESTION_ENABLED,
    val restSuggestionEnabled: Boolean = CustomizeDefaults.REST_SUGGESTION_ENABLED,
    val suggestionTriggerSeconds: Int = CustomizeDefaults.SUGGESTION_TRIGGER_SECONDS,
    val suggestionTimeoutSeconds: Int = CustomizeDefaults.SUGGESTION_TIMEOUT_SECONDS,
    val suggestionCooldownSeconds: Int = CustomizeDefaults.SUGGESTION_COOLDOWN_SECONDS,
    val suggestionForegroundStableSeconds: Int = CustomizeDefaults.SUGGESTION_FOREGROUND_STABLE_SECONDS,
    val suggestionInteractionLockoutMillis: Long = CustomizeDefaults.SUGGESTION_INTERACTION_LOCKOUT_MS,
)

/**
 * 設定全体のプリセット種別。
 *
 * - Default: 通常利用向けの標準値
 * - Debug:   動作確認・デバッグ用の値
 * - Custom:  プリセットから変更された状態
 */
enum class CustomizePreset { Default, Debug, Custom }
