package com.example.refocus.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
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

private const val DATASTORE_NAME = "refocus_settings"

private val Context.dataStore by preferencesDataStore(
    name = DATASTORE_NAME
)

class SettingsDataStore(
    private val context: Context
) {
    private object Keys {
        val GRACE_PERIOD_MS      = longPreferencesKey("grace_period_ms")
        val POLLING_INTERVAL_MS  = longPreferencesKey("polling_interval_ms")
        val MIN_FONT_SIZE_SP     = floatPreferencesKey("min_font_size_sp")
        val MAX_FONT_SIZE_SP     = floatPreferencesKey("max_font_size_sp")
        val TIME_TO_MAX_MINUTES  = intPreferencesKey("time_to_max_minutes")

        val POSITION_X          = intPreferencesKey("overlay_position_x")
        val POSITION_Y          = intPreferencesKey("overlay_position_y")
        val TOUCH_MODE          = intPreferencesKey("overlay_touch_mode")
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
                gracePeriodMillis = prefs[Keys.GRACE_PERIOD_MS] ?: 30_000L,
                pollingIntervalMillis = prefs[Keys.POLLING_INTERVAL_MS] ?: 500L,
                minFontSizeSp = prefs[Keys.MIN_FONT_SIZE_SP] ?: 20f,
                maxFontSizeSp = prefs[Keys.MAX_FONT_SIZE_SP] ?: 28f,
                timeToMaxMinutes = prefs[Keys.TIME_TO_MAX_MINUTES] ?: 30,
                positionX = prefs[Keys.POSITION_X] ?: 24,
                positionY = prefs[Keys.POSITION_Y] ?: 120,
                touchMode = touchMode
            )
        }

    // OverlaySettings を関数で変換して保存する汎用更新関数
    suspend fun update(
        transform: (OverlaySettings) -> OverlaySettings
    ) {
        context.dataStore.edit { prefs ->
            val current = OverlaySettings(
                gracePeriodMillis = prefs[Keys.GRACE_PERIOD_MS] ?: 30_000L,
                pollingIntervalMillis = prefs[Keys.POLLING_INTERVAL_MS] ?: 500L,
                minFontSizeSp = prefs[Keys.MIN_FONT_SIZE_SP] ?: 20f,
                maxFontSizeSp = prefs[Keys.MAX_FONT_SIZE_SP] ?: 28f,
                timeToMaxMinutes = prefs[Keys.TIME_TO_MAX_MINUTES] ?: 30,
                positionX = prefs[Keys.POSITION_X] ?: 24,
                positionY = prefs[Keys.POSITION_Y] ?: 120,
                touchMode = OverlayTouchMode.entries
                    .getOrNull(prefs[Keys.TOUCH_MODE] ?: 0)
                    ?: OverlayTouchMode.Drag
            )
            val updated = transform(current)

            prefs[Keys.GRACE_PERIOD_MS]     = updated.gracePeriodMillis
            prefs[Keys.POLLING_INTERVAL_MS] = updated.pollingIntervalMillis
            prefs[Keys.MIN_FONT_SIZE_SP]    = updated.minFontSizeSp
            prefs[Keys.MAX_FONT_SIZE_SP]    = updated.maxFontSizeSp
            prefs[Keys.TIME_TO_MAX_MINUTES] = updated.timeToMaxMinutes
            prefs[Keys.POSITION_X]          = updated.positionX
            prefs[Keys.POSITION_Y]          = updated.positionY
            prefs[Keys.TOUCH_MODE]          = updated.touchMode.ordinal
        }
    }
}
