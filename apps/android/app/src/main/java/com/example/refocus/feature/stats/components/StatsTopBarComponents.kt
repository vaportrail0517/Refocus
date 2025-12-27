package com.example.refocus.feature.stats

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StatsTopBar(
    dateLabel: String,
    statsRange: StatsRange,
    onRangeChange: (StatsRange) -> Unit,
    onOpenHistory: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text = "統計",
                style = MaterialTheme.typography.titleLarge,
            )
        },
        actions = {
            IconButton(onClick = onOpenHistory) {
                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = "セッション履歴",
                )
            }
        },
        windowInsets = WindowInsets(0.dp),
    )
}

@Composable
internal fun RangeToggle(
    selected: StatsRange,
    onRangeChange: (StatsRange) -> Unit,
) {
    // ひとまず Today のみ機能させる．将来 Last7Days / Last30Days を追加しやすいようにしておく．
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "今日",
            style = MaterialTheme.typography.bodyMedium,
        )
        // 週・月は今はダミー or 無効状態にしておいてもよい
        // Text("週", modifier = Modifier.alpha(0.4f))
        // Text("月", modifier = Modifier.alpha(0.4f))
    }
}
