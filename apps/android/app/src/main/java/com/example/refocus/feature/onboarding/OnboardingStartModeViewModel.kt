package com.example.refocus.feature.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.refocus.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class StartMode {
    AutoAndNow,
    NowOnly,
    Off
}

@HiltViewModel
class OnboardingStartModeViewModel @Inject constructor(
    application: Application,
    private val settingsRepository: SettingsRepository
) : AndroidViewModel(application) {

    fun applyStartMode(mode: StartMode) {
        viewModelScope.launch {
            when (mode) {
                StartMode.AutoAndNow -> {
                    settingsRepository.setOverlayEnabled(true)
                    settingsRepository.setAutoStartOnBoot(true)
                }

                StartMode.NowOnly -> {
                    settingsRepository.setOverlayEnabled(true)
                    settingsRepository.setAutoStartOnBoot(false)
                }

                StartMode.Off -> {
                    settingsRepository.setOverlayEnabled(false)
                    settingsRepository.setAutoStartOnBoot(false)
                }
            }
        }
    }
}
