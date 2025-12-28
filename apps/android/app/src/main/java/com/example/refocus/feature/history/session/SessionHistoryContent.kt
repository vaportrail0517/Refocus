package com.example.refocus.feature.history.session

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
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.refocus.core.model.SessionStatus

@Composable
fun SessionHistoryContent(
    uiState: SessionHistoryViewModel.UiState,
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

        if (uiState.sessions.isEmpty()) {
            Text(
                text = "まだセッション履歴がありません。",
                style = MaterialTheme.typography.bodyMedium,
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(uiState.sessions) { session ->
                SessionItem(session)
            }
        }
    }
}

@Composable
private fun SessionItem(session: SessionHistoryViewModel.SessionUiModel) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        onClick = {
            if (session.pauseResumeEvents.isNotEmpty()) {
                expanded = !expanded
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = session.appName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = session.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "開始: ${session.startedText}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "終了: ${session.endedText}",
                style = MaterialTheme.typography.bodySmall,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val statusText =
                    when (session.status) {
                        SessionStatus.RUNNING -> "実行中"
                        SessionStatus.GRACE -> "一時離脱中（猶予）"
                        SessionStatus.FINISHED -> "終了"
                    }
                val statusColor =
                    when (session.status) {
                        SessionStatus.RUNNING -> MaterialTheme.colorScheme.primary
                        SessionStatus.GRACE -> MaterialTheme.colorScheme.tertiary
                        SessionStatus.FINISHED -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor,
                )
                if (session.durationText.isNotEmpty()) {
                    Text(
                        text = session.durationText,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            if (session.pauseResumeEvents.isNotEmpty() && expanded) {
                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "中断履歴（タップで非表示）",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    session.pauseResumeEvents.forEach { ev ->
                        if (ev.resumedAtText != null) {
                            Text(
                                text = "中断: ${ev.pausedAtText}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                text = "再開: ${ev.resumedAtText}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        } else {
                            Text(
                                text = "中断: ${ev.pausedAtText}（未再開）",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                }
            } else if (session.pauseResumeEvents.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "タップして中断履歴を表示",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
