package com.example.refocus.feature.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.refocus.core.model.AppUsageStats
import com.example.refocus.core.util.formatDurationMilliSeconds
import com.example.refocus.ui.components.SectionCard

@Composable
internal fun AppUsageCard(
    apps: List<AppUsageStats>,
    appLabelByPackage: Map<String, String>,
    onClick: () -> Unit = {},
) {
    SectionCard(
        title = "アプリ別の使い方",
        description = "よく使っているアプリの一覧です．",
        onClick = onClick,
    ) {
        if (apps.isEmpty()) {
            Text("まだデータがありません．")
            return@SectionCard
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val topApps = apps
                .sortedByDescending { it.totalUsageMillis }
                .take(5)

            topApps.forEach { app ->
                AppUsageRow(app, appLabelByPackage)
            }

            val others = apps.size - topApps.size
            if (others > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "他 $others 件...",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
internal fun AppUsageRow(
    app: AppUsageStats,
    appLabelByPackage: Map<String, String>,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Column {
            val label = appLabelByPackage[app.packageName] ?: app.packageName
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "セッション数 ${app.sessionCount} / 平均 ${formatDurationMilliSeconds(app.averageSessionDurationMillis)}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            text = formatDurationMilliSeconds(app.totalUsageMillis),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
