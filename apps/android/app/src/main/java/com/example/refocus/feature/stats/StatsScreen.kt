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
import com.example.refocus.core.util.formatDurationMilliSeconds
import com.example.refocus.ui.components.SectionCard

/**
 * 統計詳細画面の種類。
 *
 * HomeScreen など他画面からも参照されるので、この enum の値は既存のものを維持しつつ、
 * 新しく Monitoring を追加する。
 */
enum class StatsDetailSection {
    UsageSummary,   // 今日のサマリー
    AppUsage,       // アプリ別
    Timeline,       // 1日の流れ
    Suggestions,    // 提案の履歴
    PeakTime,       // 時間を使いがちな時間帯
    Monitoring,     // Refocus の動作状況
}

@Composable
fun StatsRoute(
    viewModel: StatsViewModel = hiltViewModel(),
    onOpenHistory: () -> Unit = {},
    onOpenDetail: (StatsDetailSection) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    StatsScreen(
        uiState = uiState,
        onOpenHistory = onOpenHistory,
        onRangeChange = { _ ->
            // ひとまず Today 固定。将来 Last7Days / Last30Days を実装するときに拡張する。
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.todayStats == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("まだ統計データがありません。")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "対象アプリの使用中にオーバーレイを表示すると、ここに今日の振り返りが表示されます。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            else -> {
                StatsContent(
                    stats = uiState.todayStats,
                    modifier = Modifier
                        .padding(innerPadding)
                        .padding(16.dp),
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
        // Refocus の動作状況（監視時間）
        // item {
        //     MonitoringSummaryCard(
        //         stats = stats,
        //         onClick = { onOpenSection(StatsDetailSection.Monitoring) },
        //     )
        // }

        // 今日のサマリー（対象アプリの合計時間・セッション）
        item {
            SummaryCard(
                stats = stats,
                onClick = { onOpenSection(StatsDetailSection.UsageSummary) },
            )
        }

        // アプリ別の使い方
        if (stats.appUsageStats.isNotEmpty()) {
            item {
                AppUsageCard(
                    apps = stats.appUsageStats,
                    onClick = { onOpenSection(StatsDetailSection.AppUsage) },
                )
            }
        }

        // 1日の流れ（タイムライン）
        if (stats.timeBuckets.isNotEmpty()) {
            item {
                TimelineCard(
                    timeBuckets = stats.timeBuckets,
                    onClick = { onOpenSection(StatsDetailSection.Timeline) },
                )
            }
//            item {
//                PeakTimeCard(
//                    timeBuckets = stats.timeBuckets,
//                    onClick = { onOpenSection(StatsDetailSection.PeakTime) },
//                )
//            }
        }

        // 提案との付き合い方
//        stats.suggestionStats?.let { suggestion ->
//            item {
//                SuggestionCard(
//                    stats = suggestion,
//                    onClick = { onOpenSection(StatsDetailSection.Suggestions) },
//                )
//            }
//        }

        // 昨日のサマリー（現状は「今日の値を使ったダミー」。実装時に正しく差分集計する）
        item {
            PeriodSummarySection(
                title = "昨日のまとめ（ダミー）",
                rows = buildYesterdaySummaryRows(stats),
            )
        }

        // 過去7日間のサマリー（こちらも現状はダミー）
        item {
            PeriodSummarySection(
                title = "過去7日間のまとめ（ダミー）",
                rows = buildLast7DaysSummaryRows(stats),
            )
        }

        // 履歴画面への導線
//        item {
//            Button(
//                onClick = onOpenHistory,
//                modifier = Modifier.fillMaxWidth(),
//            ) {
//                Text("セッション履歴を詳しく見る")
//            }
//        }
    }
}

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
            Text(
                text = "統計",
                style = MaterialTheme.typography.titleLarge,
            )
        },
        actions = {
            IconButton(onClick = onOpenHistory) {
                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = "セッション履歴",
                )
            }
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
        Text(
            text = "今日",
            style = MaterialTheme.typography.bodyMedium,
        )
        // 週・月は今はダミー or 無効状態にしておいてもよい
        // Text("週", modifier = Modifier.alpha(0.4f))
        // Text("月", modifier = Modifier.alpha(0.4f))
    }
}

// --- カード群 ---

