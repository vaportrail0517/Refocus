package com.example.refocus.data.repository

import com.example.refocus.config.CustomizePresetValues
import com.example.refocus.core.model.Customize
import com.example.refocus.core.model.CustomizePreset
import com.example.refocus.data.datastore.SettingsDataStore
import com.example.refocus.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow

class SettingsRepositoryImpl(
    private val dataStore: SettingsDataStore
) : SettingsRepository {
    override fun observeOverlaySettings(): Flow<Customize> = dataStore.customizeFlow
    override fun observeSettingsPreset(): Flow<CustomizePreset> = dataStore.presetFlow
    override suspend fun updateOverlaySettings(
        transform: (Customize) -> Customize
    ) {
        dataStore.update(transform)
    }

    override suspend fun setSettingsPreset(preset: CustomizePreset) {
        dataStore.setPreset(preset)
    }

    /**
     * プリセットを適用する。
     *
     * - Default / Debug: Customize の値をプリセット値で上書き
     * - Custom: 値は変更せず、種別だけ変更
     *
     * 位置や overlayEnabled / autoStartOnBoot など
     * 「その瞬間の状態」に近い値は、既存値を維持するようにしている。
     */
    override suspend fun applyPreset(preset: CustomizePreset) {
        when (preset) {
            CustomizePreset.Default -> {
                dataStore.update { current ->
                    CustomizePresetValues.Default.copy(
                        // 「状態」に近い値は引き継ぐ
                        overlayEnabled = current.overlayEnabled,
                        autoStartOnBoot = current.autoStartOnBoot,
                        positionX = current.positionX,
                        positionY = current.positionY,
                        touchMode = current.touchMode,
                        timerTimeMode = current.timerTimeMode,
                        timerVisualTimeBasis = current.timerVisualTimeBasis,
                    )
                }
                dataStore.setPreset(CustomizePreset.Default)
            }

            CustomizePreset.Debug -> {
                dataStore.update { current ->
                    CustomizePresetValues.Debug.copy(
                        overlayEnabled = current.overlayEnabled,
                        autoStartOnBoot = current.autoStartOnBoot,
                        positionX = current.positionX,
                        positionY = current.positionY,
                        touchMode = current.touchMode,
                        timerTimeMode = current.timerTimeMode,
                        timerVisualTimeBasis = current.timerVisualTimeBasis,
                    )
                }
                dataStore.setPreset(CustomizePreset.Debug)
            }

            CustomizePreset.Custom -> {
                // 値はそのまま・種別だけ Custom に。
                dataStore.setPreset(CustomizePreset.Custom)
            }
        }
    }

    override suspend fun setOverlayEnabled(enabled: Boolean) {
        updateOverlaySettings { it.copy(overlayEnabled = enabled) }
    }

    override suspend fun setAutoStartOnBoot(enabled: Boolean) {
        updateOverlaySettings { it.copy(autoStartOnBoot = enabled) }
    }

    override suspend fun setSuggestionEnabled(enabled: Boolean) {
        updateOverlaySettings { it.copy(suggestionEnabled = enabled) }
    }

    override suspend fun setSuggestionTriggerSeconds(seconds: Int) {
        updateOverlaySettings { it.copy(suggestionTriggerSeconds = seconds) }
    }

    override suspend fun setSuggestionTimeoutSeconds(seconds: Int) {
        updateOverlaySettings { it.copy(suggestionTimeoutSeconds = seconds) }
    }

    override suspend fun setSuggestionCooldownSeconds(seconds: Int) {
        updateOverlaySettings { it.copy(suggestionCooldownSeconds = seconds) }
    }

    override suspend fun setSuggestionForegroundStableSeconds(seconds: Int) {
        updateOverlaySettings {
            it.copy(suggestionForegroundStableSeconds = seconds.coerceAtLeast(0))
        }
    }

    override suspend fun setRestSuggestionEnabled(enabled: Boolean) {
        updateOverlaySettings {
            it.copy(restSuggestionEnabled = enabled)
        }
    }

    override suspend fun resetToDefaults() {
        dataStore.update { Customize() }
        dataStore.setPreset(CustomizePreset.Default)
    }
}
