package com.example.refocus.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.refocus.core.model.AppUsageStats
import com.example.refocus.core.model.DailyStats
import com.example.refocus.core.model.SuggestionDailyStats
import com.example.refocus.core.model.TimeBucketStats
import com.example.refocus.ui.components.SectionCard

@Composable
fun StatsRoute(
    viewModel: StatsViewModel = hiltViewModel(),
    onOpenHistory: () -> Unit = {},   // 後で履歴画面へのショートカットに使う
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    StatsScreen(
        uiState = uiState,
        onOpenHistory = onOpenHistory,
        onRangeChange = { range ->
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    uiState: StatsViewModel.UiState,
    onOpenHistory: () -> Unit,
    onRangeChange: (StatsRange) -> Unit,
) {
    Scaffold(
        topBar = {
            StatsTopBar(
                dateLabel = uiState.dateLabel,
                statsRange = uiState.statsRange,
                onRangeChange = onRangeChange,
            )
        }
    ) { innerPadding ->
        when {
            uiState.isLoading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.todayStats == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("まだ統計データがありません。")
                }
            }

            else -> {
                StatsContent(
                    stats = uiState.todayStats,
                    modifier = Modifier.padding(innerPadding),
                    onOpenHistory = onOpenHistory,
                )
            }
        }
    }
}

@Composable
private fun StatsContent(
    stats: DailyStats,
    modifier: Modifier = Modifier,
    onOpenHistory: () -> Unit,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // カード1: 今日のサマリー
        item {
            SummaryCard(stats)
        }

        // カード2: アプリ別
        if (stats.appUsageStats.isNotEmpty()) {
            item {
                AppUsageCard(stats.appUsageStats)
            }
        }

        // カード3: 1日の流れ（タイムライン）
        if (stats.timeBuckets.isNotEmpty()) {
            item {
                TimelineCard(stats.timeBuckets)
            }
        }

        // カード4: 提案との付き合い方
        stats.suggestionStats?.let { suggestion ->
            item {
                SuggestionCard(suggestion)
            }
        }

        // カード5: 時間を使いがちな時間帯
        if (stats.timeBuckets.isNotEmpty()) {
            item {
                PeakTimeCard(stats.timeBuckets)
            }
        }

        // カード6: 履歴へのショートカット
        item {
            HistoryShortcutCard(onOpenHistory)
        }
    }
}

@Composable
private fun SummarySection(stats: DailyStats) {
    Column {
        Text(
            text = "合計利用時間: ${formatDuration(stats.totalUsageMillis)}",
            style = MaterialTheme.typography.titleMedium,
        )
        Text("セッション数: ${stats.sessionCount}")
        Text("平均セッション長: ${formatDuration(stats.averageSessionDurationMillis)}")
        Text("最長セッション: ${formatDuration(stats.longestSessionDurationMillis)}")
    }
}

private fun formatDuration(durationMillis: Long): String {
    val totalSeconds = durationMillis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hours = minutes / 60
    val remMinutes = minutes % 60
    return if (hours > 0) {
        String.format("%d時間%02d分%02d秒", hours, remMinutes, seconds)
    } else {
        String.format("%d分%02d秒", minutes, seconds)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatsTopBar(
    dateLabel: String,
    statsRange: StatsRange,
    onRangeChange: (StatsRange) -> Unit,
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = "統計",
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        actions = {
            RangeToggle(
                selected = statsRange,
                onRangeChange = onRangeChange,
            )
        },
    )
}

@Composable
private fun RangeToggle(
    selected: StatsRange,
    onRangeChange: (StatsRange) -> Unit,
) {
    // ひとまず Today のみ機能させる。将来 Last7Days / Last30Days を追加しやすいようにしておく。
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "日",
            style = MaterialTheme.typography.bodyMedium,
        )
        // 週・月は今はダミー or 無効状態にしておいてもよい
        // Text("週", modifier = Modifier.alpha(0.4f))
        // Text("月", modifier = Modifier.alpha(0.4f))
    }
}

