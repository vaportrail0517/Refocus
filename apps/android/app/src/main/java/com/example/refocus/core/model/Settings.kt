package com.example.refocus.core.model

import com.example.refocus.config.SettingsDefaults

data class Settings(
    // --- セッション・監視まわり ---
    val gracePeriodMillis: Long = SettingsDefaults.GRACE_PERIOD_MILLIS,
    val pollingIntervalMillis: Long = SettingsDefaults.POLLING_INTERVAL_MILLIS,

    // --- オーバーレイ見た目 ---
    val minFontSizeSp: Float = SettingsDefaults.MIN_FONT_SIZE_SP,
    val maxFontSizeSp: Float = SettingsDefaults.MAX_FONT_SIZE_SP,
    val timeToMaxMinutes: Int = SettingsDefaults.TIME_TO_MAX_MINUTES,
    val positionX: Int = SettingsDefaults.POSITION_X,
    val positionY: Int = SettingsDefaults.POSITION_Y,
    val touchMode: OverlayTouchMode = SettingsDefaults.TOUCH_MODE,

    val growthMode: OverlayGrowthMode = SettingsDefaults.GROWTH_MODE,
    val colorMode: OverlayColorMode = SettingsDefaults.COLOR_MODE,
    val fixedColorArgb: Int = SettingsDefaults.FIXED_COLOR_ARGB,
    val gradientStartColorArgb: Int = SettingsDefaults.GRADIENT_START_COLOR_ARGB,
    val gradientMiddleColorArgb: Int = SettingsDefaults.GRADIENT_MIDDLE_COLOR_ARGB,
    val gradientEndColorArgb: Int = SettingsDefaults.GRADIENT_END_COLOR_ARGB,

    // --- 起動・有効/無効 ---
    val overlayEnabled: Boolean = SettingsDefaults.OVERLAY_ENABLED,
    val autoStartOnBoot: Boolean = SettingsDefaults.AUTO_START_ON_BOOT,

    // --- 提案機能（Suggestion） ---
    val suggestionEnabled: Boolean = SettingsDefaults.SUGGESTION_ENABLED,
    val suggestionTriggerSeconds: Int = SettingsDefaults.SUGGESTION_TRIGGER_SECONDS,
    val suggestionTimeoutSeconds: Int = SettingsDefaults.SUGGESTION_TIMEOUT_SECONDS,
    val suggestionCooldownSeconds: Int = SettingsDefaults.SUGGESTION_COOLDOWN_SECONDS,
    val suggestionForegroundStableSeconds: Int = SettingsDefaults.SUGGESTION_FOREGROUND_STABLE_SECONDS,
    val restSuggestionEnabled: Boolean = SettingsDefaults.REST_SUGGESTION_ENABLED,
    val suggestionInteractionLockoutMillis: Long = SettingsDefaults.SUGGESTION_INTERACTION_LOCKOUT_MS,
)
