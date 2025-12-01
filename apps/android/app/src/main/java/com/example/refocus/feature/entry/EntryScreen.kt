package com.example.refocus.feature.entry

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun EntryScreen(
    onNeedFullOnboarding: () -> Unit,
    onAllReady: () -> Unit,
) {
    val viewModel: EntryViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.completed) {
        when (uiState.completed) {
            true -> onAllReady()
            false -> onNeedFullOnboarding()
            null -> {}
        }
    }

    Box(
        modifier = Modifier.Companion.fillMaxSize(),
        contentAlignment = Alignment.Companion.Center
    ) {
        CircularProgressIndicator()
    }
}