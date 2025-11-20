package com.example.refocus.feature.history

import android.app.Application
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
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun SessionHistoryScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val viewModel: SessionHistoryViewModel = viewModel(
        factory = SessionHistoryViewModelFactory(app)
    )
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "セッション履歴",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (uiState.isLoading) {
            Text(
                text = "読み込み中...",
                style = MaterialTheme.typography.bodyMedium
            )
            return@Column
        }

        if (uiState.sessions.isEmpty()) {
            Text(
                text = "まだセッション履歴がありません。",
                style = MaterialTheme.typography.bodyMedium
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(uiState.sessions) { session ->
                SessionItem(session)
            }
        }
    }
}

@Composable
private fun SessionItem(
    session: SessionHistoryViewModel.SessionUiModel
) {
    // ★ このカードの「中断履歴を開いているかどうか」を覚えておくローカル状態
    var expanded by remember { mutableStateOf(false) }
    Card(
        // ★ Card 全体をタップ可能にして、タップで展開状態をトグル
        onClick = {
            if (session.pauseResumeEvents.isNotEmpty()) {
                expanded = !expanded
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // アプリ名 + パッケージ名
            Text(
                text = session.appName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = session.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            // 開始・終了
            Text(
                text = "開始: ${session.startedText}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "終了: ${session.endedText}",
                style = MaterialTheme.typography.bodySmall
            )
            // 状態 + duration
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val statusText = when (session.status) {
                    SessionHistoryViewModel.SessionStatus.RUNNING -> "実行中"
                    SessionHistoryViewModel.SessionStatus.GRACE   -> "一時離脱中（猶予）"
                    SessionHistoryViewModel.SessionStatus.FINISHED -> "終了"
                }
                val statusColor = when (session.status) {
                    SessionHistoryViewModel.SessionStatus.RUNNING ->
                        MaterialTheme.colorScheme.primary
                    SessionHistoryViewModel.SessionStatus.GRACE ->
                        MaterialTheme.colorScheme.tertiary
                    SessionHistoryViewModel.SessionStatus.FINISHED ->
                        MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor
                )
                if (session.durationText.isNotEmpty()) {
                    Text(
                        text = session.durationText,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            // ★ 中断履歴がある & expanded=true のときだけ表示する
            if (session.pauseResumeEvents.isNotEmpty() && expanded) {
                Spacer(modifier = Modifier.height(4.dp))
                Divider()
                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "中断履歴（タップで非表示）",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    session.pauseResumeEvents.forEach { ev ->
                        if (ev.resumedAtText != null) {
                            Text(
                                text = "中断: ${ev.pausedAtText}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "再開: ${ev.resumedAtText}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            // 未再開の中断
                            Text(
                                text = "中断: ${ev.pausedAtText}（未再開）",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                }
            } else if (session.pauseResumeEvents.isNotEmpty()) {
                // ★ 畳んでいるときに「タップで中断履歴を表示」のヒントを出してもよい
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "タップして中断履歴を表示",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
