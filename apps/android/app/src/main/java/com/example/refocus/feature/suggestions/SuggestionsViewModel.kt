package com.example.refocus.feature.suggestions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.refocus.core.model.Suggestion
import com.example.refocus.core.model.SuggestionDurationTag
import com.example.refocus.core.model.SuggestionPriority
import com.example.refocus.core.model.SuggestionTimeSlot
import com.example.refocus.domain.repository.SuggestionsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SuggestionsUiState(
    val suggestions: List<Suggestion> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class SuggestionsViewModel @Inject constructor(
    private val suggestionsRepository: SuggestionsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SuggestionsUiState())
    val uiState: StateFlow<SuggestionsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            suggestionsRepository.observeSuggestions()
                .collect { suggestions ->
                    _uiState.value = _uiState.value.copy(
                        suggestions = suggestions,
                        isLoading = false,
                    )
                }
        }
    }

    /**
     * 新規のやりたいことを追加する。
     * ラベルが空の場合は何もしない。
     */
    fun createSuggestion(
        title: String,
        timeSlots: Set<SuggestionTimeSlot>,
        durationTag: SuggestionDurationTag,
        priority: SuggestionPriority,
    ) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            suggestionsRepository.addSuggestion(
                title = trimmed,
                timeSlots = timeSlots,
                durationTag = durationTag,
                priority = priority,
            )
        }
    }

    /**
     * 既存のやりたいことのラベルを更新する。
     * 空文字が渡された場合は何もしない（削除は deleteSuggestion で行う）。
     */
    fun updateSuggestion(
        id: Long,
        title: String,
        timeSlots: Set<SuggestionTimeSlot>,
        durationTag: SuggestionDurationTag,
        priority: SuggestionPriority,
    ) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            suggestionsRepository.updateSuggestion(id, trimmed)
            suggestionsRepository.updateSuggestionTags(
                id = id,
                timeSlots = timeSlots,
                durationTag = durationTag,
                priority = priority,
            )
        }
    }

    fun deleteSuggestion(id: Long) {
        viewModelScope.launch {
            suggestionsRepository.deleteSuggestion(id)
        }
    }

    fun updateTags(
        id: Long,
        timeSlots: Set<SuggestionTimeSlot>,
        durationTag: SuggestionDurationTag,
        priority: SuggestionPriority,
    ) {
        viewModelScope.launch {
            suggestionsRepository.updateSuggestionTags(
                id = id,
                timeSlots = timeSlots,
                durationTag = durationTag,
                priority = priority,
            )
        }
    }
}
