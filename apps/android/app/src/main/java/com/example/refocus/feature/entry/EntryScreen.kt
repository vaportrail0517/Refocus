package com.example.refocus.feature.entry

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.refocus.data.RepositoryProvider
import kotlinx.coroutines.flow.first

@Composable
fun EntryScreen(
    onNeedFullOnboarding: () -> Unit,
    onAllReady: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val repositoryProvider = remember { RepositoryProvider(app) }
    val onboardingRepository = remember { repositoryProvider.onboardingRepository }
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