@Composable
private fun SummaryCard(
    stats: DailyStats,
) {
    SectionCard(
        title = "今日のサマリー",
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = "合計利用時間",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = formatDuration(stats.totalUsageMillis),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
            ) {
                Text("セッション数: ${stats.sessionCount}")
                Text("平均セッション: ${formatDuration(stats.averageSessionDurationMillis)}")
                Text("最長セッション: ${formatDuration(stats.longestSessionDurationMillis)}")
            }
        }
    }
}

@Composable
private fun AppUsageCard(
    apps: List<AppUsageStats>,
) {
    SectionCard(
        title = "アプリ別の使い方",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            apps.take(5).forEach { app ->
                AppUsageRow(app)
            }
        }
    }
}

@Composable
private fun AppUsageRow(
    app: AppUsageStats,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "セッション数 ${app.sessionCount} / 平均 ${formatDuration(app.averageSessionDurationMillis)}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            text = formatDuration(app.totalUsageMillis),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun TimelineCard(
    timeBuckets: List<TimeBucketStats>,
) {
    SectionCard(
        title = "1日の流れ",
    ) {
        TimelineBar(timeBuckets)

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("0時", style = MaterialTheme.typography.bodySmall)
            Text("6時", style = MaterialTheme.typography.bodySmall)
            Text("12時", style = MaterialTheme.typography.bodySmall)
            Text("18時", style = MaterialTheme.typography.bodySmall)
            Text("24時", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TimelineBar(
    timeBuckets: List<TimeBucketStats>,
) {
    val maxUsage = (timeBuckets.maxOfOrNull { it.totalUsageMillis } ?: 0L)
        .coerceAtLeast(1L)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        timeBuckets.forEach { bucket ->
            val ratio = bucket.totalUsageMillis.toFloat() / maxUsage.toFloat()

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 0.5.dp)
                    .align(Alignment.Bottom),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(fraction = if (ratio > 0f) ratio else 0.05f)
                        .align(Alignment.BottomCenter)
                        .background(
                            if (bucket.totalUsageMillis > 0L) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f + 0.4f * ratio)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                )
            }
        }
    }
}

@Composable
private fun SuggestionCard(
    stats: SuggestionDailyStats,
) {
    SectionCard(
        title = "提案の履歴",
    ) {
        if (stats.totalShown == 0) {
            Text("今日は提案は表示されませんでした。")
            return@SectionCard
        }

        Text("今日は提案が ${stats.totalShown} 回表示されました。")
        Spacer(Modifier.height(8.dp))

        Text(
            text = "行動の内訳",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text("・提案を見送った（あとで / 閉じる）: ${stats.skippedCount} 回")
        Text("・このセッションでは非表示: ${stats.disabledForSessionCount} 回")

        Spacer(Modifier.height(8.dp))

        Text(
            text = "提案のあとアプリを閉じたタイミング",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text("・短い時間でアプリを閉じた提案: ${stats.endedSoonCount} 回")
        Text("・その後もしばらく続けた提案: ${stats.continuedCount} 回")
        if (stats.noEndYetCount > 0) {
            Text("・まだセッションが続いている提案: ${stats.noEndYetCount} 回")
        }
    }
}

@Composable
private fun PeakTimeCard(
    timeBuckets: List<TimeBucketStats>,
) {
    SectionCard(
        title = "時間を使いがちな時間帯",
    ) {
        val topBuckets = timeBuckets
            .filter { it.totalUsageMillis > 0L }
            .sortedByDescending { it.totalUsageMillis }
            .take(3)

        if (topBuckets.isEmpty()) {
            Text("まだデータがありません。")
            return@SectionCard
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            topBuckets.forEach { bucket ->
                Text(
                    text = "${formatBucketRange(bucket)}   合計 ${formatDuration(bucket.totalUsageMillis)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private fun formatBucketRange(
    bucket: TimeBucketStats,
): String {
    fun formatMinutes(m: Int): String {
        val h = m / 60
        val mm = m % 60
        return String.format("%02d:%02d", h, mm)
    }
    return "${formatMinutes(bucket.startMinutesOfDay)} – ${formatMinutes(bucket.endMinutesOfDay)}"
}

@Composable
private fun HistoryShortcutCard(
    onOpenHistory: () -> Unit,
) {
    SectionCard(
        title = "詳細な履歴を確認",
    ) {
        Button(onClick = onOpenHistory) {
            Text("今日のセッション履歴を開く")
        }
    }
}

