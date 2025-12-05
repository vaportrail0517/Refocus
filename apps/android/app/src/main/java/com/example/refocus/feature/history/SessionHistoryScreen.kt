package com.example.refocus.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.refocus.core.model.SessionStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionHistoryScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
) {
    val viewModel: SessionHistoryViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "セッション履歴"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "戻る"
                        )
                    }
                },
                windowInsets = WindowInsets(0.dp),
            )
        },
        contentWindowInsets = WindowInsets(0.dp),
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
}

@Composable
private fun SessionItem(
    session: SessionHistoryViewModel.SessionUiModel
) {
    // ★ このカードの「中断履歴を開いているかどうか」を覚えておくローカル状態
    var expanded by remember { mutableStateOf(false) }
    Card(
        // Card 全体をタップ可能にして、タップで展開状態をトグル
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
                    SessionStatus.RUNNING -> "実行中"
                    SessionStatus.GRACE -> "一時離脱中（猶予）"
                    SessionStatus.FINISHED -> "終了"
                }
                val statusColor = when (session.status) {
                    SessionStatus.RUNNING ->
                        MaterialTheme.colorScheme.primary

                    SessionStatus.GRACE ->
                        MaterialTheme.colorScheme.tertiary

                    SessionStatus.FINISHED ->
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
                HorizontalDivider()
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
