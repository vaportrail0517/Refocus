package com.example.refocus.config

import com.example.refocus.core.model.FontPreset
import com.example.refocus.core.model.GracePreset
import com.example.refocus.core.model.Settings
import com.example.refocus.core.model.SuggestionTriggerPreset
import com.example.refocus.core.model.TimeToMaxPreset

/**
 * enum → 具体的な値のマッピングと helper 関数。
 * 基本的なプリセット系のロジックはすべてここに集約する。
 */
object SettingsBasicPresets {
    private val fontPresetMap: Map<FontPreset, Pair<Float, Float>> = mapOf(
        FontPreset.Small to (12f to 48f),
        FontPreset.Medium to (16f to 64f),
        FontPreset.Large to (20f to 80f),
    )

    fun Settings.withFontPreset(preset: FontPreset): Settings {
        val (min, max) = fontPresetMap[preset] ?: return this
        return copy(
            minFontSizeSp = min,
            maxFontSizeSp = max,
        )
    }

    fun fontPresetOrNull(settings: Settings): FontPreset? =
        fontPresetMap.entries.firstOrNull {
            it.value.first == settings.minFontSizeSp &&
                    it.value.second == settings.maxFontSizeSp
        }?.key

    private val timeToMaxPresetMap: Map<TimeToMaxPreset, Int> = mapOf(
        TimeToMaxPreset.Fast to 10,
        TimeToMaxPreset.Normal to 15,
        TimeToMaxPreset.Slow to 30,
    )

    fun Settings.withTimeToMaxPreset(preset: TimeToMaxPreset): Settings {
        val minutes = timeToMaxPresetMap[preset] ?: return this
        return copy(timeToMaxMinutes = minutes)
    }

    fun timeToMaxPresetOrNull(settings: Settings): TimeToMaxPreset? =
        timeToMaxPresetMap.entries.firstOrNull {
            it.value == settings.timeToMaxMinutes
        }?.key

    private val gracePresetMap: Map<GracePreset, Long> = mapOf(
        GracePreset.Short to 60_000L,
        GracePreset.Normal to 300_000L,
        GracePreset.Long to 600_000L,
    )

    fun Settings.withGracePreset(preset: GracePreset): Settings {
        val millis = gracePresetMap[preset] ?: return this
        return copy(gracePeriodMillis = millis)
    }

    fun gracePresetOrNull(settings: Settings): GracePreset? =
        gracePresetMap.entries.firstOrNull {
            it.value == settings.gracePeriodMillis
        }?.key

    private val suggestionTriggerPresetMap: Map<SuggestionTriggerPreset, Int> = mapOf(
        SuggestionTriggerPreset.Short to 10 * 60,
        SuggestionTriggerPreset.Normal to 15 * 60,
        SuggestionTriggerPreset.Long to 30 * 60,
    )

    fun Settings.withSuggestionTriggerPreset(preset: SuggestionTriggerPreset): Settings {
        val seconds = suggestionTriggerPresetMap[preset] ?: return this
        return copy(suggestionTriggerSeconds = seconds)
    }

    fun suggestionTriggerPresetOrNull(settings: Settings): SuggestionTriggerPreset? =
        suggestionTriggerPresetMap.entries.firstOrNull {
            it.value == settings.suggestionTriggerSeconds
        }?.key
}