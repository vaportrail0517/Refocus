package com.example.refocus.feature.history.timeline

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Comparator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineHistoryContent(
    uiState: TimelineHistoryViewModel.UiState,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onToggleCategory: (TimelineCategory) -> Unit,
    onSelectAllCategories: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val pickerState =
            rememberDatePickerState(
                initialSelectedDateMillis = uiState.selectedDateUtcMillis,
            )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val millis = pickerState.selectedDateMillis
                        if (millis != null) {
                            val date =
                                Instant
                                    .ofEpochMilli(millis)
                                    .atZone(ZoneOffset.UTC)
                                    .toLocalDate()
                            onSelectDate(date)
                        }
                        showDatePicker = false
                    },
                ) {
                    Text(text = "OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(text = "キャンセル")
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TimelineFilterCard(
            uiState = uiState,
            onPreviousDay = onPreviousDay,
            onNextDay = onNextDay,
            onOpenDatePicker = { showDatePicker = true },
            onToggleCategory = onToggleCategory,
            onSelectAllCategories = onSelectAllCategories,
        )

        if (uiState.isLoading) {
            Text(
                text = "読み込み中...",
                style = MaterialTheme.typography.bodyMedium,
            )
            return@Column
        }

        if (uiState.rows.isEmpty()) {
            Text(
                text = "イベントがありません。",
                style = MaterialTheme.typography.bodyMedium,
            )
            return@Column
        }

        if (uiState.rangeText.isNotBlank()) {
            Text(
                text = uiState.rangeText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (uiState.countText.isNotBlank()) {
            Text(
                text = uiState.countText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // 折りたたみ状態は，日付やフィルタが変わったらリセットする
        val filterKey =
            if (uiState.isAllCategoriesSelected) {
                "ALL"
            } else {
                uiState.categories
                    .asSequence()
                    .filter { it.selected }
                    .map { it.category.name }
                    .sorted()
                    .joinToString(separator = ",")
            }

        var collapsedHours by remember(uiState.selectedDate, filterKey) { mutableStateOf(setOf<Int>()) }

        val grouped =
            remember(uiState.rows) {
                // 上に行くほど新しい（大きい）時刻になるように，時台グループも降順に並べる
                uiState.rows.groupBy { it.hour }.toSortedMap(Comparator.reverseOrder())
            }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
        ) {
            grouped.forEach { (hour, rows) ->
                val isCollapsed = collapsedHours.contains(hour)

                item(key = "hour-$hour") {
                    TimelineHourHeader(
                        hour = hour,
                        eventCount = rows.size,
                        collapsed = isCollapsed,
                        onToggle = {
                            collapsedHours =
                                collapsedHours.toMutableSet().apply {
                                    if (contains(hour)) remove(hour) else add(hour)
                                }
                        },
                    )
                }

                if (!isCollapsed) {
                    items(
                        items = rows,
                        key = { row ->
                            row.id ?: "${row.hour}-${row.timeText}-${row.title}"
                        },
                    ) { row ->
                        TimelineRowItem(row)
                        HorizontalDivider()
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun TimelineFilterCard(
    uiState: TimelineHistoryViewModel.UiState,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onOpenDatePicker: () -> Unit,
    onToggleCategory: (TimelineCategory) -> Unit,
    onSelectAllCategories: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "日付",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    IconButton(onClick = onPreviousDay) {
                        Icon(
                            imageVector = Icons.Filled.ChevronLeft,
                            contentDescription = "前の日",
                        )
                    }
                    TextButton(onClick = onOpenDatePicker) {
                        Text(text = uiState.selectedDateText)
                    }
                    IconButton(
                        onClick = onNextDay,
                        enabled = uiState.canGoNext,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = "次の日",
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "種類",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                val scrollState = rememberScrollState()
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(scrollState),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = uiState.isAllCategoriesSelected,
                        onClick = onSelectAllCategories,
                        label = { Text(text = "すべて") },
                    )
                    uiState.categories.forEach { c ->
                        FilterChip(
                            selected = c.selected,
                            onClick = { onToggleCategory(c.category) },
                            label = { Text(text = c.label) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineHourHeader(
    hour: Int,
    eventCount: Int,
    collapsed: Boolean,
    onToggle: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .clickable(onClick = onToggle),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${hour}時台",
                style = MaterialTheme.typography.labelLarge,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "${eventCount}件",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    imageVector = if (collapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                    contentDescription = if (collapsed) "展開" else "折りたたむ",
                )
            }
        }
    }
}

@Composable
private fun TimelineRowItem(row: TimelineHistoryViewModel.RowUiModel) {
    ListItem(
        headlineContent = {
            Text(
                text = row.title,
                style = MaterialTheme.typography.titleSmall,
            )
        },
        overlineContent = {
            Text(
                text = row.category.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        supportingContent = {
            if (!row.detail.isNullOrBlank()) {
                Text(
                    text = row.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        trailingContent = {
            Text(
                text = row.timeText,
                style = MaterialTheme.typography.labelMedium,
            )
        },
    )
}
