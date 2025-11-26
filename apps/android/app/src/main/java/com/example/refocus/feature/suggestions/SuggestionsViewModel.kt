package com.example.refocus.feature.suggestions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.refocus.core.model.Suggestion
import com.example.refocus.data.repository.SuggestionsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SuggestionsUiState(
    val suggestions: List<Suggestion> = emptyList(),
    val editingId: Long? = null,
    val isLoading: Boolean = true,
)

@HiltViewModel
class SuggestionsViewModel @Inject constructor(
    application: Application,
    private val suggestionsRepository: SuggestionsRepository,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SuggestionsUiState())
    val uiState: StateFlow<SuggestionsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            suggestionsRepository.observeSuggestions()
                .collectLatest { list ->
                    _uiState.value = _uiState.value.copy(
                        suggestions = list,
                        isLoading = false,
                    )
                }
        }
    }

    fun startEditing(id: Long) {
        _uiState.value = _uiState.value.copy(editingId = id)
    }

    fun stopEditing() {
        _uiState.value = _uiState.value.copy(editingId = null)
    }

    /**
     * 右下の「追加」ボタン押下時に呼ぶ。
     * 空タイトルの Suggestion を 1 件作って、そのカードを編集状態にする。
     */
    fun addSuggestionAndStartEditing() {
        viewModelScope.launch {
            val created = suggestionsRepository.addSuggestion(title = "")
            _uiState.value = _uiState.value.copy(editingId = created.id)
        }
    }

    /**
     * 編集を確定する。
     * 空文字で確定された場合は削除扱い。
     */
    fun commitEdit(id: Long, text: String) {
        val trimmed = text.trim()
        viewModelScope.launch {
            if (trimmed.isEmpty()) {
                suggestionsRepository.deleteSuggestion(id)
            } else {
                suggestionsRepository.updateSuggestion(id, trimmed)
            }
            stopEditing()
        }
    }

    fun deleteSuggestion(id: Long) {
        viewModelScope.launch {
            suggestionsRepository.deleteSuggestion(id)
            if (_uiState.value.editingId == id) {
                stopEditing()
            }
        }
    }
}
