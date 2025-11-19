package com.example.refocus.feature.suggestions

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.refocus.data.RepositoryProvider

class SuggestionsViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {

    private val repositoryProvider = RepositoryProvider(application)

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SuggestionsViewModel::class.java)) {
            return SuggestionsViewModel(
                application = application,
                suggestionsRepository = repositoryProvider.suggestionsRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
