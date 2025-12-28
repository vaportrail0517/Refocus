package com.example.refocus.feature.history.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

@Composable
fun SessionHistoryRoute(modifier: Modifier = Modifier) {
    val viewModel: SessionHistoryViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()

    SessionHistoryContent(
        uiState = uiState,
        modifier = modifier,
    )
}
