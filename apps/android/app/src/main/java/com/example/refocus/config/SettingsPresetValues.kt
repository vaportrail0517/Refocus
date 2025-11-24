package com.example.refocus.config

import com.example.refocus.core.model.Settings
import com.example.refocus.core.model.SettingsPreset

object SettingsPresetValues {

    // 通常利用時のプリセット
    val Default: Settings = Settings(
        gracePeriodMillis = SettingsDefaults.GRACE_PERIOD_MILLIS,
        pollingIntervalMillis = SettingsDefaults.POLLING_INTERVAL_MILLIS,
        minFontSizeSp = SettingsDefaults.MIN_FONT_SIZE_SP,
        maxFontSizeSp = SettingsDefaults.MAX_FONT_SIZE_SP,
        timeToMaxMinutes = SettingsDefaults.TIME_TO_MAX_MINUTES,
        positionX = SettingsDefaults.POSITION_X,
        positionY = SettingsDefaults.POSITION_Y,
        touchMode = SettingsDefaults.TOUCH_MODE,
        overlayEnabled = SettingsDefaults.OVERLAY_ENABLED,
        autoStartOnBoot = SettingsDefaults.AUTO_START_ON_BOOT,
        suggestionEnabled = SettingsDefaults.SUGGESTION_ENABLED,
        suggestionTriggerSeconds = SettingsDefaults.SUGGESTION_TRIGGER_SECONDS,
        suggestionTimeoutSeconds = SettingsDefaults.SUGGESTION_TIMEOUT_SECONDS,
        suggestionCooldownSeconds = SettingsDefaults.SUGGESTION_COOLDOWN_SECONDS,
        suggestionForegroundStableSeconds = SettingsDefaults.SUGGESTION_FOREGROUND_STABLE_SECONDS,
        restSuggestionEnabled = SettingsDefaults.REST_SUGGESTION_ENABLED,
        suggestionInteractionLockoutMillis = SettingsDefaults.SUGGESTION_INTERACTION_LOCKOUT_MS,
    )

    // デバッグ用
    val Debug: Settings = Settings(
        gracePeriodMillis = 30_000L,
        pollingIntervalMillis = 500L,
        minFontSizeSp = 32f,
        maxFontSizeSp = 96f,
        timeToMaxMinutes = 2,

        // 起動系はユーザ選好なので SettingsDefaults をそのまま使う
        overlayEnabled = SettingsDefaults.OVERLAY_ENABLED,
        autoStartOnBoot = SettingsDefaults.AUTO_START_ON_BOOT,

        // 位置・タッチモードも「その時の状態」だが、基準値として SettingsDefaults を踏襲
        positionX = SettingsDefaults.POSITION_X,
        positionY = SettingsDefaults.POSITION_Y,

        // 提案周りは「体感しやすい値」に寄せる
        suggestionEnabled = true,
        suggestionTriggerSeconds = 20,
        suggestionTimeoutSeconds = 8,
        suggestionCooldownSeconds = 50,
        suggestionForegroundStableSeconds = 10,
        restSuggestionEnabled = true,
        suggestionInteractionLockoutMillis = 400L,
    )

    fun forPreset(preset: SettingsPreset): Settings = when (preset) {
        SettingsPreset.Default -> Default
        SettingsPreset.Debug -> Debug
        SettingsPreset.Custom -> Default  // Custom は DataStore 上の値そのまま使う
    }
}