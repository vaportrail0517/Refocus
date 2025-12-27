package com.example.refocus.config

import com.example.refocus.core.model.Customize

/**
 * 「部分プリセット」の選択肢（UI都合のenum）。
 *
 * Customize 自体（永続化される値）とは分離し、enum→具体値の対応は config 側に閉じる。
 */
enum class FontPreset { Small, Medium, Large }
enum class TimeToMaxPreset { Slow, Normal, Fast }
enum class GracePreset { Short, Normal, Long }
enum class SuggestionTriggerPreset { Short, Normal, Long }
enum class SuggestionCooldownPreset { Short, Normal, Long }

/**
 * enum → 具体的な値のマッピングと helper 関数。
 * 基本的なプリセット系のロジックはすべてここに集約する。
 */
object CustomizeBasicPresets {
    private val fontPresetMap: Map<FontPreset, Pair<Float, Float>> = mapOf(
        FontPreset.Small to (12f to 48f),
        FontPreset.Medium to (16f to 64f),
        FontPreset.Large to (20f to 80f),
    )

    fun Customize.withFontPreset(preset: FontPreset): Customize {
        val (min, max) = fontPresetMap[preset] ?: return this
        return copy(
            minFontSizeSp = min,
            maxFontSizeSp = max,
        )
    }

    fun fontPresetOrNull(customize: Customize): FontPreset? =
        fontPresetMap.entries.firstOrNull {
            it.value.first == customize.minFontSizeSp &&
                    it.value.second == customize.maxFontSizeSp
        }?.key

    private val timeToMaxPresetMap: Map<TimeToMaxPreset, Int> = mapOf(
        TimeToMaxPreset.Fast to 10 * 60,
        TimeToMaxPreset.Normal to 15 * 60,
        TimeToMaxPreset.Slow to 30 * 60,
    )

    fun Customize.withTimeToMaxPreset(preset: TimeToMaxPreset): Customize {
        val seconds = timeToMaxPresetMap[preset] ?: return this
        return copy(timeToMaxSeconds = seconds)
    }

    fun timeToMaxPresetOrNull(customize: Customize): TimeToMaxPreset? =
        timeToMaxPresetMap.entries.firstOrNull {
            it.value == customize.timeToMaxSeconds
        }?.key

    private val gracePresetMap: Map<GracePreset, Long> = mapOf(
        GracePreset.Short to 60_000L,
        GracePreset.Normal to 300_000L,
        GracePreset.Long to 600_000L,
    )

    fun Customize.withGracePreset(preset: GracePreset): Customize {
        val millis = gracePresetMap[preset] ?: return this
        return copy(gracePeriodMillis = millis)
    }

    fun gracePresetOrNull(customize: Customize): GracePreset? =
        gracePresetMap.entries.firstOrNull {
            it.value == customize.gracePeriodMillis
        }?.key

    private val suggestionTriggerPresetMap: Map<SuggestionTriggerPreset, Int> = mapOf(
        SuggestionTriggerPreset.Short to 10 * 60,
        SuggestionTriggerPreset.Normal to 15 * 60,
        SuggestionTriggerPreset.Long to 30 * 60,
    )

    fun Customize.withSuggestionTriggerPreset(preset: SuggestionTriggerPreset): Customize {
        val seconds = suggestionTriggerPresetMap[preset] ?: return this
        return copy(suggestionTriggerSeconds = seconds)
    }

    fun suggestionTriggerPresetOrNull(customize: Customize): SuggestionTriggerPreset? =
        suggestionTriggerPresetMap.entries.firstOrNull {
            it.value == customize.suggestionTriggerSeconds
        }?.key
}