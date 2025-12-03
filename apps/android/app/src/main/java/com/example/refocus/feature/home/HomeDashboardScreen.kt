package com.example.refocus.feature.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.refocus.core.model.DailyStats
import com.example.refocus.feature.stats.StatsDetailSection
import com.example.refocus.feature.stats.StatsViewModel

@Composable
fun HomeDashboardRoute(
    onOpenHistory: () -> Unit,
    onOpenStatsDetail: (StatsDetailSection) -> Unit = {},
    onOpenPermissionFixFlow: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value

    when {
        uiState.isLoading -> {
            // 簡易なローディングでOK
//            LoadingContent()
        }

        uiState.todayStats == null -> {
//            EmptyContent()
        }

        else -> {
            HomeDashboardContent(
                stats = uiState.todayStats,
                onOpenHistory = onOpenHistory,
                onOpenStatsDetail = onOpenStatsDetail,
            )
        }
    }
}

@Composable
private fun HomeDashboardContent(
    stats: DailyStats,
    onOpenHistory: () -> Unit,
    onOpenStatsDetail: (StatsDetailSection) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ここに「統計画面で使っていた FocusSection を移植」
        FocusSection(
            stats = stats,
            onOpenSection = onOpenStatsDetail,
        )
    }
}

/*
 * フォーカス
 */

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FocusSection(
    stats: DailyStats,
    onOpenSection: (StatsDetailSection) -> Unit,
) {
    // フォーカスに載せるカード一覧
    val focusItems = buildFocusItems(stats)
    if (focusItems.isEmpty()) return

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { focusItems.size },
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 12.dp,
        ) { page ->
            val item = focusItems[page]
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                FocusCard(
                    title = item.title,
                    value = item.value,
                    subtitle = item.subtitle,
                    onClick = { onOpenSection(item.section) },
                )
            }
        }

        PagerIndicator(
            pageCount = focusItems.size,
            currentPage = pagerState.currentPage,
        )
    }
}

private data class FocusItem(
    val title: String,
    val value: String,
    val subtitle: String?,
    val section: StatsDetailSection,
)

private fun buildFocusItems(stats: DailyStats): List<FocusItem> {
    val items = mutableListOf<FocusItem>()

    // カード1: 今日の合計利用時間
    items += FocusItem(
        title = "今日の合計利用時間",
        value = formatDurationShort(stats.totalUsageMillis),
        subtitle = "${stats.sessionCount} セッション",
        section = StatsDetailSection.UsageSummary,
    )

    // カード2: 一番使っているアプリ
    stats.appUsageStats.maxByOrNull { it.totalUsageMillis }?.let { topApp ->
        items += FocusItem(
            title = "よく使ったアプリ",
            value = topApp.packageName, // 後でアプリ名に差し替え可
            subtitle = formatDurationShort(topApp.totalUsageMillis),
            section = StatsDetailSection.AppUsage,
        )
    }

    // カード3: 今日の提案
    stats.suggestionStats?.let { s ->
        items += FocusItem(
            title = "今日の提案",
            value = "${s.totalShown} 回",
            subtitle = "見送った: ${s.skippedCount} 回",
            section = StatsDetailSection.Suggestions,
        )
    }

    return items
}

@Composable
private fun FocusCard(
    title: String,
    value: String,
    subtitle: String?,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()            // 画面幅の 9 割
            .heightIn(min = 120.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun PagerIndicator(
    pageCount: Int,
    currentPage: Int,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val selected = index == currentPage
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f)
                        },
                        shape = CircleShape,
                    )
            )
        }
    }
}

// フォーカス用の短めフォーマット
private fun formatDurationShort(durationMillis: Long): String {
    val totalSeconds = durationMillis / 1000
    val minutes = totalSeconds / 60
    val hours = minutes / 60
    val remMinutes = minutes % 60
    return if (hours > 0) {
        String.format("%d時間%02d分", hours, remMinutes)
    } else {
        String.format("%d分", remMinutes)
    }
}

