package com.example.refocus.core.model

enum class OverlayTouchMode {
    Drag,        // タイマーをドラッグして動かす
    PassThrough, // タップは背面の UI にそのまま渡す
}

data class OverlaySettings(
    // --- セッション・監視まわり ---
    val gracePeriodMillis: Long = DEFAULT_GRACE_PERIOD_MILLIS,
    val pollingIntervalMillis: Long = DEFAULT_POLLING_INTERVAL_MILLIS,

    // --- オーバーレイ見た目 ---
    val minFontSizeSp: Float = DEFAULT_MIN_FONT_SIZE_SP,
    val maxFontSizeSp: Float = DEFAULT_MAX_FONT_SIZE_SP,
    val timeToMaxMinutes: Int = DEFAULT_TIME_TO_MAX_MINUTES,
    val positionX: Int = DEFAULT_POSITION_X,
    val positionY: Int = DEFAULT_POSITION_Y,
    val touchMode: OverlayTouchMode = DEFAULT_TOUCH_MODE,
    // val colorMode: OverlayColorMode = OverlayColorMode.SingleColor,

    // --- 起動・有効/無効 ---
    val overlayEnabled: Boolean = DEFAULT_OVERLAY_ENABLED,
    val autoStartOnBoot: Boolean = DEFAULT_AUTO_START_ON_BOOT,

    // --- 提案機能（Suggestion） ---
    val suggestionEnabled: Boolean = DEFAULT_SUGGESTION_ENABLED,
    val suggestionTriggerSeconds: Int = DEFAULT_SUGGESTION_TRIGGER_SECONDS, // 連続使用時間が何秒を超えたら提案を出すか
    val suggestionTimeoutSeconds: Int = DEFAULT_SUGGESTION_TIMEOUT_SECONDS, // 提案カードの自動クローズまでの秒数
    val suggestionCooldownSeconds: Int = DEFAULT_SUGGESTION_COOLDOWN_SECONDS, // 提案を再度出すまでのクールダウン秒数（全アプリ共通）, 「あとで」「スワイプで消す」「タイムアウト」後に適用される
    val suggestionForegroundStableSeconds: Int = DEFAULT_SUGGESTION_FOREGROUND_STABLE_SECONDS, // 対象アプリが前面に来てから何秒以上経過していれば「提案を出してよい」とみなすか
    val restSuggestionEnabled: Boolean = DEFAULT_REST_SUGGESTION_ENABLED, // やりたいことが登録されていない場合に「休憩」をフォールバックとして提案するか
    val suggestionInteractionLockoutMillis: Long = DEFAULT_SUGGESTION_INTERACTION_LOCKOUT_MS, // 提案表示直後の誤操作を防ぐロックアウト時間（ミリ秒）
) {
    companion object {
        // --- セッション・監視 ---
        const val DEFAULT_GRACE_PERIOD_MILLIS: Long = 300_000L
        const val DEFAULT_POLLING_INTERVAL_MILLIS: Long = 500L

        // --- オーバーレイ見た目 ---
        const val DEFAULT_MIN_FONT_SIZE_SP: Float = 12f
        const val DEFAULT_MAX_FONT_SIZE_SP: Float = 40f
        const val DEFAULT_TIME_TO_MAX_MINUTES: Int = 30
        const val DEFAULT_POSITION_X: Int = 24
        const val DEFAULT_POSITION_Y: Int = 120
        val DEFAULT_TOUCH_MODE: OverlayTouchMode = OverlayTouchMode.Drag

        // --- 起動・有効/無効 ---
        const val DEFAULT_OVERLAY_ENABLED: Boolean = true
        const val DEFAULT_AUTO_START_ON_BOOT: Boolean = true

        // --- 提案機能 ---
        const val DEFAULT_SUGGESTION_ENABLED: Boolean = true
        const val DEFAULT_SUGGESTION_TRIGGER_SECONDS: Int = 30
        const val DEFAULT_SUGGESTION_TIMEOUT_SECONDS: Int = 8
        const val DEFAULT_SUGGESTION_COOLDOWN_SECONDS: Int = 30
        const val DEFAULT_SUGGESTION_FOREGROUND_STABLE_SECONDS: Int = 20
        const val DEFAULT_REST_SUGGESTION_ENABLED: Boolean = true
        const val DEFAULT_SUGGESTION_INTERACTION_LOCKOUT_MS: Long = 400L
    }
}
