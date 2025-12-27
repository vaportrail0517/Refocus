package com.example.refocus.feature.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.refocus.core.model.DailyStats
import com.example.refocus.core.util.displayLength
import com.example.refocus.core.util.formatDurationMilliSeconds
import com.example.refocus.feature.stats.StatsDetailSection

@Composable
internal fun HomeContent(
    stats: DailyStats?,
    appLabelByPackage: Map<String, String>,
    targetApps: List<HomeViewModel.TargetAppUiModel>,
    hasCorePermissions: Boolean,
    showNotificationWarning: Boolean,
    innerPadding: PaddingValues,
    onOpenStatsDetail: (StatsDetailSection) -> Unit,
    onOpenPermissionFixFlow: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenAppSelect: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            if (!hasCorePermissions || showNotificationWarning) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (!hasCorePermissions) {
                        PermissionWarningCard(
                            onClick = onOpenPermissionFixFlow,
                        )
                    }
                    if (showNotificationWarning) {
                        NotificationWarningCard(
                            onClick = onOpenNotificationSettings,
                        )
                    }
                }
            }
        }

        item {
            FocusSection(
                stats = stats,
                appLabelByPackage = appLabelByPackage,
                onOpenSection = onOpenStatsDetail,
            )
        }

        item {
            TargetAppsSection(
                apps = targetApps,
                onAddClick = onOpenAppSelect,
                onAppClick = { /* 必要があればここで何かする */ },
            )
        }
    }
}


@Composable
internal fun PermissionWarningCard(
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = "権限が不足しています",
            )
            Column {
                Text(
                    text = "権限が不足しています",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "連続使用時間を計測するには「使用状況へのアクセス」と「他のアプリの上に表示」の権限が必要です。タップして権限設定へ移動します。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
internal fun NotificationWarningCard(
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = "通知が無効です",
            )
            Column {
                Text(
                    text = "通知が無効です",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "通知が無効なので常駐通知は表示されません．サービスは動作します．タップして通知設定へ移動します．",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}


/*
 * フォーカス
 */

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FocusSection(
    stats: DailyStats?,
    appLabelByPackage: Map<String, String>,
    onOpenSection: (StatsDetailSection) -> Unit,
) {
    // フォーカスに載せるカード一覧
    val focusItems = buildFocusItems(stats, appLabelByPackage)
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

private fun buildFocusItems(
    stats: DailyStats?,
    appLabelByPackage: Map<String, String>,
): List<FocusItem> {
    val items = mutableListOf<FocusItem>()

    if (stats == null) return items

    // カード1: 今日の合計利用時間
    items += FocusItem(
        title = "今日の合計利用時間",
        value = formatDurationMilliSeconds(stats.totalUsageMillis),
        subtitle = "${stats.sessionCount} セッション",
        section = StatsDetailSection.UsageSummary,
    )

    // カード2: 一番使っているアプリ
    stats.appUsageStats.maxByOrNull { it.totalUsageMillis }?.let { topApp ->
        val label = appLabelByPackage[topApp.packageName] ?: topApp.packageName
        items += FocusItem(
            title = "よく使ったアプリ",
            value = label,
            subtitle = formatDurationMilliSeconds(topApp.totalUsageMillis),
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
//private fun formatDurationShort(durationMillis: Long): String {
//    val totalSeconds = durationMillis / 1000
//    val minutes = totalSeconds / 60
//    val hours = minutes / 60
//    val remMinutes = minutes % 60
//    return if (hours > 0) {
//        String.format("%d時間%02d分", hours, remMinutes)
//    } else {
//        String.format("%d分", remMinutes)
//    }
//}


@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TargetAppsSection(
    apps: List<HomeViewModel.TargetAppUiModel>,
    onAddClick: () -> Unit,
    onAppClick: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "対象アプリ",
            style = MaterialTheme.typography.titleMedium,
        )

        TargetAppsGrid(
            apps = apps,
            onAddClick = onAddClick,
            onAppClick = onAppClick,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TargetAppsGrid(
    apps: List<HomeViewModel.TargetAppUiModel>,
    onAddClick: () -> Unit,
    onAppClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val columns = 2
        val horizontalSpacing = 8.dp
        val itemWidth = (maxWidth - horizontalSpacing * (columns - 1)) / columns

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            apps.forEach { app ->
                TargetAppCard(
                    app = app,
                    onClick = { onAppClick(app.packageName) },
                    modifier = Modifier.width(itemWidth)
                )
            }
            AddTargetAppCard(
                onClick = onAddClick,
                modifier = Modifier.width(itemWidth)
            )
        }
    }
}

@Composable
internal fun AddTargetAppCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,  // Grid 側から width(itemWidth) が渡される前提
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.AddCircleOutline,
                contentDescription = "追加",
                modifier = Modifier.size(40.dp) // TargetAppCard のアイコンと揃える
            )

            Text(
                text = "追加",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .weight(1f),
            )
        }
    }
}


@Composable
internal fun TargetAppCard(
    app: HomeViewModel.TargetAppUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLong = app.label.displayLength() > 6.0
    val textStyle = if (isLong) {
        MaterialTheme.typography.bodySmall
    } else {
        MaterialTheme.typography.bodyMedium
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Drawable → Painter 変換を remember でキャッシュ
            val iconPainter = remember(app.packageName, app.icon) {
                app.icon?.let { drawable ->
                    val bitmap = drawable.toBitmap()          // Drawable → Bitmap
                    BitmapPainter(bitmap.asImageBitmap())     // Bitmap → Painter
                }
            }

            if (iconPainter != null) {
                Image(
                    painter = iconPainter,
                    contentDescription = app.label,
                    modifier = Modifier.size(40.dp)
                )
            } else {
                // アイコンが取れなかったときのプレースホルダ
                Box(
                    modifier = Modifier.size(40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "？",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            // 右側にアプリ名
            Text(
                text = app.label,
                style = textStyle,
                maxLines = 2,
                softWrap = true,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .weight(1f),
            )
        }
    }
}
