package com.example.refocus.core.model

/**
 * OverlaySettings / 設定プリセット / 基本設定用プリセットの
 * 「数字」を一か所に集約するためのコンフィグ。
 *
 * ここを変えれば:
 * - デフォルト値
 * - Debug プリセット値
 * - 基本設定プリセット (Small / Medium / Large など)
 * がすべて一括で変わる。
 */
object OverlaySettingsConfig {

    // ----------------------------------------
    // 1. デフォルト値（OverlaySettings() の基準）
    // ----------------------------------------
    object Defaults {
        // --- セッション・監視 ---
        const val GRACE_PERIOD_MILLIS: Long = 300_000L          // 5分
        const val POLLING_INTERVAL_MILLIS: Long = 500L          // 0.5秒

        // --- オーバーレイ見た目 ---
        const val MIN_FONT_SIZE_SP: Float = 12f
        const val MAX_FONT_SIZE_SP: Float = 40f
        const val TIME_TO_MAX_MINUTES: Int = 30
        const val POSITION_X: Int = 24
        const val POSITION_Y: Int = 120
        val TOUCH_MODE: OverlayTouchMode = OverlayTouchMode.PassThrough

        // --- 起動・有効/無効 ---
        const val OVERLAY_ENABLED: Boolean = true
        const val AUTO_START_ON_BOOT: Boolean = true

        // --- 提案機能 ---
        const val SUGGESTION_ENABLED: Boolean = true
        const val SUGGESTION_TRIGGER_SECONDS: Int = 15 * 60
        const val SUGGESTION_TIMEOUT_SECONDS: Int = 8
        const val SUGGESTION_COOLDOWN_SECONDS: Int = 20 * 60
        const val SUGGESTION_FOREGROUND_STABLE_SECONDS: Int = 5 * 60
        const val REST_SUGGESTION_ENABLED: Boolean = true
        const val SUGGESTION_INTERACTION_LOCKOUT_MS: Long = 400L
    }

    /**
     * 「全体プリセット：Default」の OverlaySettings 一括値。
     * SettingsPresets.default などから参照される。
     */
    val Default: OverlaySettings = OverlaySettings(
        gracePeriodMillis = Defaults.GRACE_PERIOD_MILLIS,
        pollingIntervalMillis = Defaults.POLLING_INTERVAL_MILLIS,
        minFontSizeSp = Defaults.MIN_FONT_SIZE_SP,
        maxFontSizeSp = Defaults.MAX_FONT_SIZE_SP,
        timeToMaxMinutes = Defaults.TIME_TO_MAX_MINUTES,
        positionX = Defaults.POSITION_X,
        positionY = Defaults.POSITION_Y,
        touchMode = Defaults.TOUCH_MODE,
        overlayEnabled = Defaults.OVERLAY_ENABLED,
        autoStartOnBoot = Defaults.AUTO_START_ON_BOOT,
        suggestionEnabled = Defaults.SUGGESTION_ENABLED,
        suggestionTriggerSeconds = Defaults.SUGGESTION_TRIGGER_SECONDS,
        suggestionTimeoutSeconds = Defaults.SUGGESTION_TIMEOUT_SECONDS,
        suggestionCooldownSeconds = Defaults.SUGGESTION_COOLDOWN_SECONDS,
        suggestionForegroundStableSeconds = Defaults.SUGGESTION_FOREGROUND_STABLE_SECONDS,
        restSuggestionEnabled = Defaults.REST_SUGGESTION_ENABLED,
        suggestionInteractionLockoutMillis = Defaults.SUGGESTION_INTERACTION_LOCKOUT_MS,
    )

    /**
     * 「全体プリセット：Debug」の OverlaySettings 一括値。
     * 数値は現状の SettingsPresets.debug から移植。
     */
    val Debug: OverlaySettings = OverlaySettings(
        gracePeriodMillis = 30_000L,
        pollingIntervalMillis = 500L,
        minFontSizeSp = 32f,
        maxFontSizeSp = 96f,
        timeToMaxMinutes = 2,

        // 起動系はユーザ選好なので Defaults をそのまま使う
        overlayEnabled = Defaults.OVERLAY_ENABLED,
        autoStartOnBoot = Defaults.AUTO_START_ON_BOOT,

        // 位置・タッチモードも「その時の状態」だが、基準値として Defaults を踏襲
        positionX = Defaults.POSITION_X,
        positionY = Defaults.POSITION_Y,

        // 提案周りは「体感しやすい値」に寄せる
        suggestionEnabled = true,
        suggestionTriggerSeconds = 30,    // 10秒で提案発火
        suggestionTimeoutSeconds = 8,
        suggestionCooldownSeconds = 30,
        suggestionForegroundStableSeconds = 20,
        restSuggestionEnabled = true,
        suggestionInteractionLockoutMillis = 400L,
    )

