package com.example.refocus.feature.settings

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.refocus.data.RepositoryProvider

class SettingsViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {

    private val repositoryProvider = RepositoryProvider(application)

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            return SettingsViewModel(
                application = application,
                settingsRepository = repositoryProvider.settingsRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
