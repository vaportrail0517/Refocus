package com.example.refocus.feature.history

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.clickable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

@Composable
fun SessionHistoryScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application

    val viewModel: SessionHistoryViewModel = viewModel(
        factory = SessionHistoryViewModelFactory(app)
    )

    val sessions by viewModel.sessions.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "セッション履歴",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "まだ記録されたセッションがありません。",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(sessions) { item ->
                    SessionRow(item)
                    Divider()
                }
            }
        }
    }
}

@Composable
private fun SessionRow(
    session: SessionHistoryViewModel.SessionUiModel
) {
    val defaultKey = "${session.packageName}_${session.startedAtText}"
    var expanded by rememberSaveable(defaultKey) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { expanded = !expanded }
    ) {
        Text(
            text = session.appLabel,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = session.packageName,
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "開始: ${session.startedAtText}",
            style = MaterialTheme.typography.bodySmall
        )
        if (expanded && session.pauses.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            session.pauses.forEach { ev ->
                if (!ev.resumedAtText.isNullOrEmpty()){
                    Text(
                        text = "  ↳ 中断: ${ev.pausedAtText}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "  ↳ 再開: ${ev.resumedAtText}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
        Text(
            text = "終了: ${session.endedAtText}",
            style = MaterialTheme.typography.bodySmall
        )
        if (session.status == SessionHistoryViewModel.SessionStatus.FINISHED &&
            session.durationText.isNotEmpty()
        ) {
            Text(
                text = "継続時間: ${session.durationText}",
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        val statusText = when (session.status) {
            SessionHistoryViewModel.SessionStatus.RUNNING -> "状態: 計測中"
            SessionHistoryViewModel.SessionStatus.GRACE   -> "状態: 停止猶予中"
            SessionHistoryViewModel.SessionStatus.FINISHED-> "状態: 終了"
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
    }
}
