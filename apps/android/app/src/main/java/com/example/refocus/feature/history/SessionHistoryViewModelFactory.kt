package com.example.refocus.feature.history

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.refocus.data.RepositoryProvider
import com.example.refocus.feature.monitor.ForegroundAppMonitorProvider

class SessionHistoryViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {

    private val repositoryProvider = RepositoryProvider(application)

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SessionHistoryViewModel::class.java)) {
            return SessionHistoryViewModel(
                application = application,
                sessionRepository = repositoryProvider.sessionRepository,
                foregroundAppMonitor = ForegroundAppMonitorProvider.get(application)
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
