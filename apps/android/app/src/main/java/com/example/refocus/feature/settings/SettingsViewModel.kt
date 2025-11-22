package com.example.refocus.feature.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.refocus.core.model.OverlaySettings
import com.example.refocus.data.repository.SettingsRepository
import com.example.refocus.core.model.OverlayTouchMode
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

    fun updateGracePeriodMillis(ms: Long) {
        viewModelScope.launch {
            settingsRepository.updateOverlaySettings { current ->
                current.copy(gracePeriodMillis = ms)
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

    fun updateMinFontSizeSp(minSp: Float) {
        viewModelScope.launch {
            settingsRepository.updateOverlaySettings { current ->
                current.copy(
                    minFontSizeSp = minSp
                )
            }
        }
    }

    fun updateMaxFontSizeSp(maxSp: Float) {
        viewModelScope.launch {
            settingsRepository.updateOverlaySettings { current ->
                current.copy(
                    maxFontSizeSp = maxSp
                )
            }
        }
    }

    fun updateTimeToMaxMinutes(minutes: Int) {
        viewModelScope.launch {
            settingsRepository.updateOverlaySettings { current ->
                current.copy(
                    timeToMaxMinutes = minutes
                )
            }
        }
    }

    fun updateOverlayTouchMode(mode: OverlayTouchMode) {
        viewModelScope.launch {
            settingsRepository.updateOverlaySettings { current ->
                current.copy(touchMode = mode)
            }
        }
    }

    fun updateOverlayPosition(x: Int, y: Int) {
        viewModelScope.launch {
            settingsRepository.updateOverlaySettings { current ->
                current.copy(positionX = x, positionY = y)
            }
        }
    }
    fun updateOverlayEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setOverlayEnabled(enabled)
        }
    }
    fun updateAutoStartOnBoot(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoStartOnBoot(enabled)
        }
    }

    fun updateSuggestionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSuggestionEnabled(enabled)
        }
    }

    fun updateRestSuggestionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setRestSuggestionEnabled(enabled)
        }
    }
}
