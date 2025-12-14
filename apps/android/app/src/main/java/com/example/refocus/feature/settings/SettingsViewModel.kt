package com.example.refocus.feature.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.refocus.core.model.Customize
import com.example.refocus.core.model.CustomizePreset
import com.example.refocus.data.repository.SettingsRepository
import com.example.refocus.domain.app.AppDataResetter
import com.example.refocus.domain.timeline.EventRecorder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val settingsRepository: SettingsRepository,
    private val appDataResetter: AppDataResetter,
    private val eventRecorder: EventRecorder,
) : AndroidViewModel(application) {

    data class UiState(
        val customize: Customize,
        val preset: CustomizePreset = CustomizePreset.Default,
        val isLoading: Boolean = true,
    )

    private val _uiState = MutableStateFlow(
        UiState(
            customize = Customize(),
            preset = CustomizePreset.Default,
            isLoading = true,
        )
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                settingsRepository.observeOverlaySettings(),
                settingsRepository.observeSettingsPreset(),
            ) { settings, preset ->
                UiState(
                    customize = settings,
                    preset = preset,
                    isLoading = false,
                )
            }.collect { combined ->
                _uiState.value = combined
            }
        }
    }

    // --- 有効/無効・起動関連 ---

    fun updateOverlayEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setOverlayEnabled(enabled)
            eventRecorder.onSettingsChanged(
                key = "overlayEnabled",
                newValueDescription = enabled.toString(),
            )
        }
    }

    fun updateAutoStartOnBoot(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoStartOnBoot(enabled)
            eventRecorder.onSettingsChanged(
                key = "autoStartOnBoot",
                newValueDescription = enabled.toString(),
            )
        }
    }

    fun resetAllData() {
        viewModelScope.launch {
            appDataResetter.resetAll()
        }
    }
}
