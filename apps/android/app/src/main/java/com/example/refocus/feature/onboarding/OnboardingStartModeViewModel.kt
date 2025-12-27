package com.example.refocus.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.refocus.domain.overlay.port.OverlayServiceController
import com.example.refocus.domain.settings.SettingsCommand
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
    private val settingsCommand: SettingsCommand,
    private val overlayServiceController: OverlayServiceController,
) : ViewModel() {

    fun applyStartMode(mode: StartMode) {
        viewModelScope.launch {
            when (mode) {
                StartMode.AutoAndNow -> {
                    settingsCommand.setOverlayEnabled(enabled = true, source = "onboarding")
                    settingsCommand.setAutoStartOnBoot(enabled = true, source = "onboarding")
                    overlayServiceController.startIfReady(source = "onboarding_auto_and_now")
                }

                StartMode.NowOnly -> {
                    settingsCommand.setOverlayEnabled(enabled = true, source = "onboarding")
                    settingsCommand.setAutoStartOnBoot(enabled = false, source = "onboarding")
                    overlayServiceController.startIfReady(source = "onboarding_now_only")
                }

                StartMode.Off -> {
                    settingsCommand.setOverlayEnabled(enabled = false, source = "onboarding")
                    settingsCommand.setAutoStartOnBoot(enabled = false, source = "onboarding")
                }
            }
        }
    }
}
