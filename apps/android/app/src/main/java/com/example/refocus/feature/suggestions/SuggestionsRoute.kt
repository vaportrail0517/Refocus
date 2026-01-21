package com.example.refocus.feature.suggestions

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestionsRoute(viewModel: SuggestionsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SuggestionsScreen(
        uiState = uiState,
        onCreateSuggestion = { title, timeSlots, durationTag, priority, action ->
            viewModel.createSuggestion(title, timeSlots, durationTag, priority, action)
        },
        onUpdateSuggestion = { id, title, timeSlots, durationTag, priority, action ->
            viewModel.updateSuggestion(id, title, timeSlots, durationTag, priority, action)
        },
        onDeleteConfirmed = { id ->
            viewModel.deleteSuggestion(id)
        },
    )
}
