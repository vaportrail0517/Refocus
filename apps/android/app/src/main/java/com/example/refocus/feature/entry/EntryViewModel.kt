package com.example.refocus.feature.entry

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.refocus.domain.repository.OnboardingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EntryUiState(
    val isLoading: Boolean = true,
    val completed: Boolean? = null,
)

@HiltViewModel
class EntryViewModel @Inject constructor(
    application: Application,
    val onboardingRepository: OnboardingRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(EntryUiState())
    val uiState: StateFlow<EntryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val completed = onboardingRepository.completedFlow.first()
            _uiState.value = EntryUiState(
                isLoading = false,
                completed = completed
            )
        }
    }
}