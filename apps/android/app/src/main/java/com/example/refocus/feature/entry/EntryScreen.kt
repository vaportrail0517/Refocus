package com.example.refocus.feature.entry

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.flow.first

@Composable
fun EntryScreen(
    onNeedFullOnboarding: () -> Unit,
    onAllReady: () -> Unit,
) {
    val viewModel: EntryViewModel = hiltViewModel()
    val onboardingRepository = viewModel.onboardingRepository
    LaunchedEffect(Unit) {
        val completed = onboardingRepository.completedFlow.first()
        if (!completed) {
            onNeedFullOnboarding()
        } else {
            onAllReady()
        }
    }

    Box(
        modifier = Modifier.Companion.fillMaxSize(),
        contentAlignment = Alignment.Companion.Center
    ) {
        CircularProgressIndicator()
    }
}