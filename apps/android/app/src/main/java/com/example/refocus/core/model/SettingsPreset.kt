// app/src/main/java/com/example/refocus/core/model/SettingsPreset.kt
package com.example.refocus.core.model

/**
 * 設定全体のプリセット種別。
 *
 * - Default: 通常利用向けの標準値
 * - Debug:   動作確認・デバッグ用の値
 * - Custom:  プリセットから変更された状態
 */
enum class SettingsPreset {
    Default,
    Debug,
    Custom,
}

/**
 * 各プリセットに対応する OverlaySettings の値セット。
 *
 * ※ positionX / positionY / overlayEnabled / autoStartOnBoot など
 *   「その瞬間の状態」に近い値は applyPreset 側で上書きしないようにします。
 */
object SettingsPresets {
    // 標準プリセット（OverlaySettings のデフォルトと揃える）
    val default: OverlaySettings = OverlaySettings()

    // デバッグ用プリセット（値はあくまで例）
    val debug: OverlaySettings = OverlaySettings(
        gracePeriodMillis = 5_000L,       // 5秒でセッション途切れ判定
        pollingIntervalMillis = 250L,     // 250ms ごとに前面アプリチェック
        minFontSizeSp = 14f,
        maxFontSizeSp = 50f,
        timeToMaxMinutes = 5,             // 5分で最大サイズに到達

        // 起動系は preset ではなくユーザ選好なのでデフォルトのままにしておく
        overlayEnabled = true,
        autoStartOnBoot = true,

        // 位置・タッチモードも「その時の状態」なので後で上書きしない
        positionX = 24,
        positionY = 120,

        // 提案周りは「体感しやすい値」に寄せる
        suggestionEnabled = true,
        suggestionTriggerSeconds = 10,    // 10秒で提案発火
        suggestionTimeoutSeconds = 3,
        suggestionCooldownSeconds = 5,
        suggestionForegroundStableSeconds = 3,
        restSuggestionEnabled = true,
        suggestionInteractionLockoutMillis = 200L,
    )
}
