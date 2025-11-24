// app/src/main/java/com/example/refocus/core/model/SettingsPreset.kt
package com.example.refocus.core.model

/**
 * 設定全体のプリセット種別。
 *
 * - Default: 通常利用向けの標準値
 * - Debug:   動作確認・デバッグ用の値
 * - Custom:  プリセットから変更された状態
 */
enum class SettingsPreset { Default, Debug, Custom }

enum class FontPreset { Small, Medium, Large }

enum class TimeToMaxPreset { Slow, Normal, Fast }

enum class GracePreset { Short, Normal, Long }

enum class SuggestionTriggerPreset { Short, Normal, Long }

enum class SuggestionCooldownPreset { Short, Normal, Long }
