package com.example.refocus.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.refocus.core.model.Customize
import com.example.refocus.core.model.CustomizePreset
import com.example.refocus.core.model.MiniGameKind
import com.example.refocus.core.model.MiniGameOrder
import com.example.refocus.core.model.TimerColorMode
import com.example.refocus.core.model.TimerGrowthMode
import com.example.refocus.core.model.TimerTimeMode
import com.example.refocus.core.model.TimerTouchMode
import com.example.refocus.core.model.TimerVisualTimeBasis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsDataStore by preferencesDataStore(name = "overlay_settings")

class SettingsDataStore(
    private val context: Context,
) {
    object Keys {
        // --- セッション・監視 ---
        val GRACE_PERIOD_MS = longPreferencesKey("grace_period_ms")
        val POLLING_INTERVAL_MS = longPreferencesKey("polling_interval_ms")

        // --- オーバーレイ見た目 ---
        val MIN_FONT_SIZE_SP = floatPreferencesKey("min_font_size_sp")
        val MAX_FONT_SIZE_SP = floatPreferencesKey("max_font_size_sp")
        val TIME_TO_MAX_SECONDS = intPreferencesKey("time_to_max_seconds")

        // 旧: 分単位（後方互換のため読み込みのみ）
        val TIME_TO_MAX_MINUTES = intPreferencesKey("time_to_max_minutes")
        val POSITION_X = intPreferencesKey("overlay_position_x")
        val POSITION_Y = intPreferencesKey("overlay_position_y")

        // enum: legacy ordinal
        val TOUCH_MODE = intPreferencesKey("overlay_touch_mode")
        val TIMER_TIME_MODE = intPreferencesKey("timer_time_mode")
        val TIMER_VISUAL_TIME_BASIS = intPreferencesKey("timer_visual_time_basis")
        val GROWTH_MODE = intPreferencesKey("growth_mode")
        val COLOR_MODE = intPreferencesKey("color_mode")
        val SETTINGS_PRESET = intPreferencesKey("settings_preset")

        // enum: new name
        val TOUCH_MODE_NAME = stringPreferencesKey("overlay_touch_mode_name")
        val TIMER_TIME_MODE_NAME = stringPreferencesKey("timer_time_mode_name")
        val TIMER_VISUAL_TIME_BASIS_NAME = stringPreferencesKey("timer_visual_time_basis_name")
        val GROWTH_MODE_NAME = stringPreferencesKey("growth_mode_name")
        val COLOR_MODE_NAME = stringPreferencesKey("color_mode_name")
        val SETTINGS_PRESET_NAME = stringPreferencesKey("settings_preset_name")

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


        // --- ミニゲーム（提案フローに挟むチャレンジ） ---
        val MINI_GAME_ENABLED = booleanPreferencesKey("mini_game_enabled")
        val MINI_GAME_ORDER_NAME = stringPreferencesKey("mini_game_order_name")
        val MINI_GAME_KIND_NAME = stringPreferencesKey("mini_game_kind_name")
    }

    val customizeFlow: Flow<Customize> =
        context.settingsDataStore.data
            .catch { e ->
                if (e is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw e
                }
            }.map { prefs ->
                prefs.toOverlaySettings()
            }

    /**
     * 現在のプリセット種別を流す Flow．
     * 保存されていない場合は Default 扱いとする．
     */
    val presetFlow: Flow<CustomizePreset> =
        context.settingsDataStore.data
            .catch { e ->
                if (e is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw e
                }
            }.map { prefs ->
                decodeEnum(
                    name = prefs[Keys.SETTINGS_PRESET_NAME],
                    ordinal = prefs[Keys.SETTINGS_PRESET],
                    entries = CustomizePreset.entries,
                    default = CustomizePreset.Default,
                    valueOf = { CustomizePreset.valueOf(it) },
                )
            }

    suspend fun update(transform: (Customize) -> Customize) {
        context.settingsDataStore.edit { prefs ->
            // まず現在値（保存値＋デフォルト）を Customize にデコード
            val current = prefs.toOverlaySettings()
            // ViewModel から渡された変換を当てる
            val updated = transform(current)

            // Customize -> Preferences へ書き戻す
            prefs[Keys.GRACE_PERIOD_MS] = updated.gracePeriodMillis
            prefs[Keys.POLLING_INTERVAL_MS] = updated.pollingIntervalMillis
            prefs[Keys.MIN_FONT_SIZE_SP] = updated.minFontSizeSp
            prefs[Keys.MAX_FONT_SIZE_SP] = updated.maxFontSizeSp
            prefs[Keys.TIME_TO_MAX_SECONDS] = updated.timeToMaxSeconds
            prefs.remove(Keys.TIME_TO_MAX_MINUTES)
            prefs[Keys.OVERLAY_ENABLED] = updated.overlayEnabled
            prefs[Keys.AUTO_START_ON_BOOT] = updated.autoStartOnBoot
            prefs[Keys.POSITION_X] = updated.positionX
            prefs[Keys.POSITION_Y] = updated.positionY

            // enum は name 保存へ移行
            prefs[Keys.TOUCH_MODE_NAME] = updated.touchMode.name
            prefs[Keys.TIMER_TIME_MODE_NAME] = updated.timerTimeMode.name
            prefs[Keys.TIMER_VISUAL_TIME_BASIS_NAME] = updated.timerVisualTimeBasis.name
            prefs[Keys.GROWTH_MODE_NAME] = updated.growthMode.name
            prefs[Keys.COLOR_MODE_NAME] = updated.colorMode.name

            // legacy ordinal は書き戻さず，更新時に削除して段階的に移行する
            prefs.remove(Keys.TOUCH_MODE)
            prefs.remove(Keys.TIMER_TIME_MODE)
            prefs.remove(Keys.TIMER_VISUAL_TIME_BASIS)
            prefs.remove(Keys.GROWTH_MODE)
            prefs.remove(Keys.COLOR_MODE)

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


            prefs[Keys.MINI_GAME_ENABLED] = updated.miniGameEnabled
            prefs[Keys.MINI_GAME_ORDER_NAME] = updated.miniGameOrder.name
            prefs[Keys.MINI_GAME_KIND_NAME] = updated.miniGameKind.name
        }
    }

    /**
     * プリセット種別だけを更新する．
     */
    suspend fun setPreset(preset: CustomizePreset) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.SETTINGS_PRESET_NAME] = preset.name
            prefs.remove(Keys.SETTINGS_PRESET)
        }
    }

    /**
     * Preferences -> Customize の変換を 1 箇所に集約．
     * fallback はすべて Customize() のデフォルトを見る．
     */
    private fun Preferences.toOverlaySettings(): Customize {
        // Customize() のデフォルトは core.model.CustomizeDefaults を参照している
        val base = Customize()

        val touchMode =
            decodeEnum(
                name = this[Keys.TOUCH_MODE_NAME],
                ordinal = this[Keys.TOUCH_MODE],
                entries = TimerTouchMode.entries,
                default = base.touchMode,
                valueOf = { TimerTouchMode.valueOf(it) },
            )

        val timerTimeMode =
            decodeEnum(
                name = this[Keys.TIMER_TIME_MODE_NAME],
                ordinal = this[Keys.TIMER_TIME_MODE],
                entries = TimerTimeMode.entries,
                default = base.timerTimeMode,
                valueOf = { TimerTimeMode.valueOf(it) },
            )

        val timerVisualTimeBasis =
            decodeEnum(
                name = this[Keys.TIMER_VISUAL_TIME_BASIS_NAME],
                ordinal = this[Keys.TIMER_VISUAL_TIME_BASIS],
                entries = TimerVisualTimeBasis.entries,
                default = base.timerVisualTimeBasis,
                valueOf = { TimerVisualTimeBasis.valueOf(it) },
            )

        val growthMode =
            decodeEnum(
                name = this[Keys.GROWTH_MODE_NAME],
                ordinal = this[Keys.GROWTH_MODE],
                entries = TimerGrowthMode.entries,
                default = base.growthMode,
                valueOf = { TimerGrowthMode.valueOf(it) },
            )

        val colorMode =
            decodeEnum(
                name = this[Keys.COLOR_MODE_NAME],
                ordinal = this[Keys.COLOR_MODE],
                entries = TimerColorMode.entries,
                default = base.colorMode,
                valueOf = { TimerColorMode.valueOf(it) },
            )

        val miniGameOrder =
            decodeEnum(
                name = this[Keys.MINI_GAME_ORDER_NAME],
                ordinal = null,
                entries = MiniGameOrder.entries,
                default = base.miniGameOrder,
                valueOf = { MiniGameOrder.valueOf(it) },
            )

        val miniGameKind =
            decodeEnum(
                name = this[Keys.MINI_GAME_KIND_NAME],
                ordinal = null,
                entries = MiniGameKind.entries,
                default = base.miniGameKind,
                valueOf = { MiniGameKind.valueOf(it) },
            )

        val timeToMaxSeconds =
            this[Keys.TIME_TO_MAX_SECONDS]
                ?: this[Keys.TIME_TO_MAX_MINUTES]?.let { it * 60 }
                ?: base.timeToMaxSeconds

        return base.copy(
            gracePeriodMillis =
                this[Keys.GRACE_PERIOD_MS]
                    ?: base.gracePeriodMillis,
            pollingIntervalMillis =
                this[Keys.POLLING_INTERVAL_MS]
                    ?: base.pollingIntervalMillis,
            minFontSizeSp =
                this[Keys.MIN_FONT_SIZE_SP]
                    ?: base.minFontSizeSp,
            maxFontSizeSp =
                this[Keys.MAX_FONT_SIZE_SP]
                    ?: base.maxFontSizeSp,
            timeToMaxSeconds = timeToMaxSeconds,
            overlayEnabled =
                this[Keys.OVERLAY_ENABLED]
                    ?: base.overlayEnabled,
            autoStartOnBoot =
                this[Keys.AUTO_START_ON_BOOT]
                    ?: base.autoStartOnBoot,
            positionX =
                this[Keys.POSITION_X]
                    ?: base.positionX,
            positionY =
                this[Keys.POSITION_Y]
                    ?: base.positionY,
            touchMode = touchMode,
            timerTimeMode = timerTimeMode,
            timerVisualTimeBasis = timerVisualTimeBasis,
            growthMode = growthMode,
            colorMode = colorMode,
            fixedColorArgb = this[Keys.FIXED_COLOR_ARGB] ?: base.fixedColorArgb,
            gradientStartColorArgb =
                this[Keys.GRADIENT_START_COLOR_ARGB]
                    ?: base.gradientStartColorArgb,
            gradientMiddleColorArgb =
                this[Keys.GRADIENT_MIDDLE_COLOR_ARGB]
                    ?: base.gradientMiddleColorArgb,
            gradientEndColorArgb =
                this[Keys.GRADIENT_END_COLOR_ARGB]
                    ?: base.gradientEndColorArgb,
            suggestionEnabled =
                this[Keys.SUGGESTION_ENABLED]
                    ?: base.suggestionEnabled,
            suggestionTriggerSeconds =
                this[Keys.SUGGESTION_TRIGGER_SECONDS]
                    ?: base.suggestionTriggerSeconds,
            suggestionTimeoutSeconds =
                this[Keys.SUGGESTION_TIMEOUT_SECONDS]
                    ?: base.suggestionTimeoutSeconds,
            suggestionCooldownSeconds =
                this[Keys.SUGGESTION_COOLDOWN_SECONDS]
                    ?: base.suggestionCooldownSeconds,
            suggestionForegroundStableSeconds =
                this[Keys.SUGGESTION_FOREGROUND_STABLE_SECONDS]
                    ?: base.suggestionForegroundStableSeconds,
            restSuggestionEnabled =
                this[Keys.REST_SUGGESTION_ENABLED]
                    ?: base.restSuggestionEnabled,
            suggestionInteractionLockoutMillis =
                this[Keys.SUGGESTION_INTERACTION_LOCKOUT_MS]
                    ?: base.suggestionInteractionLockoutMillis,
            miniGameEnabled = this[Keys.MINI_GAME_ENABLED] ?: base.miniGameEnabled,
            miniGameOrder = miniGameOrder,
            miniGameKind = miniGameKind,
        )
    }

    private fun <T> decodeEnum(
        name: String?,
        ordinal: Int?,
        entries: List<T>,
        default: T,
        valueOf: (String) -> T,
    ): T {
        if (!name.isNullOrBlank()) {
            runCatching { return valueOf(name) }
        }

        val idx = ordinal ?: return default
        return entries.getOrNull(idx) ?: default
    }
}
