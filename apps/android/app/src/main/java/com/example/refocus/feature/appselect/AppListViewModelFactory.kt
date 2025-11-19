package com.example.refocus.feature.appselect

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.refocus.data.RepositoryProvider

class AppListViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {

    private val repositoryProvider = RepositoryProvider(application)

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppListViewModel::class.java)) {
            return AppListViewModel(
                application = application,
                targetsRepository = repositoryProvider.targetsRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
