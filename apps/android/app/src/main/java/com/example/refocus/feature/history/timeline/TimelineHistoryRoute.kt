package com.example.refocus.feature.history.timeline

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

@Composable
fun TimelineHistoryRoute(
    modifier: Modifier = Modifier,
) {
    val viewModel: TimelineHistoryViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()

    TimelineHistoryContent(
        uiState = uiState,
        onPreviousDay = viewModel::onPreviousDay,
        onNextDay = viewModel::onNextDay,
        onSelectDate = viewModel::onSelectDate,
        onToggleCategory = viewModel::onToggleCategory,
        onSelectAllCategories = viewModel::onSelectAllCategories,
        modifier = modifier,
    )
}
