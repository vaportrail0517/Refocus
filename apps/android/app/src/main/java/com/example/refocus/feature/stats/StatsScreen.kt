package com.example.refocus.feature.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.refocus.core.model.DailyStats

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
            // ひとまず Today 固定．将来 Last7Days / Last30Days を実装するときに拡張する．
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
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.todayStats == null -> {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("まだ統計データがありません．")
                    Spacer(Modifier.padding(top = 8.dp))
                    Text(
                        text = "対象アプリの使用中にオーバーレイを表示すると，ここに今日の振り返りが表示されます．",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            else -> {
                StatsContent(
                    stats = uiState.todayStats,
                    appLabelByPackage = uiState.appLabelByPackage,
                    modifier =
                        Modifier
                            .padding(innerPadding)
                            .padding(horizontal = 16.dp),
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
    appLabelByPackage: Map<String, String>,
    modifier: Modifier = Modifier,
    onOpenHistory: () -> Unit,
    onOpenSection: (StatsDetailSection) -> Unit = {},
) {
    LazyColumn(
        modifier =
            modifier
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

        item {
            SummaryCard(
                stats = stats,
                onClick = { onOpenSection(StatsDetailSection.UsageSummary) },
            )
        }

        if (stats.appUsageStats.isNotEmpty()) {
            item {
                AppUsageCard(
                    apps = stats.appUsageStats,
                    appLabelByPackage = appLabelByPackage,
                    onClick = { onOpenSection(StatsDetailSection.AppUsage) },
                )
            }
        }

        if (stats.timeBuckets.isNotEmpty()) {
            item {
                TimelineCard(
                    timeBuckets = stats.timeBuckets,
                    onClick = { onOpenSection(StatsDetailSection.Timeline) },
                )
            }
            // item {
            //     PeakTimeCard(
            //         timeBuckets = stats.timeBuckets,
            //         onClick = { onOpenSection(StatsDetailSection.PeakTime) },
            //     )
            // }
        }

        // stats.suggestionStats?.let { suggestion ->
        //     item {
        //         SuggestionCard(
        //             stats = suggestion,
        //             onClick = { onOpenSection(StatsDetailSection.Suggestions) },
        //         )
        //     }
        // }

        item {
            PeriodSummarySection(
                title = "昨日のまとめ（ダミー）",
                rows = buildYesterdaySummaryRows(stats),
            )
        }

        item {
            PeriodSummarySection(
                title = "過去7日間のまとめ（ダミー）",
                rows = buildLast7DaysSummaryRows(stats),
            )
        }

        // item {
        //     Button(
        //         onClick = onOpenHistory,
        //         modifier = Modifier.fillMaxWidth(),
        //     ) {
        //         Text("セッション履歴を詳しく見る")
        //     }
        // }
    }
}
