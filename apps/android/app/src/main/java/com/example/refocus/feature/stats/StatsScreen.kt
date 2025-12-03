package com.example.refocus.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.refocus.core.model.AppUsageStats
import com.example.refocus.core.model.DailyStats
import com.example.refocus.core.model.SuggestionDailyStats
import com.example.refocus.core.model.TimeBucketStats
import com.example.refocus.ui.components.SectionCard

enum class StatsDetailSection {
    UsageSummary,   // 今日のサマリー
    AppUsage,       // アプリ別
    Timeline,       // 1日の流れ
    Suggestions,    // 提案との付き合い方
    PeakTime,       // 時間を使いがちな時間帯
}

@Composable
fun StatsRoute(
    viewModel: StatsViewModel = hiltViewModel(),
    onOpenHistory: () -> Unit = {},   // 後で履歴画面へのショートカットに使う
    onOpenDetail: (StatsDetailSection) -> Unit = {}, // 各統計項目の詳細画面
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    StatsScreen(
        uiState = uiState,
        onOpenHistory = onOpenHistory,
        onRangeChange = { range ->
        },
        onOpenDetail = onOpenDetail,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    uiState: StatsViewModel.UiState,
    onOpenHistory: () -> Unit,
    onRangeChange: (StatsRange) -> Unit,
    onOpenDetail: (StatsDetailSection) -> Unit = {},
) {
    Scaffold(
        topBar = {
            StatsTopBar(
                dateLabel = uiState.dateLabel,
                statsRange = uiState.statsRange,
                onRangeChange = onRangeChange,
                onOpenHistory = onOpenHistory,
            )
        },
        contentWindowInsets = WindowInsets(0.dp),
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
                    onOpenSection = onOpenDetail,
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
    onOpenSection: (StatsDetailSection) -> Unit = {},
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 1日の流れ（タイムライン）
        if (stats.timeBuckets.isNotEmpty()) {
            item {
                TimelineCard(
                    timeBuckets = stats.timeBuckets,
                    onClick = { onOpenSection(StatsDetailSection.Timeline) },
                )
            }
        }

        // 提案履歴
        stats.suggestionStats?.let { suggestion ->
            item {
                SuggestionCard(
                    stats = suggestion,
                    onClick = { onOpenSection(StatsDetailSection.Suggestions) },
                )
            }
        }

        // 昨日のサマリー
        item {
            PeriodSummarySection(
                title = "昨日のまとめ",
                rows = buildYesterdaySummaryRows(stats),   // ※今は DailyStats を仮で流用
            )
        }

        // 過去7日間のサマリー
        item {
            PeriodSummarySection(
                title = "過去7日間のまとめ",
                rows = buildLast7DaysSummaryRows(stats),
            )
        }
    }
}

// ナビゲーションバー

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatsTopBar(
    dateLabel: String,
    statsRange: StatsRange,
    onRangeChange: (StatsRange) -> Unit,
    onOpenHistory: () -> Unit,
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
            IconButton(onClick = onOpenHistory) {
                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = "セッション履歴",
                )
            }

            RangeToggle(
                selected = statsRange,
                onRangeChange = onRangeChange,
            )
        },
        windowInsets = WindowInsets(0.dp),
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
        // Text(
        //     text = "日",
        //     style = MaterialTheme.typography.bodyMedium,
        // )
        // 週・月は今はダミー or 無効状態にしておいてもよい
        // Text("週", modifier = Modifier.alpha(0.4f))
        // Text("月", modifier = Modifier.alpha(0.4f))
    }
}

// データカード

