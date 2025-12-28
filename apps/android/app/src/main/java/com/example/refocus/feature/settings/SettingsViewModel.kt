package com.example.refocus.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.refocus.core.model.Customize
import com.example.refocus.core.model.CustomizePreset
import com.example.refocus.domain.repository.SettingsRepository
import com.example.refocus.domain.reset.AppDataResetter
import com.example.refocus.domain.settings.SettingsCommand
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val settingsCommand: SettingsCommand,
        private val appDataResetter: AppDataResetter,
    ) : ViewModel() {
        data class UiState(
            val customize: Customize,
            val preset: CustomizePreset = CustomizePreset.Default,
            val isLoading: Boolean = true,
        )

        private val _uiState =
            MutableStateFlow(
                UiState(
                    customize = Customize(),
                    preset = CustomizePreset.Default,
                    isLoading = true,
                ),
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

        /**
         * overlayEnabled の永続化が完了するまで待つ（サービス起動と競合させないため）．
         *
         * Composable 側では rememberCoroutineScope().launch { ... } から呼び出す想定．
         */
        suspend fun setOverlayEnabledAndWait(enabled: Boolean) {
            settingsCommand.setOverlayEnabled(
                enabled = enabled,
                source = "ui_settings",
            )
        }

        fun updateOverlayEnabled(enabled: Boolean) {
            viewModelScope.launch {
                settingsCommand.setOverlayEnabled(
                    enabled = enabled,
                    source = "ui_settings",
                )
            }
        }

        fun updateAutoStartOnBoot(enabled: Boolean) {
            viewModelScope.launch {
                settingsCommand.setAutoStartOnBoot(
                    enabled = enabled,
                    source = "ui_settings",
                )
            }
        }

        fun resetAllData() {
            viewModelScope.launch {
                appDataResetter.resetAll()
            }
        }
    }
