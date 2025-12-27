package com.example.refocus.feature.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.example.refocus.core.model.SuggestionDailyStats
import com.example.refocus.core.model.TimeBucketStats
import com.example.refocus.core.util.formatDurationMilliSeconds
import com.example.refocus.ui.components.SectionCard

@Composable
internal fun PeakTimeCard(
    timeBuckets: List<TimeBucketStats>,
    onClick: () -> Unit = {},
) {
    SectionCard(
        title = "時間を使いがちな時間帯",
        description = "対象アプリをよく使っている時間帯の一覧です．",
        onClick = onClick,
    ) {
        val topBuckets = timeBuckets
            .filter { it.targetUsageMinutes > 0 }
            .sortedByDescending { it.targetUsageMinutes }
            .take(3)

        if (topBuckets.isEmpty()) {
            Text("まだデータがありません．")
            return@SectionCard
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            topBuckets.forEach { bucket ->
                Text(
                    text = "${formatBucketRange(bucket)}   合計 ${
                        formatDurationMilliSeconds(
                            bucket.targetUsageMinutes * 60_000L
                        )
                    }",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

internal fun formatBucketRange(
    bucket: TimeBucketStats,
): String {
    fun formatMinutes(m: Int): String {
        val h = m / 60
        val mm = m % 60
        return String.format("%02d:%02d", h, mm)
    }
    return "${formatMinutes(bucket.startMinutesOfDay)}〜${formatMinutes(bucket.endMinutesOfDay)}"
}

@Composable
internal fun SuggestionCard(
    stats: SuggestionDailyStats,
    onClick: () -> Unit = {},
) {
    SectionCard(
        title = "提案との付き合い方",
        description = "今日，提案がどれくらい表示され，どのように扱ったかの概要です．",
        onClick = onClick,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("提案例数: ${stats.totalShown}")
            Text("「あとで」「閉じる」: ${stats.skippedCount}")
            Text("短時間で終了した提案: ${stats.endedSoonCount}")
            Text("続行した提案: ${stats.continuedCount}")
            Text("まだ判定できない提案: ${stats.noEndYetCount}")
        }
    }
}
