package com.example.refocus.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.refocus.core.model.DailyStats
import com.example.refocus.feature.home.components.FocusSection
import com.example.refocus.feature.home.components.NotificationWarningCard
import com.example.refocus.feature.home.components.PermissionWarningCard
import com.example.refocus.feature.home.components.TargetAppsSection
import com.example.refocus.feature.stats.StatsDetailSection

@Composable
internal fun HomeContent(
    stats: DailyStats?,
    appLabelByPackage: Map<String, String>,
    targetApps: List<HomeViewModel.TargetAppUiModel>,
    hasCorePermissions: Boolean,
    showNotificationWarning: Boolean,
    innerPadding: PaddingValues,
    onOpenStatsDetail: (StatsDetailSection) -> Unit,
    onOpenPermissionFixFlow: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenAppSelect: () -> Unit,
) {
    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            if (!hasCorePermissions || showNotificationWarning) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (!hasCorePermissions) {
                        PermissionWarningCard(
                            onClick = onOpenPermissionFixFlow,
                        )
                    }
                    if (showNotificationWarning) {
                        NotificationWarningCard(
                            onClick = onOpenNotificationSettings,
                        )
                    }
                }
            }
        }

        item {
            FocusSection(
                stats = stats,
                appLabelByPackage = appLabelByPackage,
                onOpenSection = onOpenStatsDetail,
            )
        }

        item {
            TargetAppsSection(
                apps = targetApps,
                onAddClick = onOpenAppSelect,
                onAppClick = { /* 必要があればここで何かする */ },
            )
        }
    }
}