    // ----------------------------------------------------------------
    // 2. 基本設定用プリセット（タイマー / グレース / 提案トリガ等）
    // ----------------------------------------------------------------

    // タイマーの文字サイズプリセット
    enum class FontPreset { Small, Medium, Large }

    private val FONT_PRESETS: Map<FontPreset, Pair<Float, Float>> = mapOf(
        FontPreset.Small to (10f to 30f),
        FontPreset.Medium to (12f to 40f),
        FontPreset.Large to (14f to 50f),
    )

    // 最大サイズまでの時間プリセット
    enum class TimeToMaxPreset { Slow, Normal, Fast }

    private val TIME_TO_MAX_PRESETS: Map<TimeToMaxPreset, Int> = mapOf(
        TimeToMaxPreset.Fast to 15,
        TimeToMaxPreset.Normal to 30,
        TimeToMaxPreset.Slow to 45,
    )

    // グレース期間プリセット
    enum class GracePreset { Short, Normal, Long }

    private val GRACE_PRESETS: Map<GracePreset, Long> = mapOf(
        GracePreset.Short to 60_000L,
        GracePreset.Normal to 300_000L,
        GracePreset.Long to 600_000L,
    )

    // 提案トリガ時間プリセット
    enum class SuggestionTriggerPreset(val seconds: Int) {
        Short(10 * 60),
        Normal(15 * 60),
        Long(30 * 60),
    }

    // 提案の「クールダウン」頻度プリセット
    // Infrequent: あまり出さない（クールダウン長い）
    // Normal:     ふつう
    // Frequent:   頻繁に出す（クールダウン短い）
    enum class SuggestionCooldownPreset(val seconds: Int) {
        Frequent(10 * 60),
        Normal(20 * 60),
        Infrequent(30 * 60),
    }

    // ----------------------------------------------------------
    // 3. OverlaySettings に対するプリセット適用 / 判定 helper
    // ----------------------------------------------------------

    fun OverlaySettings.withFontPreset(preset: FontPreset): OverlaySettings {
        val (min, max) = FONT_PRESETS[preset]!!
        return copy(
            minFontSizeSp = min,
            maxFontSizeSp = max,
        )
    }

    fun OverlaySettings.fontPresetOrNull(): FontPreset? {
        return FONT_PRESETS.entries.firstOrNull { (preset, range) ->
            minFontSizeSp == range.first && maxFontSizeSp == range.second
        }?.key
    }

    fun OverlaySettings.withTimeToMaxPreset(preset: TimeToMaxPreset): OverlaySettings {
        val minutes = TIME_TO_MAX_PRESETS[preset]!!
        return copy(timeToMaxMinutes = minutes)
    }

    fun OverlaySettings.timeToMaxPresetOrNull(): TimeToMaxPreset? {
        return TIME_TO_MAX_PRESETS.entries.firstOrNull { (_, minutes) ->
            timeToMaxMinutes == minutes
        }?.key
    }

    fun OverlaySettings.withGracePreset(preset: GracePreset): OverlaySettings {
        val millis = GRACE_PRESETS[preset]!!
        return copy(gracePeriodMillis = millis)
    }

    fun OverlaySettings.gracePresetOrNull(): GracePreset? {
        return GRACE_PRESETS.entries.firstOrNull { (_, millis) ->
            gracePeriodMillis == millis
        }?.key
    }

    fun OverlaySettings.withSuggestionTriggerPreset(preset: SuggestionTriggerPreset): OverlaySettings {
        return copy(suggestionTriggerSeconds = preset.seconds)
    }

    fun OverlaySettings.suggestionTriggerPresetOrNull(): SuggestionTriggerPreset? {
        return SuggestionTriggerPreset.entries.firstOrNull { preset ->
            preset.seconds == suggestionTriggerSeconds
        }
    }

    fun OverlaySettings.withSuggestionCooldownPreset(preset: SuggestionCooldownPreset): OverlaySettings {
        return copy(suggestionCooldownSeconds = preset.seconds)
    }

    fun OverlaySettings.suggestionCooldownPresetOrNull(): SuggestionCooldownPreset? {
        return SuggestionCooldownPreset.entries.firstOrNull { preset ->
            preset.seconds == suggestionCooldownSeconds
        }
    }
}