@Composable
private fun SummaryCard(
    stats: DailyStats,
    onClick: () -> Unit = {},
) {
    SectionCard(
        title = "今日のサマリー",
        description = "対象アプリの利用状況の概要です。",
        onClick = onClick,
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
    onClick: () -> Unit = {},
) {
    SectionCard(
        title = "アプリ別の使い方",
        description = "よく使っているアプリの一覧です。",
        onClick = onClick,
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
    onClick: () -> Unit = {},
) {
    SectionCard(
        title = "タイムライン",
        description = "どの時間帯に対象アプリを使っていたかの目安です。",
    ) {
        TimelineChart(timeBuckets = timeBuckets)
    }
}

@Composable
private fun TimelineChart(
    timeBuckets: List<TimeBucketStats>,
) {
    val maxUsageMillis = (timeBuckets.maxOfOrNull { it.totalUsageMillis } ?: 0L)
        .coerceAtLeast(1L)
    val maxMinutes = (maxUsageMillis / 60_000L).toInt().coerceAtLeast(1)
    // 縦軸の最大値を「10分刻み」で丸める（例: 7分 -> 10, 18分 -> 20, 35分 -> 40）
    val axisMaxMinutes = ((maxMinutes + 9) / 10) * 10
    val yLabels = listOf(
        axisMaxMinutes,
        (axisMaxMinutes * 2) / 3,
        axisMaxMinutes / 3,
        0,
    )
    val yAxisWidth = 40.dp
    val chartHeight = 80.dp
    Column {
        // 上段: 縦軸ラベル + 棒グラフ
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight),
            verticalAlignment = Alignment.Bottom,
        ) {
            // 縦軸ラベル (分)
            Column(
                modifier = Modifier
                    .width(yAxisWidth)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                yLabels.forEach { minutes ->
                    Text(
                        text = "${minutes}分",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            // 棒グラフ本体
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalAlignment = Alignment.Bottom,
            ) {
                timeBuckets.forEach { bucket ->
                    val bucketMinutes =
                        (bucket.totalUsageMillis / 60_000L).toFloat()
                    val ratio = (bucketMinutes / axisMaxMinutes.toFloat())
                        .coerceIn(0f, 1f)
                    // 0 でもうっすら見えるように最低高さ
                    val heightFraction = if (ratio > 0f) {
                        0.1f + 0.9f * ratio
                    } else {
                        0.02f
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = 0.5.dp),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(heightFraction)
                                .background(
                                    color = if (bucket.totalUsageMillis > 0L) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    shape = RoundedCornerShape(
                                        topStart = 2.dp,
                                        topEnd = 2.dp,
                                    ),
                                )
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        // 下段: 横軸ラベル（0, 6, 12, 18, 24 時）と縦線
        TimelineXAxis(yAxisWidth = yAxisWidth)
    }
}

@Composable
private fun TimelineXAxis(
    yAxisWidth: Dp,
) {
    val labels = listOf("0時", "6時", "12時", "18時", "24時")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Spacer(modifier = Modifier.width(yAxisWidth))
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            labels.forEach { label ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .height(6.dp)
                            .width(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestionCard(
    stats: SuggestionDailyStats,
    onClick: () -> Unit = {},
) {
    SectionCard(
        title = "提案との付き合い方",
        description = null,
        onClick = onClick,
    ) {
        val total = stats.totalShown
        if (total == 0) {
            Text("今日は提案はありません。")
            return@SectionCard
        }

        val skipRate = if (total > 0) stats.skippedCount.toFloat() / total else 0f
        val skipPercent = (skipRate * 100).toInt()

        Column {
            // 上段：数字
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("提案表示", style = MaterialTheme.typography.bodySmall)
                    Text("$total 回", style = MaterialTheme.typography.bodyMedium)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("見送った（あとで／閉じる）", style = MaterialTheme.typography.bodySmall)
                    Text("$skipPercent%", style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(Modifier.height(8.dp))

            // 下段：割合バー
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(999.dp),
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(skipRate.coerceIn(0f, 1f))
                        .height(8.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(999.dp),
                        )
                )
            }
        }
    }
}

@Composable
private fun PeakTimeCard(
    timeBuckets: List<TimeBucketStats>,
    onClick: () -> Unit = {},
) {
    SectionCard(
        title = "時間を使いがちな時間帯",
        description = "対象アプリをよく使っている時間帯の一覧です。",
        onClick = onClick,
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

// サマリー表

@Composable
private fun PeriodSummarySection(
    title: String,
    rows: List<Pair<String, String>>,
) {
    SectionCard(
        title = title,
        description = null,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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

private fun buildYesterdaySummaryRows(today: DailyStats): List<Pair<String, String>> {
    // TODO: 本来は「昨日」の別 DailyStats を使う
    return listOf(
        "合計利用時間" to formatDuration(today.totalUsageMillis),
        "セッション数" to today.sessionCount.toString(),
        "平均セッション長" to formatDuration(today.averageSessionDurationMillis),
        "提案表示回数" to (today.suggestionStats?.totalShown?.toString() ?: "0"),
    )
}

private fun buildLast7DaysSummaryRows(today: DailyStats): List<Pair<String, String>> {
    // TODO: 本来は過去7日分を集計して 1日平均などを出す
    return listOf(
        "1日あたり平均利用時間" to formatDuration(today.totalUsageMillis),
        "1日あたりセッション数" to today.sessionCount.toString(),
        "提案を見送った割合" to (
                today.suggestionStats?.let { s ->
                    if (s.totalShown > 0) "${(s.skippedCount * 100 / s.totalShown)}%" else "-"
                } ?: "-"
                ),
    )
}

// ヘルパー

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

