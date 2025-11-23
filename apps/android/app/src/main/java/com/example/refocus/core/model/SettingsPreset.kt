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
 * 実際の数値は OverlaySettingsConfig に集約し、
 * ここは「種類 → 設定値」の入口だけを提供する。
 */
object SettingsPresets {

    // 標準プリセット
    val default: OverlaySettings = OverlaySettingsConfig.Default

    // デバッグ用プリセット
    val debug: OverlaySettings = OverlaySettingsConfig.Debug
}
