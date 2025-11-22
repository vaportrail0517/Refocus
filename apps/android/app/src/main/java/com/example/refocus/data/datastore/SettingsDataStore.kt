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
import com.example.refocus.core.model.OverlaySettings
import com.example.refocus.core.model.OverlayTouchMode
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

        // --- 起動・有効/無効 ---
        val OVERLAY_ENABLED = booleanPreferencesKey("overlay_enabled")
        val AUTO_START_ON_BOOT = booleanPreferencesKey("auto_start_on_boot")

        // --- 提案機能 ---
        val SUGGESTION_ENABLED = booleanPreferencesKey("suggestion_enabled")
        val SUGGESTION_TRIGGER_SECONDS = intPreferencesKey("suggestion_trigger_seconds")
        val SUGGESTION_TIMEOUT_SECONDS = intPreferencesKey("suggestion_timeout_seconds")
        val SUGGESTION_SNOOZE_SECONDS = intPreferencesKey("suggestion_snooze_seconds")
        val SUGGESTION_FOREGROUND_STABLE_SECONDS =
            intPreferencesKey("suggestion_foreground_stable_seconds")
        val REST_SUGGESTION_ENABLED = booleanPreferencesKey("rest_suggestion_enabled")
        val SUGGESTION_INTERACTION_LOCKOUT_MS =
            longPreferencesKey("suggestion_interaction_lockout_ms")
    }

    val settingsFlow: Flow<OverlaySettings> =
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

    suspend fun update(
        transform: (OverlaySettings) -> OverlaySettings
    ) {
        context.settingsDataStore.edit { prefs ->
            // まず現在値（保存値＋デフォルト）を OverlaySettings にデコード
            val current = prefs.toOverlaySettings()
            // ViewModel から渡された変換を当てる
            val updated = transform(current)

            // OverlaySettings -> Preferences へ書き戻す
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

            prefs[Keys.SUGGESTION_ENABLED] = updated.suggestionEnabled
            prefs[Keys.SUGGESTION_TRIGGER_SECONDS] = updated.suggestionTriggerSeconds
            prefs[Keys.SUGGESTION_TIMEOUT_SECONDS] = updated.suggestionTimeoutSeconds
            prefs[Keys.SUGGESTION_SNOOZE_SECONDS] = updated.suggestionCooldownSeconds
            prefs[Keys.SUGGESTION_FOREGROUND_STABLE_SECONDS] =
                updated.suggestionForegroundStableSeconds
            prefs[Keys.REST_SUGGESTION_ENABLED] = updated.restSuggestionEnabled
            prefs[Keys.SUGGESTION_INTERACTION_LOCKOUT_MS] =
                updated.suggestionInteractionLockoutMillis
        }
    }

    /**
     * Preferences -> OverlaySettings の変換を 1 箇所に集約。
     * fallback はすべて OverlaySettings のデフォルトを見る。
     */
    private fun Preferences.toOverlaySettings(): OverlaySettings {
        val touchModeOrdinal = this[Keys.TOUCH_MODE] ?: 0
        val touchMode = OverlayTouchMode.entries.getOrNull(touchModeOrdinal)
            ?: OverlayTouchMode.Drag

        return OverlaySettings(
            gracePeriodMillis = this[Keys.GRACE_PERIOD_MS]
                ?: OverlaySettings.DEFAULT_GRACE_PERIOD_MILLIS,
            pollingIntervalMillis = this[Keys.POLLING_INTERVAL_MS]
                ?: OverlaySettings.DEFAULT_POLLING_INTERVAL_MILLIS,
            minFontSizeSp = this[Keys.MIN_FONT_SIZE_SP]
                ?: OverlaySettings.DEFAULT_MIN_FONT_SIZE_SP,
            maxFontSizeSp = this[Keys.MAX_FONT_SIZE_SP]
                ?: OverlaySettings.DEFAULT_MAX_FONT_SIZE_SP,
            timeToMaxMinutes = this[Keys.TIME_TO_MAX_MINUTES]
                ?: OverlaySettings.DEFAULT_TIME_TO_MAX_MINUTES,
            overlayEnabled = this[Keys.OVERLAY_ENABLED]
                ?: OverlaySettings.DEFAULT_OVERLAY_ENABLED,
            autoStartOnBoot = this[Keys.AUTO_START_ON_BOOT]
                ?: OverlaySettings.DEFAULT_AUTO_START_ON_BOOT,
            positionX = this[Keys.POSITION_X]
                ?: OverlaySettings.DEFAULT_POSITION_X,
            positionY = this[Keys.POSITION_Y]
                ?: OverlaySettings.DEFAULT_POSITION_Y,
            touchMode = touchMode,
            suggestionEnabled = this[Keys.SUGGESTION_ENABLED]
                ?: OverlaySettings.DEFAULT_SUGGESTION_ENABLED,
            suggestionTriggerSeconds = this[Keys.SUGGESTION_TRIGGER_SECONDS]
                ?: OverlaySettings.DEFAULT_SUGGESTION_TRIGGER_SECONDS,
            suggestionTimeoutSeconds = this[Keys.SUGGESTION_TIMEOUT_SECONDS]
                ?: OverlaySettings.DEFAULT_SUGGESTION_TIMEOUT_SECONDS,
            suggestionCooldownSeconds = this[Keys.SUGGESTION_SNOOZE_SECONDS]
                ?: OverlaySettings.DEFAULT_SUGGESTION_COOLDOWN_SECONDS,
            suggestionForegroundStableSeconds =
                this[Keys.SUGGESTION_FOREGROUND_STABLE_SECONDS]
                    ?: OverlaySettings.DEFAULT_SUGGESTION_FOREGROUND_STABLE_SECONDS,
            restSuggestionEnabled = this[Keys.REST_SUGGESTION_ENABLED]
                ?: OverlaySettings.DEFAULT_REST_SUGGESTION_ENABLED,
            suggestionInteractionLockoutMillis =
                this[Keys.SUGGESTION_INTERACTION_LOCKOUT_MS]
                    ?: OverlaySettings.DEFAULT_SUGGESTION_INTERACTION_LOCKOUT_MS,
        )
    }
}