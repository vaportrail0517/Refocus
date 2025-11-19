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
}
