package com.example.refocus.feature.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.refocus.feature.history.timeline.TimelineHistoryViewModel

@Composable
fun HistoryRoute(
    onNavigateBack: () -> Unit,
) {
    val sessionHistoryViewModel: SessionHistoryViewModel = hiltViewModel()
    val sessionUiState by sessionHistoryViewModel.uiState.collectAsState()

    val timelineHistoryViewModel: TimelineHistoryViewModel = hiltViewModel()
    val timelineUiState by timelineHistoryViewModel.uiState.collectAsState()

    HistoryScreen(
        sessionUiState = sessionUiState,
        timelineUiState = timelineUiState,
        onNavigateBack = onNavigateBack,
    )
}
