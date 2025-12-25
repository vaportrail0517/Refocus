package com.example.refocus.feature.history

import androidx.compose.runtime.Composable

@Composable
fun HistoryRoute(
    onNavigateBack: () -> Unit,
) {
    HistoryScreen(
        onNavigateBack = onNavigateBack,
    )
}
