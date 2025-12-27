package com.example.refocus.config

import com.example.refocus.core.model.Customize
import com.example.refocus.core.model.TimerColorMode
import com.example.refocus.core.model.TimerGrowthMode

object CustomizePresetValues {

    // 通常利用時のプリセット
    val Default: Customize = Customize(
        gracePeriodMillis = CustomizeDefaults.GRACE_PERIOD_MILLIS,
        pollingIntervalMillis = CustomizeDefaults.POLLING_INTERVAL_MILLIS,
        minFontSizeSp = CustomizeDefaults.MIN_FONT_SIZE_SP,
        maxFontSizeSp = CustomizeDefaults.MAX_FONT_SIZE_SP,
        timeToMaxSeconds = CustomizeDefaults.TIME_TO_MAX_SECONDS,
        positionX = CustomizeDefaults.POSITION_X,
        positionY = CustomizeDefaults.POSITION_Y,
        touchMode = CustomizeDefaults.TOUCH_MODE,
        timerTimeMode = CustomizeDefaults.TIMER_TIME_MODE,
        timerVisualTimeBasis = CustomizeDefaults.TIMER_VISUAL_TIME_BASIS,
        growthMode = CustomizeDefaults.GROWTH_MODE,
        colorMode = CustomizeDefaults.COLOR_MODE,
        fixedColorArgb = CustomizeDefaults.FIXED_COLOR_ARGB,
        gradientStartColorArgb = CustomizeDefaults.GRADIENT_START_COLOR_ARGB,
        gradientMiddleColorArgb = CustomizeDefaults.GRADIENT_MIDDLE_COLOR_ARGB,
        gradientEndColorArgb = CustomizeDefaults.GRADIENT_END_COLOR_ARGB,
        overlayEnabled = CustomizeDefaults.OVERLAY_ENABLED,
        autoStartOnBoot = CustomizeDefaults.AUTO_START_ON_BOOT,
        suggestionEnabled = CustomizeDefaults.SUGGESTION_ENABLED,
        restSuggestionEnabled = CustomizeDefaults.REST_SUGGESTION_ENABLED,
        suggestionTriggerSeconds = CustomizeDefaults.SUGGESTION_TRIGGER_SECONDS,
        suggestionTimeoutSeconds = CustomizeDefaults.SUGGESTION_TIMEOUT_SECONDS,
        suggestionCooldownSeconds = CustomizeDefaults.SUGGESTION_COOLDOWN_SECONDS,
        suggestionForegroundStableSeconds = CustomizeDefaults.SUGGESTION_FOREGROUND_STABLE_SECONDS,
        suggestionInteractionLockoutMillis = CustomizeDefaults.SUGGESTION_INTERACTION_LOCKOUT_MS,
    )

    // デバッグ用
    val Debug: Customize = Customize(
        gracePeriodMillis = 10_000L,
        pollingIntervalMillis = 500L,
        minFontSizeSp = 32f,
        maxFontSizeSp = 96f,
        timeToMaxSeconds = 60,

        // 起動系はユーザ選好なので CustomizeDefaults をそのまま使う
//        overlayEnabled = CustomizeDefaults.OVERLAY_ENABLED,
//        autoStartOnBoot = CustomizeDefaults.AUTO_START_ON_BOOT,

        // 位置・タッチモードも「その時の状態」だが、基準値として CustomizeDefaults を踏襲
        positionX = CustomizeDefaults.POSITION_X,
        positionY = CustomizeDefaults.POSITION_Y,
        timerTimeMode = CustomizeDefaults.TIMER_TIME_MODE,
        timerVisualTimeBasis = CustomizeDefaults.TIMER_VISUAL_TIME_BASIS,

        growthMode = TimerGrowthMode.SlowFastSlow,
        colorMode = TimerColorMode.GradientThree,
        fixedColorArgb = CustomizeDefaults.FIXED_COLOR_ARGB,
        gradientStartColorArgb = CustomizeDefaults.GRADIENT_START_COLOR_ARGB,
        gradientMiddleColorArgb = CustomizeDefaults.GRADIENT_MIDDLE_COLOR_ARGB,
        gradientEndColorArgb = CustomizeDefaults.GRADIENT_END_COLOR_ARGB,

        // 提案周りは「体感しやすい値」に寄せる
        suggestionEnabled = true,
        restSuggestionEnabled = true,
        suggestionTriggerSeconds = 20,
        suggestionTimeoutSeconds = 0,
        suggestionCooldownSeconds = 20,
        suggestionForegroundStableSeconds = 10,
        suggestionInteractionLockoutMillis = 400L,
    )

//    fun forPreset(preset: CustomizePreset): Customize = when (preset) {
//        CustomizePreset.Default -> Default
//        CustomizePreset.Debug -> Debug
//        CustomizePreset.Custom -> Default  // Custom は DataStore 上の値そのまま使う
//    }
}