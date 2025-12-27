package com.example.refocus.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.refocus.core.model.DailyStats
import com.example.refocus.ui.components.SectionCard

@Composable
internal fun MonitoringSummaryCard(
    stats: DailyStats,
    onClick: () -> Unit = {},
) {
    SectionCard(
        title = "Refocus の動作状況",
        description = "今日どれくらい Refocus が対象アプリを見守っていたかの概要です．",
        onClick = onClick,
    ) {
        if (stats.monitoringTotalMinutes == 0) {
            Text(
                text = "今日はまだ Refocus が動作していません．\n",
                style = MaterialTheme.typography.bodyMedium,
            )
            return@SectionCard
        }

        val total = stats.monitoringTotalMinutes
        val withTarget = stats.monitoringWithTargetMinutes
        val withoutTarget = stats.monitoringWithoutTargetMinutes
        val ratio = if (total > 0) (withTarget * 100 / total) else 0

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "対象の使用時間 / Refocus の動作時間",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "${withTarget}分 / ${total}分",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(999.dp),
                    )
            ) {
                val fraction =
                    if (total > 0) withTarget.toFloat() / total.toFloat() else 0f
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(999.dp),
                        )
                )
            }
        }
    }
}
