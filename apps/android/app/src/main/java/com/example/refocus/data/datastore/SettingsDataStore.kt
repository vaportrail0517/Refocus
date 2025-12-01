package com.example.refocus.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.refocus.core.model.OverlayColorMode
import com.example.refocus.core.model.OverlayGrowthMode
import com.example.refocus.core.model.OverlayTouchMode
import com.example.refocus.core.model.Settings
import com.example.refocus.core.model.SettingsPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsDataStore by preferencesDataStore(name = "overlay_settings")

class SettingsDataStore(
    private val context: Context
) {
    object Keys {
        // --- セッション・監視 ---
        val GRACE_PERIOD_MS = longPreferencesKey("grace_period_ms")
        val POLLING_INTERVAL_MS = longPreferencesKey("polling_interval_ms")

        // --- オーバーレイ見た目 ---
        val MIN_FONT_SIZE_SP = floatPreferencesKey("min_font_size_sp")
        val MAX_FONT_SIZE_SP = floatPreferencesKey("max_font_size_sp")
        val TIME_TO_MAX_MINUTES = intPreferencesKey("time_to_max_minutes")
        val POSITION_X = intPreferencesKey("overlay_position_x")
        val POSITION_Y = intPreferencesKey("overlay_position_y")
        val TOUCH_MODE = intPreferencesKey("overlay_touch_mode")

        val GROWTH_MODE = intPreferencesKey("growth_mode")
        val COLOR_MODE = intPreferencesKey("color_mode")
        val FIXED_COLOR_ARGB = intPreferencesKey("fixed_color_argb")
        val GRADIENT_START_COLOR_ARGB = intPreferencesKey("gradient_start_color_argb")
        val GRADIENT_MIDDLE_COLOR_ARGB = intPreferencesKey("gradient_middle_color_argb")
        val GRADIENT_END_COLOR_ARGB = intPreferencesKey("gradient_end_color_argb")

        // --- 起動・有効/無効 ---
        val OVERLAY_ENABLED = booleanPreferencesKey("overlay_enabled")
        val AUTO_START_ON_BOOT = booleanPreferencesKey("auto_start_on_boot")

        // --- 提案機能 ---
        val SUGGESTION_ENABLED = booleanPreferencesKey("suggestion_enabled")
        val SUGGESTION_TRIGGER_SECONDS = intPreferencesKey("suggestion_trigger_seconds")
        val SUGGESTION_TIMEOUT_SECONDS = intPreferencesKey("suggestion_timeout_seconds")
        val SUGGESTION_COOLDOWN_SECONDS = intPreferencesKey("suggestion_cooldown_seconds")
        val SUGGESTION_FOREGROUND_STABLE_SECONDS =
            intPreferencesKey("suggestion_foreground_stable_seconds")
        val REST_SUGGESTION_ENABLED = booleanPreferencesKey("rest_suggestion_enabled")
        val SUGGESTION_INTERACTION_LOCKOUT_MS =
            longPreferencesKey("suggestion_interaction_lockout_ms")

        // --- 設定全体のプリセット種別 ---
        val SETTINGS_PRESET = intPreferencesKey("settings_preset")
    }

    val settingsFlow: Flow<Settings> =
        context.settingsDataStore.data
            .catch { e ->
                if (e is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw e
                }
            }
            .map { prefs ->
                prefs.toOverlaySettings()
            }

    /**
     * 現在のプリセット種別を流す Flow。
     * 保存されていない場合は Default 扱いとする。
     */
    val presetFlow: Flow<SettingsPreset> = context.settingsDataStore.data
        .catch { e ->
            if (e is IOException) {
                emit(emptyPreferences())
            } else {
                throw e
            }
        }
        .map { prefs ->
            val ordinal = prefs[Keys.SETTINGS_PRESET] ?: SettingsPreset.Default.ordinal
            SettingsPreset.entries.getOrNull(ordinal) ?: SettingsPreset.Default
        }

    suspend fun update(
        transform: (Settings) -> Settings
    ) {
        context.settingsDataStore.edit { prefs ->
            // まず現在値（保存値＋デフォルト）を Settings にデコード
            val current = prefs.toOverlaySettings()
            // ViewModel から渡された変換を当てる
            val updated = transform(current)

            // Settings -> Preferences へ書き戻す
            prefs[Keys.GRACE_PERIOD_MS] = updated.gracePeriodMillis
            prefs[Keys.POLLING_INTERVAL_MS] = updated.pollingIntervalMillis
            prefs[Keys.MIN_FONT_SIZE_SP] = updated.minFontSizeSp
            prefs[Keys.MAX_FONT_SIZE_SP] = updated.maxFontSizeSp
            prefs[Keys.TIME_TO_MAX_MINUTES] = updated.timeToMaxMinutes
            prefs[Keys.OVERLAY_ENABLED] = updated.overlayEnabled
            prefs[Keys.AUTO_START_ON_BOOT] = updated.autoStartOnBoot
            prefs[Keys.POSITION_X] = updated.positionX
            prefs[Keys.POSITION_Y] = updated.positionY
            prefs[Keys.TOUCH_MODE] = updated.touchMode.ordinal

            prefs[Keys.GROWTH_MODE] = updated.growthMode.ordinal
            prefs[Keys.COLOR_MODE] = updated.colorMode.ordinal
            prefs[Keys.FIXED_COLOR_ARGB] = updated.fixedColorArgb
            prefs[Keys.GRADIENT_START_COLOR_ARGB] = updated.gradientStartColorArgb
            prefs[Keys.GRADIENT_MIDDLE_COLOR_ARGB] = updated.gradientMiddleColorArgb
            prefs[Keys.GRADIENT_END_COLOR_ARGB] = updated.gradientEndColorArgb

            prefs[Keys.SUGGESTION_ENABLED] = updated.suggestionEnabled
            prefs[Keys.SUGGESTION_TRIGGER_SECONDS] = updated.suggestionTriggerSeconds
            prefs[Keys.SUGGESTION_TIMEOUT_SECONDS] = updated.suggestionTimeoutSeconds
            prefs[Keys.SUGGESTION_COOLDOWN_SECONDS] = updated.suggestionCooldownSeconds
            prefs[Keys.SUGGESTION_FOREGROUND_STABLE_SECONDS] =
                updated.suggestionForegroundStableSeconds
            prefs[Keys.REST_SUGGESTION_ENABLED] = updated.restSuggestionEnabled
            prefs[Keys.SUGGESTION_INTERACTION_LOCKOUT_MS] =
                updated.suggestionInteractionLockoutMillis
        }
    }

    /**
     * プリセット種別だけを更新する。
     */
    suspend fun setPreset(preset: SettingsPreset) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.SETTINGS_PRESET] = preset.ordinal
        }
    }

    /**
     * Preferences -> Settings の変換を 1 箇所に集約。
     * fallback はすべて Settings() のデフォルトを見る。
     */
    private fun Preferences.toOverlaySettings(): Settings {
        // Settings() のデフォルトは SettingsConfig.SettingsDefaults を参照している
        val base = Settings()

        val touchModeOrdinal = this[Keys.TOUCH_MODE] ?: base.touchMode.ordinal
        val touchMode = OverlayTouchMode.entries.getOrNull(touchModeOrdinal)
            ?: OverlayTouchMode.Drag

        return base.copy(
            gracePeriodMillis = this[Keys.GRACE_PERIOD_MS]
                ?: base.gracePeriodMillis,
            pollingIntervalMillis = this[Keys.POLLING_INTERVAL_MS]
                ?: base.pollingIntervalMillis,
            minFontSizeSp = this[Keys.MIN_FONT_SIZE_SP]
                ?: base.minFontSizeSp,
            maxFontSizeSp = this[Keys.MAX_FONT_SIZE_SP]
                ?: base.maxFontSizeSp,
            timeToMaxMinutes = this[Keys.TIME_TO_MAX_MINUTES]
                ?: base.timeToMaxMinutes,
            overlayEnabled = this[Keys.OVERLAY_ENABLED]
                ?: base.overlayEnabled,
            autoStartOnBoot = this[Keys.AUTO_START_ON_BOOT]
                ?: base.autoStartOnBoot,
            positionX = this[Keys.POSITION_X]
                ?: base.positionX,
            positionY = this[Keys.POSITION_Y]
                ?: base.positionY,
            touchMode = touchMode,
            growthMode = this[Keys.GROWTH_MODE]
                ?.let { OverlayGrowthMode.values().getOrNull(it) }
                ?: base.growthMode,
            colorMode = this[Keys.COLOR_MODE]
                ?.let { OverlayColorMode.values().getOrNull(it) }
                ?: base.colorMode,
            fixedColorArgb = this[Keys.FIXED_COLOR_ARGB] ?: base.fixedColorArgb,
            gradientStartColorArgb = this[Keys.GRADIENT_START_COLOR_ARGB]
                ?: base.gradientStartColorArgb,
            gradientMiddleColorArgb = this[Keys.GRADIENT_MIDDLE_COLOR_ARGB]
                ?: base.gradientMiddleColorArgb,
            gradientEndColorArgb = this[Keys.GRADIENT_END_COLOR_ARGB] ?: base.gradientEndColorArgb,
            suggestionEnabled = this[Keys.SUGGESTION_ENABLED]
                ?: base.suggestionEnabled,
            suggestionTriggerSeconds = this[Keys.SUGGESTION_TRIGGER_SECONDS]
                ?: base.suggestionTriggerSeconds,
            suggestionTimeoutSeconds = this[Keys.SUGGESTION_TIMEOUT_SECONDS]
                ?: base.suggestionTimeoutSeconds,
            suggestionCooldownSeconds = this[Keys.SUGGESTION_COOLDOWN_SECONDS]
                ?: base.suggestionCooldownSeconds,
            suggestionForegroundStableSeconds =
                this[Keys.SUGGESTION_FOREGROUND_STABLE_SECONDS]
                    ?: base.suggestionForegroundStableSeconds,
            restSuggestionEnabled = this[Keys.REST_SUGGESTION_ENABLED]
                ?: base.restSuggestionEnabled,
            suggestionInteractionLockoutMillis =
                this[Keys.SUGGESTION_INTERACTION_LOCKOUT_MS]
                    ?: base.suggestionInteractionLockoutMillis,
        )
    }
}
