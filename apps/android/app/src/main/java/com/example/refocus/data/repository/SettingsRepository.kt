package com.example.refocus.data.repository

import com.example.refocus.core.model.Settings
import com.example.refocus.core.model.SettingsPreset
import com.example.refocus.core.model.SettingsPresets
import com.example.refocus.data.datastore.SettingsDataStore
import kotlinx.coroutines.flow.Flow

class SettingsRepository(
    private val dataStore: SettingsDataStore
) {
    fun observeOverlaySettings(): Flow<Settings> = dataStore.settingsFlow
    fun observeSettingsPreset(): Flow<SettingsPreset> = dataStore.presetFlow
    suspend fun updateOverlaySettings(
        transform: (Settings) -> Settings
    ) {
        dataStore.update(transform)
    }

    suspend fun setSettingsPreset(preset: SettingsPreset) {
        dataStore.setPreset(preset)
    }

    /**
     * プリセットを適用する。
     *
     * - Default / Debug: Settings の値をプリセット値で上書き
     * - Custom: 値は変更せず、種別だけ変更
     *
     * 位置や overlayEnabled / autoStartOnBoot など
     * 「その瞬間の状態」に近い値は、既存値を維持するようにしている。
     */
    suspend fun applyPreset(preset: SettingsPreset) {
        when (preset) {
            SettingsPreset.Default -> {
                dataStore.update { current ->
                    SettingsPresets.default.copy(
                        // 「状態」に近い値は引き継ぐ
                        overlayEnabled = current.overlayEnabled,
                        autoStartOnBoot = current.autoStartOnBoot,
                        positionX = current.positionX,
                        positionY = current.positionY,
                        touchMode = current.touchMode,
                    )
                }
                dataStore.setPreset(SettingsPreset.Default)
            }

            SettingsPreset.Debug -> {
                dataStore.update { current ->
                    SettingsPresets.debug.copy(
                        overlayEnabled = current.overlayEnabled,
                        autoStartOnBoot = current.autoStartOnBoot,
                        positionX = current.positionX,
                        positionY = current.positionY,
                        touchMode = current.touchMode,
                    )
                }
                dataStore.setPreset(SettingsPreset.Debug)
            }

            SettingsPreset.Custom -> {
                // 値はそのまま・種別だけ Custom に。
                dataStore.setPreset(SettingsPreset.Custom)
            }
        }
    }

    suspend fun setOverlayEnabled(enabled: Boolean) {
        updateOverlaySettings { it.copy(overlayEnabled = enabled) }
    }

    suspend fun setAutoStartOnBoot(enabled: Boolean) {
        updateOverlaySettings { it.copy(autoStartOnBoot = enabled) }
    }

    suspend fun setSuggestionEnabled(enabled: Boolean) {
        updateOverlaySettings { it.copy(suggestionEnabled = enabled) }
    }

    suspend fun setSuggestionTriggerSeconds(seconds: Int) {
        updateOverlaySettings { it.copy(suggestionTriggerSeconds = seconds) }
    }

    suspend fun setSuggestionTimeoutSeconds(seconds: Int) {
        updateOverlaySettings { it.copy(suggestionTimeoutSeconds = seconds) }
    }

    suspend fun setSuggestionCooldownSeconds(seconds: Int) {
        updateOverlaySettings { it.copy(suggestionCooldownSeconds = seconds) }
    }

    suspend fun setSuggestionForegroundStableSeconds(seconds: Int) {
        updateOverlaySettings {
            it.copy(suggestionForegroundStableSeconds = seconds.coerceAtLeast(0))
        }
    }

    suspend fun setRestSuggestionEnabled(enabled: Boolean) {
        updateOverlaySettings {
            it.copy(restSuggestionEnabled = enabled)
        }
    }
}
