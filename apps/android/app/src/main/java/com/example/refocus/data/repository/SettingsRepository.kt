package com.example.refocus.data.repository

import com.example.refocus.core.model.OverlaySettings
import com.example.refocus.data.datastore.SettingsDataStore
import kotlinx.coroutines.flow.Flow

class SettingsRepository(
    private val dataStore: SettingsDataStore
) {
    fun observeOverlaySettings(): Flow<OverlaySettings> = dataStore.settingsFlow
    suspend fun updateOverlaySettings(
        transform: (OverlaySettings) -> OverlaySettings
    ) {
        dataStore.update(transform)
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
