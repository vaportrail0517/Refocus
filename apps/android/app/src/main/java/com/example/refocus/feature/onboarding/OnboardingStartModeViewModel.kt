package com.example.refocus.feature.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.refocus.domain.settings.SettingsCommand
import com.example.refocus.system.overlay.startOverlayService
import com.example.refocus.system.permissions.PermissionHelper
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
    private val settingsCommand: SettingsCommand
) : AndroidViewModel(application) {

    private val appContext: Application
        get() = getApplication()

    fun applyStartMode(mode: StartMode) {
        viewModelScope.launch {
            when (mode) {
                StartMode.AutoAndNow -> {
                    settingsCommand.setOverlayEnabled(enabled = true, source = "onboarding")
                    settingsCommand.setAutoStartOnBoot(enabled = true, source = "onboarding")
                    if (PermissionHelper.hasAllCorePermissions(appContext)) {
                        appContext.startOverlayService()
                    }
                }

                StartMode.NowOnly -> {
                    settingsCommand.setOverlayEnabled(enabled = true, source = "onboarding")
                    settingsCommand.setAutoStartOnBoot(enabled = false, source = "onboarding")
                    if (PermissionHelper.hasAllCorePermissions(appContext)) {
                        appContext.startOverlayService()
                    }
                }

                StartMode.Off -> {
                    settingsCommand.setOverlayEnabled(enabled = false, source = "onboarding")
                    settingsCommand.setAutoStartOnBoot(enabled = false, source = "onboarding")
                }
            }
        }
    }
}
