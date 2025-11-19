package com.example.refocus.feature.suggestions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.refocus.core.model.Suggestion
import com.example.refocus.data.repository.SuggestionsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SuggestionsViewModel(
    application: Application,
    private val suggestionsRepository: SuggestionsRepository
) : AndroidViewModel(application) {

    data class UiState(
        val suggestion: Suggestion? = null,
        val isLoading: Boolean = true
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            suggestionsRepository
                .observeSuggestion()
                .collect { suggestion ->
                    _uiState.value = UiState(
                        suggestion = suggestion,
                        isLoading = false
                    )
                }
        }
    }

    fun submitSuggestion(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        viewModelScope.launch {
            suggestionsRepository.setSuggestion(trimmed)
        }
    }

    fun deleteSuggestion() {
        viewModelScope.launch {
            suggestionsRepository.clearSuggestion()
        }
    }
}