@Composable
private fun MonitoringSummaryCard(
    stats: DailyStats,
    onClick: () -> Unit = {},
) {
    SectionCard(
        title = "Refocus の動作状況",
        description = "今日どれくらい Refocus が対象アプリを見守っていたかの概要です。",
        onClick = onClick,
    ) {
        if (stats.monitoringTotalMinutes == 0) {
            Text(
                text = "今日はまだ Refocus が動作していません。\n",
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
                "平均セッション" to formatDurationMilliSeconds(stats.averageSessionDurationMillis),
                "最長セッション" to formatDurationMilliSeconds(stats.longestSessionDurationMillis),
                "長めのセッション" to "${stats.longSessionCount} 回",
                "とても長いセッション" to "${stats.veryLongSessionCount} 回",
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
private fun AppUsageCard(
    apps: List<AppUsageStats>,
    onClick: () -> Unit = {},
) {
    SectionCard(
        title = "アプリ別の使い方",
        description = "よく使っているアプリの一覧です。",
        onClick = onClick,
    ) {
        if (apps.isEmpty()) {
            Text("まだデータがありません。")
            return@SectionCard
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val topApps = apps
                .sortedByDescending { it.totalUsageMillis }
                .take(5)

            topApps.forEach { app ->
                AppUsageRow(app)
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

@Composable
private fun TimelineCard(
    timeBuckets: List<TimeBucketStats>,
    onClick: () -> Unit = {},
) {
    SectionCard(
        title = "タイムライン",
        description = "どの時間帯に対象アプリを使っていたかの目安です。",
        onClick = onClick,
    ) {
        TimelineChart(timeBuckets = timeBuckets)
    }
}

@Composable
private fun TimelineChart(
    timeBuckets: List<TimeBucketStats>,
) {
    // 縦軸のベースは「対象アプリの利用時間（分）」で見る
    val maxTargetMinutes = (timeBuckets.maxOfOrNull { it.targetUsageMinutes } ?: 0)
        .coerceAtLeast(1)
    // 縦軸の最大値を「10分刻み」で丸める（例: 7分 -> 10, 18分 -> 20, 35分 -> 40）
    val axisMaxMinutes = ((maxTargetMinutes + 9) / 10) * 10
    val yLabels = listOf(
        axisMaxMinutes,
        (axisMaxMinutes * 2) / 3,
        axisMaxMinutes / 3,
        0,
    )
    val yAxisWidth = 40.dp

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        // 上段: Y 軸 + バー
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
        ) {
            // Y 軸ラベル
            Column(
                modifier = Modifier
                    .width(yAxisWidth)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                yLabels.forEach { label ->
                    Text(
                        text = "${label}分",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // バー本体
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                timeBuckets.forEach { bucket ->
                    val usageMinutes = bucket.targetUsageMinutes
                    val usageHeightFraction =
                        (usageMinutes.toFloat() / axisMaxMinutes.toFloat()).coerceIn(0f, 1f)

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        // 背景: Refocus 監視状態
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth()
                                .background(
                                    color = if (bucket.monitoringMinutes > 0) {
                                        // 監視していた時間帯
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                    } else {
                                        // Refocus が動いていなかった時間帯（未知）
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    shape = RoundedCornerShape(2.dp),
                                )
                        )

                        // 前景: 対象アプリの利用時間バー
                        if (usageMinutes > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(usageHeightFraction)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
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
            .filter { it.targetUsageMinutes > 0 }
            .sortedByDescending { it.targetUsageMinutes }
            .take(3)

        if (topBuckets.isEmpty()) {
            Text("まだデータがありません。")
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

private fun formatBucketRange(
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
private fun SuggestionCard(
    stats: SuggestionDailyStats,
    onClick: () -> Unit = {},
) {
    SectionCard(
        title = "提案との付き合い方",
        description = "今日、提案がどれくらい表示され、どのように扱ったかの概要です。",
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

// --- サマリー表（昨日 / 過去7日） ---

@Composable
private fun PeriodSummarySection(
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

private fun buildYesterdaySummaryRows(today: DailyStats): List<Pair<String, String>> {
    // TODO: 本来は「昨日の DailyStats」を受け取り、今日との差分を表示する。
    // ここではひとまず今日の値を使ったダミーとしておく。
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

private fun buildLast7DaysSummaryRows(today: DailyStats): List<Pair<String, String>> {
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
