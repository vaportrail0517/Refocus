package com.example.refocus.feature.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.refocus.core.model.DailyStats
import com.example.refocus.core.util.formatDurationMilliSeconds
import com.example.refocus.ui.components.SectionCard

@Composable
internal fun SummaryCard(
    stats: DailyStats,
    onClick: () -> Unit = {},
) {
    SectionCard(
        title = "今日のサマリー",
        description = "対象アプリの利用状況の概要です．",
        onClick = onClick,
    ) {
        Column {
            Text(
                text = "合計利用時間",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = formatDurationMilliSeconds(stats.totalUsageMillis),
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf(
                "セッション数" to stats.sessionCount.toString(),
                "平均セッション長" to formatDurationMilliSeconds(stats.averageSessionDurationMillis),
                "最長セッション" to formatDurationMilliSeconds(stats.longestSessionDurationMillis),
                "長めのセッション" to "${stats.longSessionCount} 回",
                "とても長いセッション" to "${stats.veryLongSessionCount} 回",
                "提案表示回数" to (stats.suggestionStats?.totalShown?.toString() ?: "0"),
                "提案を見送った割合" to (
                    stats.suggestionStats?.let { s ->
                        if (s.totalShown > 0) "${(s.skippedCount * 100 / s.totalShown)}%" else "-"
                    } ?: "-"
                ),
            ).forEach { (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
internal fun PeriodSummarySection(
    title: String,
    rows: List<Pair<String, String>>,
) {
    SectionCard(
        title = title,
        description = null,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            rows.forEach { (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

internal fun buildYesterdaySummaryRows(today: DailyStats): List<Pair<String, String>> {
    // TODO: 本来は「昨日の DailyStats」を受け取り，今日との差分を表示する．
    // ここではひとまず今日の値を使ったダミーとしておく．
    return listOf(
        "合計利用時間" to formatDurationMilliSeconds(today.totalUsageMillis),
        "セッション数" to today.sessionCount.toString(),
        "平均セッション長" to formatDurationMilliSeconds(today.averageSessionDurationMillis),
        "提案表示回数" to (today.suggestionStats?.totalShown?.toString() ?: "0"),
        "提案を見送った割合" to (
            today.suggestionStats?.let { s ->
                if (s.totalShown > 0) "${(s.skippedCount * 100 / s.totalShown)}%" else "-"
            } ?: "-"
        ),
    )
}

internal fun buildLast7DaysSummaryRows(today: DailyStats): List<Pair<String, String>> {
    // TODO: 本来は過去7日分を集計して 1日平均などを出す
    return listOf(
        "1日あたり平均利用時間" to formatDurationMilliSeconds(today.totalUsageMillis),
        "1日あたりセッション数" to today.sessionCount.toString(),
        "1日あたり平均セッション長" to formatDurationMilliSeconds(today.averageSessionDurationMillis),
        "提案表示回数" to (today.suggestionStats?.totalShown?.toString() ?: "0"),
        "提案を見送った割合" to (
            today.suggestionStats?.let { s ->
                if (s.totalShown > 0) "${(s.skippedCount * 100 / s.totalShown)}%" else "-"
            } ?: "-"
        ),
    )
}
