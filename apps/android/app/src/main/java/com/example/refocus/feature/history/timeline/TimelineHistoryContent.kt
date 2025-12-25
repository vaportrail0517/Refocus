package com.example.refocus.feature.history.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun TimelineHistoryContent(
    uiState: TimelineHistoryViewModel.UiState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (uiState.isLoading) {
            Text(
                text = "読み込み中...",
                style = MaterialTheme.typography.bodyMedium,
            )
            return@Column
        }

        if (uiState.rows.isEmpty()) {
            Text(
                text = "まだタイムラインイベントがありません。",
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

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(uiState.rows) { row ->
                TimelineRowItem(row)
            }
        }
    }
}

@Composable
private fun TimelineRowItem(
    row: TimelineHistoryViewModel.RowUiModel,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (!row.detail.isNullOrBlank()) {
                Text(
                    text = row.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = row.timeText,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
