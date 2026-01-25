package com.example.refocus.feature.entry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.refocus.core.logging.RefocusLog
import com.example.refocus.domain.repository.OnboardingRepository
import com.example.refocus.domain.targets.EnsureAppCatalogForCurrentTargetsUseCase
import com.example.refocus.domain.targets.EnsureTargetsExcludeHiddenAppsUseCase
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
class EntryViewModel
    @Inject
    constructor(
        val onboardingRepository: OnboardingRepository,
        private val ensureAppCatalogForCurrentTargetsUseCase: EnsureAppCatalogForCurrentTargetsUseCase,
        private val ensureTargetsExcludeHiddenAppsUseCase: EnsureTargetsExcludeHiddenAppsUseCase,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(EntryUiState())
        val uiState: StateFlow<EntryUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                launch(Dispatchers.Default) {
                    try {
                        ensureTargetsExcludeHiddenAppsUseCase.ensure(recordEvent = false)
                    } catch (e: Exception) {
                        RefocusLog.w("EntryViewModel", e) { "Failed to normalize targets" }
                    }

                    try {
                        ensureAppCatalogForCurrentTargetsUseCase.ensure()
                    } catch (e: Exception) {
                        RefocusLog.w("EntryViewModel", e) { "Failed to bootstrap app catalog" }
                    }
                }

                val completed = onboardingRepository.completedFlow.first()
                _uiState.value =
                    EntryUiState(
                        isLoading = false,
                        completed = completed,
                    )
            }
        }
    }
