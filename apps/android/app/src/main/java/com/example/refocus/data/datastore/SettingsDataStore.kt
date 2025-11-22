package com.example.refocus.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.example.refocus.core.model.OverlaySettings
import com.example.refocus.core.model.OverlayTouchMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private const val DATASTORE_NAME = "refocus_settings"

private val Context.dataStore by preferencesDataStore(
    name = DATASTORE_NAME
)

class SettingsDataStore(
    private val context: Context
) {
    private object Keys {
        val GRACE_PERIOD_MS = longPreferencesKey("grace_period_ms")
        val POLLING_INTERVAL_MS = longPreferencesKey("polling_interval_ms")
        val MIN_FONT_SIZE_SP = floatPreferencesKey("min_font_size_sp")
        val MAX_FONT_SIZE_SP = floatPreferencesKey("max_font_size_sp")
        val TIME_TO_MAX_MINUTES = intPreferencesKey("time_to_max_minutes")

        val OVERLAY_ENABLED = booleanPreferencesKey("overlay_enabled")
        val AUTO_START_ON_BOOT = booleanPreferencesKey("auto_start_on_boot")

        val POSITION_X = intPreferencesKey("overlay_position_x")
        val POSITION_Y = intPreferencesKey("overlay_position_y")
        val TOUCH_MODE = intPreferencesKey("overlay_touch_mode")

        val SUGGESTION_ENABLED        = booleanPreferencesKey("suggestion_enabled")
        val SUGGESTION_TRIGGER_SECONDS = intPreferencesKey("suggestion_trigger_seconds")
        val SUGGESTION_TIMEOUT_SECONDS = intPreferencesKey("suggestion_timeout_seconds")
        // 既存キー名はそのまま使いつつ、意味としては「クールダウン秒数」
        val SUGGESTION_SNOOZE_SECONDS  = intPreferencesKey("suggestion_snooze_seconds")
        val SUGGESTION_FOREGROUND_STABLE_SECONDS =
            intPreferencesKey("suggestion_foreground_stable_seconds")
        val REST_SUGGESTION_ENABLED    = booleanPreferencesKey("rest_suggestion_enabled")
        val SUGGESTION_INTERACTION_LOCKOUT_MS =
            longPreferencesKey("suggestion_interaction_lockout_ms")
    }

    // 現在の設定を OverlaySettings にマッピングして流す Flow
    val settingsFlow: Flow<OverlaySettings> = context.dataStore.data
        .catch { e ->
            if (e is IOException) {
                // I/O エラー時は空プリファレンスにフォールバック
                emit(emptyPreferences())
            } else {
                throw e
            }
        }
        .map { prefs: Preferences ->
            val touchModeOrdinal = prefs[Keys.TOUCH_MODE] ?: 0
            val touchMode = OverlayTouchMode.entries.getOrNull(touchModeOrdinal)
                ?: OverlayTouchMode.Drag
            OverlaySettings(
                gracePeriodMillis = prefs[Keys.GRACE_PERIOD_MS] ?: 300_000L,
                pollingIntervalMillis = prefs[Keys.POLLING_INTERVAL_MS] ?: 500L,
                minFontSizeSp = prefs[Keys.MIN_FONT_SIZE_SP] ?: 12f,
                maxFontSizeSp = prefs[Keys.MAX_FONT_SIZE_SP] ?: 40f,
                timeToMaxMinutes = prefs[Keys.TIME_TO_MAX_MINUTES] ?: 30,
                overlayEnabled      = prefs[Keys.OVERLAY_ENABLED] ?: true,
                autoStartOnBoot     = prefs[Keys.AUTO_START_ON_BOOT] ?: true,
                positionX = prefs[Keys.POSITION_X] ?: 24,
                positionY = prefs[Keys.POSITION_Y] ?: 120,
                touchMode = touchMode,
                suggestionEnabled = prefs[Keys.SUGGESTION_ENABLED] ?: true,
                suggestionTriggerSeconds = prefs[Keys.SUGGESTION_TRIGGER_SECONDS] ?: 30,
                suggestionTimeoutSeconds = prefs[Keys.SUGGESTION_TIMEOUT_SECONDS] ?: 10,
                suggestionCooldownSeconds = prefs[Keys.SUGGESTION_SNOOZE_SECONDS] ?: 30,
                suggestionForegroundStableSeconds =
                    prefs[Keys.SUGGESTION_FOREGROUND_STABLE_SECONDS] ?: 20,
                restSuggestionEnabled = prefs[Keys.REST_SUGGESTION_ENABLED] ?: true,
                suggestionInteractionLockoutMillis =
                    prefs[Keys.SUGGESTION_INTERACTION_LOCKOUT_MS] ?: 400L,
            )
        }

    // OverlaySettings を関数で変換して保存する汎用更新関数
    suspend fun update(
        transform: (OverlaySettings) -> OverlaySettings
    ) {
        context.dataStore.edit { prefs ->
            val current = OverlaySettings(
                gracePeriodMillis = prefs[Keys.GRACE_PERIOD_MS] ?: 300_000L,
                pollingIntervalMillis = prefs[Keys.POLLING_INTERVAL_MS] ?: 500L,
                minFontSizeSp = prefs[Keys.MIN_FONT_SIZE_SP] ?: 12f,
                maxFontSizeSp = prefs[Keys.MAX_FONT_SIZE_SP] ?: 40f,
                timeToMaxMinutes = prefs[Keys.TIME_TO_MAX_MINUTES] ?: 30,
                overlayEnabled      = prefs[Keys.OVERLAY_ENABLED] ?: true,
                autoStartOnBoot     = prefs[Keys.AUTO_START_ON_BOOT] ?: true,
                positionX = prefs[Keys.POSITION_X] ?: 24,
                positionY = prefs[Keys.POSITION_Y] ?: 120,
                touchMode = OverlayTouchMode.entries
                    .getOrNull(prefs[Keys.TOUCH_MODE] ?: 0)
                    ?: OverlayTouchMode.Drag,
                suggestionEnabled = prefs[Keys.SUGGESTION_ENABLED] ?: true,
                suggestionTriggerSeconds = prefs[Keys.SUGGESTION_TRIGGER_SECONDS] ?: 30,
                suggestionTimeoutSeconds = prefs[Keys.SUGGESTION_TIMEOUT_SECONDS] ?: 10,
                suggestionCooldownSeconds = prefs[Keys.SUGGESTION_SNOOZE_SECONDS] ?: 30,
                suggestionForegroundStableSeconds =
                    prefs[Keys.SUGGESTION_FOREGROUND_STABLE_SECONDS] ?: 20,
                restSuggestionEnabled = prefs[Keys.REST_SUGGESTION_ENABLED] ?: true,
                suggestionInteractionLockoutMillis =
                    prefs[Keys.SUGGESTION_INTERACTION_LOCKOUT_MS] ?: 400L,
            )
            val updated = transform(current)

            prefs[Keys.GRACE_PERIOD_MS]     = updated.gracePeriodMillis
            prefs[Keys.POLLING_INTERVAL_MS] = updated.pollingIntervalMillis
            prefs[Keys.MIN_FONT_SIZE_SP]    = updated.minFontSizeSp
            prefs[Keys.MAX_FONT_SIZE_SP]    = updated.maxFontSizeSp
            prefs[Keys.TIME_TO_MAX_MINUTES] = updated.timeToMaxMinutes
            prefs[Keys.OVERLAY_ENABLED]      = updated.overlayEnabled
            prefs[Keys.AUTO_START_ON_BOOT]   = updated.autoStartOnBoot
            prefs[Keys.POSITION_X]          = updated.positionX
            prefs[Keys.POSITION_Y]          = updated.positionY
            prefs[Keys.TOUCH_MODE]          = updated.touchMode.ordinal
            prefs[Keys.SUGGESTION_ENABLED]        = updated.suggestionEnabled
            prefs[Keys.SUGGESTION_TRIGGER_SECONDS] = updated.suggestionTriggerSeconds
            prefs[Keys.SUGGESTION_TIMEOUT_SECONDS] = updated.suggestionTimeoutSeconds
            prefs[Keys.SUGGESTION_SNOOZE_SECONDS]  = updated.suggestionCooldownSeconds
            prefs[Keys.SUGGESTION_FOREGROUND_STABLE_SECONDS] = updated.suggestionForegroundStableSeconds
            prefs[Keys.REST_SUGGESTION_ENABLED]    = updated.restSuggestionEnabled
            prefs[Keys.SUGGESTION_INTERACTION_LOCKOUT_MS] = updated.suggestionInteractionLockoutMillis
        }
    }
}
