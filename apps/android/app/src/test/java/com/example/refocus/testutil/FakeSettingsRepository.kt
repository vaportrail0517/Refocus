package com.example.refocus.testutil

import com.example.refocus.core.model.Customize
import com.example.refocus.core.model.CustomizePreset
import com.example.refocus.core.preset.CustomizePresetValues
import com.example.refocus.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * JVM 単体テスト用の in-memory SettingsRepository．
 *
 * - DataStore を使わず，MutableStateFlow で状態を保持する
 * - domain の SettingsCommand / usecase のテストで使用する
 */
class FakeSettingsRepository(
    initialCustomize: Customize = Customize(),
    initialPreset: CustomizePreset = CustomizePreset.Default,
) : SettingsRepository {
    private val customizeState = MutableStateFlow(initialCustomize)
    private val presetState = MutableStateFlow(initialPreset)

    override fun observeOverlaySettings(): Flow<Customize> = customizeState.asStateFlow()

    override fun observeSettingsPreset(): Flow<CustomizePreset> = presetState.asStateFlow()

    override suspend fun updateOverlaySettings(transform: (Customize) -> Customize) {
        customizeState.value = transform(customizeState.value)
    }

    override suspend fun setSettingsPreset(preset: CustomizePreset) {
        presetState.value = preset
    }

    override suspend fun applyPreset(preset: CustomizePreset) {
        when (preset) {
            CustomizePreset.Default -> {
                customizeState.value = CustomizePresetValues.default
                presetState.value = CustomizePreset.Default
            }
            CustomizePreset.Debug -> {
                customizeState.value = CustomizePresetValues.debug
                presetState.value = CustomizePreset.Debug
            }
            CustomizePreset.Custom -> {
                // Custom は「値はそのまま，種別だけ」になり得るので，ここでは preset のみ更新する
                presetState.value = CustomizePreset.Custom
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
        updateOverlaySettings { it.copy(suggestionForegroundStableSeconds = seconds) }
    }

    override suspend fun setRestSuggestionEnabled(enabled: Boolean) {
        updateOverlaySettings { it.copy(restSuggestionEnabled = enabled) }
    }

    override suspend fun resetToDefaults() {
        customizeState.value = CustomizePresetValues.default
        presetState.value = CustomizePreset.Default
    }
}
