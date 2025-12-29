package com.example.refocus.feature.home.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.refocus.core.model.DailyStats
import com.example.refocus.core.util.formatDurationMilliSeconds
import com.example.refocus.feature.stats.StatsDetailSection

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FocusSection(
    stats: DailyStats?,
    appLabelByPackage: Map<String, String>,
    onOpenSection: (StatsDetailSection) -> Unit,
) {
    val focusItems = remember(stats, appLabelByPackage) { buildFocusItems(stats, appLabelByPackage) }
    if (focusItems.isEmpty()) return

    val pagerState =
        rememberPagerState(
            initialPage = 0,
            pageCount = { focusItems.size },
        )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "サマリー",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
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
}

private data class FocusItem(
    val title: String,
    val value: String,
    val subtitle: String?,
    val section: StatsDetailSection,
)

private fun buildFocusItems(
    stats: DailyStats?,
    appLabelByPackage: Map<String, String>,
): List<FocusItem> {
    val items = mutableListOf<FocusItem>()

    if (stats == null) return items

    items +=
        FocusItem(
            title = "今日の合計利用時間",
            value = formatDurationMilliSeconds(stats.totalUsageMillis),
            subtitle = "${stats.sessionCount} セッション",
            section = StatsDetailSection.UsageSummary,
        )

    stats.appUsageStats.maxByOrNull { it.totalUsageMillis }?.let { topApp ->
        val label = appLabelByPackage[topApp.packageName] ?: topApp.packageName
        items +=
            FocusItem(
                title = "よく使ったアプリ",
                value = label,
                subtitle = formatDurationMilliSeconds(topApp.totalUsageMillis),
                section = StatsDetailSection.AppUsage,
            )
    }

    stats.suggestionStats?.let { s ->
        items +=
            FocusItem(
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
        modifier =
            Modifier
                .fillMaxWidth()
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
                modifier =
                    Modifier
                        .size(8.dp)
                        .background(
                            color =
                                if (selected) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f)
                                },
                            shape = CircleShape,
                        ),
            )
        }
    }
}
