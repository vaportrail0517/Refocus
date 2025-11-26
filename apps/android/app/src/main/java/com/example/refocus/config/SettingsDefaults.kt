package com.example.refocus.config

import com.example.refocus.core.model.OverlayColorMode
import com.example.refocus.core.model.OverlayGrowthMode
import com.example.refocus.core.model.OverlayTouchMode

object SettingsDefaults {
    // --- セッション・監視 ---
    const val GRACE_PERIOD_MILLIS: Long = 300_000L
    const val POLLING_INTERVAL_MILLIS: Long = 500L

    // --- オーバーレイ見た目 ---
    const val MIN_FONT_SIZE_SP: Float = 16f
    const val MAX_FONT_SIZE_SP: Float = 64f
    const val TIME_TO_MAX_MINUTES: Int = 15
    const val POSITION_X: Int = 24
    const val POSITION_Y: Int = 120
    val TOUCH_MODE: OverlayTouchMode = OverlayTouchMode.PassThrough

    val GROWTH_MODE: OverlayGrowthMode = OverlayGrowthMode.SlowToFast
    val COLOR_MODE: OverlayColorMode = OverlayColorMode.GradientThree

    const val FIXED_COLOR_ARGB: Int = 0xFF222222.toInt()
    const val GRADIENT_START_COLOR_ARGB: Int = 0xFF4CAF50.toInt()
    const val GRADIENT_MIDDLE_COLOR_ARGB: Int = 0xFFFFC107.toInt()
    const val GRADIENT_END_COLOR_ARGB: Int = 0xFFF44336.toInt()

    // --- 起動・有効/無効 ---
    const val OVERLAY_ENABLED: Boolean = true
    const val AUTO_START_ON_BOOT: Boolean = true

    // --- 提案機能 ---
    const val SUGGESTION_ENABLED: Boolean = true
    const val SUGGESTION_TRIGGER_SECONDS: Int = 15 * 60
    const val SUGGESTION_TIMEOUT_SECONDS: Int = 8
    const val SUGGESTION_COOLDOWN_SECONDS: Int = 10 * 60
    const val SUGGESTION_FOREGROUND_STABLE_SECONDS: Int = 5 * 60
    const val REST_SUGGESTION_ENABLED: Boolean = true
    const val SUGGESTION_INTERACTION_LOCKOUT_MS: Long = 400L
}