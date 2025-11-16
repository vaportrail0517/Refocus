package com.example.refocus.feature.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.refocus.core.model.OverlaySettings
import com.example.refocus.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    application: Application,
    private val settingsRepository: SettingsRepository
) : AndroidViewModel(application) {

    data class UiState(
        val overlaySettings: OverlaySettings,
        val isLoading: Boolean = true
    )

    private val _uiState = MutableStateFlow(
        UiState(overlaySettings = OverlaySettings(), isLoading = true)
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.observeOverlaySettings()
                .collect { settings ->
                    _uiState.value = UiState(
                        overlaySettings = settings,
                        isLoading = false
                    )
                }
        }
    }

    fun updateGracePeriodSeconds(sec: Long) {
        viewModelScope.launch {
            settingsRepository.updateOverlaySettings { current ->
                current.copy(gracePeriodMillis = sec * 1000L)
            }
        }
    }

    fun updatePollingIntervalMillis(ms: Long) {
        viewModelScope.launch {
            settingsRepository.updateOverlaySettings { current ->
                current.copy(pollingIntervalMillis = ms)
            }
        }
    }

    // フォントサイズや timeToMaxMinutes に対する更新関数も同様
}
