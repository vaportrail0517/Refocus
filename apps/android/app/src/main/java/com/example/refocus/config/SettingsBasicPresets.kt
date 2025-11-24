package com.example.refocus.config

import com.example.refocus.core.model.FontPreset
import com.example.refocus.core.model.GracePreset
import com.example.refocus.core.model.Settings
import com.example.refocus.core.model.SuggestionCooldownPreset
import com.example.refocus.core.model.SuggestionTriggerPreset
import com.example.refocus.core.model.TimeToMaxPreset

/**
 * enum → 具体的な値のマッピングと helper 関数。
 * 基本的なプリセット系のロジックはすべてここに集約する。
 */
object SettingsBasicPresets {
    private val fontPresetMap: Map<FontPreset, Pair<Float, Float>> = mapOf(
        FontPreset.Small to (10f to 30f),
        FontPreset.Medium to (12f to 40f),
        FontPreset.Large to (14f to 50f),
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
        TimeToMaxPreset.Fast to 15,
        TimeToMaxPreset.Normal to 30,
        TimeToMaxPreset.Slow to 45,
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

    private val suggestionCooldownPresetMap: Map<SuggestionCooldownPreset, Int> = mapOf(
        SuggestionCooldownPreset.Short to 10 * 60,
        SuggestionCooldownPreset.Normal to 20 * 60,
        SuggestionCooldownPreset.Long to 30 * 60,
    )

    fun Settings.withSuggestionCooldownPreset(preset: SuggestionCooldownPreset): Settings {
        val seconds = suggestionCooldownPresetMap[preset] ?: return this
        return copy(suggestionCooldownSeconds = seconds)
    }

    fun suggestionCooldownPresetOrNull(settings: Settings): SuggestionCooldownPreset? =
        suggestionCooldownPresetMap.entries.firstOrNull {
            it.value == settings.suggestionCooldownSeconds
        }?.key
}