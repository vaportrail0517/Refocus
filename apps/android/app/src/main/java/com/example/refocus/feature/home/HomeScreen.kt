package com.example.refocus.feature.home

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.example.refocus.core.model.DailyStats
import com.example.refocus.feature.stats.StatsDetailSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    isRunning: Boolean,
    hasCorePermissions: Boolean,
    showNotificationWarning: Boolean,
    stats: DailyStats?,
    appLabelByPackage: Map<String, String>,
    targetApps: List<HomeViewModel.TargetAppUiModel>,
    onToggleRunning: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStatsDetail: (StatsDetailSection) -> Unit,
    onOpenPermissionFixFlow: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenAppSelect: () -> Unit,
) {
    Scaffold(
        topBar = {
            HomeTopBar(
                onOpenSettings = onOpenSettings,
            )
        },
        contentWindowInsets = WindowInsets(0.dp),
    ) { innerPadding ->
        HomeContent(
            stats = stats,
            appLabelByPackage = appLabelByPackage,
            targetApps = targetApps,
            isRunning = isRunning,
            onToggleRunning = onToggleRunning,
            hasCorePermissions = hasCorePermissions,
            showNotificationWarning = showNotificationWarning,
            innerPadding = innerPadding,
            onOpenStatsDetail = onOpenStatsDetail,
            onOpenPermissionFixFlow = onOpenPermissionFixFlow,
            onOpenNotificationSettings = onOpenNotificationSettings,
            onOpenAppSelect = onOpenAppSelect,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeTopBar(onOpenSettings: () -> Unit) {
    TopAppBar(
        title = { Text("ホーム") },
        actions = {
            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "設定を開く",
                )
            }
        },
        windowInsets = WindowInsets(0.dp),
    )
}
