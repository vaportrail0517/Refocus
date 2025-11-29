package com.example.refocus.feature.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.refocus.data.repository.SettingsRepository
import com.example.refocus.system.overlay.service.startOverlayService
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
    private val settingsRepository: SettingsRepository
) : AndroidViewModel(application) {

    private val appContext: Application
        get() = getApplication()

    fun applyStartMode(mode: StartMode) {
        viewModelScope.launch {
            when (mode) {
                StartMode.AutoAndNow -> {
                    settingsRepository.setOverlayEnabled(true)
                    settingsRepository.setAutoStartOnBoot(true)
                    if (PermissionHelper.hasAllCorePermissions(appContext)) {
                        appContext.startOverlayService()
                    }
                }

                StartMode.NowOnly -> {
                    settingsRepository.setOverlayEnabled(true)
                    settingsRepository.setAutoStartOnBoot(false)
                    if (PermissionHelper.hasAllCorePermissions(appContext)) {
                        appContext.startOverlayService()
                    }
                }

                StartMode.Off -> {
                    settingsRepository.setOverlayEnabled(false)
                    settingsRepository.setAutoStartOnBoot(false)
                }
            }
        }
    }
}
