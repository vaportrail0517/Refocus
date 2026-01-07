package com.example.refocus.core.model

/**
 * Customize のデフォルト値。
 *
 * - core 内に置くことで，Customize（core.model）が上位レイヤ（config 等）へ依存しないようにする．
 * - 将来のマルチモジュール化で :core を切り出しても破綻しない構造にする．
 */
object CustomizeDefaults {
    // --- セッション・監視 ---
    const val GRACE_PERIOD_MILLIS: Long = 300_000L
    const val POLLING_INTERVAL_MILLIS: Long = 500L

    // --- オーバーレイ見た目 ---
    const val MIN_FONT_SIZE_SP: Float = 24f
    const val MAX_FONT_SIZE_SP: Float = 64f

    const val TIME_TO_MAX_SECONDS: Int = 20 * 60

    @Deprecated(
        message = "Use TIME_TO_MAX_SECONDS",
        replaceWith = ReplaceWith("TIME_TO_MAX_SECONDS / 60"),
    )
    const val TIME_TO_MAX_MINUTES: Int = TIME_TO_MAX_SECONDS / 60

    const val POSITION_X: Int = 24
    const val POSITION_Y: Int = 120

    val TOUCH_MODE: TimerTouchMode = TimerTouchMode.PassThrough
    val TIMER_TIME_MODE: TimerTimeMode = TimerTimeMode.SessionElapsed
    val TIMER_VISUAL_TIME_BASIS: TimerVisualTimeBasis = TimerVisualTimeBasis.SessionElapsed

    val GROWTH_MODE: TimerGrowthMode = TimerGrowthMode.SlowToFast
    val COLOR_MODE: TimerColorMode = TimerColorMode.GradientThree

    const val FIXED_COLOR_ARGB: Int = 0xFF222222.toInt()
    const val GRADIENT_START_COLOR_ARGB: Int = 0xFF4CAF50.toInt()
    const val GRADIENT_MIDDLE_COLOR_ARGB: Int = 0xFFFFC107.toInt()
    const val GRADIENT_END_COLOR_ARGB: Int = 0xFFF44336.toInt()

    // --- 起動・有効/無効 ---
    const val OVERLAY_ENABLED: Boolean = true
    const val AUTO_START_ON_BOOT: Boolean = true

    // --- 提案機能 ---
    const val SUGGESTION_ENABLED: Boolean = true
    const val REST_SUGGESTION_ENABLED: Boolean = true
    const val SUGGESTION_TRIGGER_SECONDS: Int = 15 * 60
    const val SUGGESTION_TIMEOUT_SECONDS: Int = 0
    const val SUGGESTION_COOLDOWN_SECONDS: Int = 10 * 60
    const val SUGGESTION_FOREGROUND_STABLE_SECONDS: Int = 5 * 60
    const val SUGGESTION_INTERACTION_LOCKOUT_MS: Long = 400L

    // --- ミニゲーム（提案フローに挟むチャレンジ） ---
    const val MINI_GAME_ENABLED: Boolean = false
    val MINI_GAME_ORDER: MiniGameOrder = MiniGameOrder.BeforeSuggestion
    val MINI_GAME_KIND: MiniGameKind = MiniGameKind.FlashAnzan
}
