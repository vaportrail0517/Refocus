package com.example.refocus.feature.onboarding

import android.app.Application
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.refocus.data.RepositoryProvider
import com.example.refocus.system.permissions.PermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


@Composable
fun EntryScreen(
    onNeedFullOnboarding: () -> Unit,
    onNeedPermissionFix: () -> Unit,
    onAllReady: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val repositoryProvider = remember { RepositoryProvider(app) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                repositoryProvider.sessionRepository.repairStaleSessions()
            } catch (e: Exception) {
                Log.e("EntryScreen", "repairStaleSessions failed", e)
            }
        }

        val hasUsage = PermissionHelper.hasUsageAccess(context)
        val hasOverlay = PermissionHelper.hasOverlayPermission(context)
        val hasNotif = PermissionHelper.hasNotificationPermission(context)
        val allGranted = hasUsage && hasOverlay && hasNotif

        val completed = OnboardingState.isCompleted(context)

        if (!completed) {
            onNeedFullOnboarding()
        } else {
            if (allGranted) {
                onAllReady()
            } else {
                onNeedPermissionFix()
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}
