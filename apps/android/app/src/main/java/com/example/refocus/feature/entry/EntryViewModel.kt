package com.example.refocus.feature.entry

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.refocus.core.logging.RefocusLog
import androidx.lifecycle.viewModelScope
import com.example.refocus.domain.targets.EnsureAppCatalogForCurrentTargetsUseCase
import com.example.refocus.domain.repository.OnboardingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
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
    val onboardingRepository: OnboardingRepository,
    private val ensureAppCatalogForCurrentTargetsUseCase: EnsureAppCatalogForCurrentTargetsUseCase
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(EntryUiState())
    val uiState: StateFlow<EntryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            launch(Dispatchers.Default) {
                try {
                    ensureAppCatalogForCurrentTargetsUseCase.ensure()
                } catch (e: Exception) {
                    RefocusLog.w("EntryViewModel", e) { "Failed to bootstrap app catalog" }
                }
            }

            val completed = onboardingRepository.completedFlow.first()
            _uiState.value = EntryUiState(
                isLoading = false,
                completed = completed
            )
        }
    }
}