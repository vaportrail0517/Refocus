package com.example.refocus.feature.home.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.refocus.feature.home.ServiceStartFailureUiModel

@Composable
internal fun PermissionWarningCard(onClick: () -> Unit) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
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
                    text =
                        "連続使用時間を計測するには「使用状況へのアクセス」と「他のアプリの上に表示」の権限が必要です。" +
                            "タップして権限設定へ移動します。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
internal fun NotificationWarningCard(onClick: () -> Unit) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
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

@Composable
internal fun ServiceStartFailureWarningCard(
    failure: ServiceStartFailureUiModel,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = "サービスの起動に失敗しました",
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "サービスの起動に失敗しました",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = "最終失敗: ${failure.occurredAtText}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Text(
                text = "詳細: ${failure.summaryShort}",
                style = MaterialTheme.typography.bodySmall,
            )

            if (!failure.source.isNullOrBlank()) {
                Text(
                    text = "source: ${failure.source}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onOpenSettings) {
                    Text("設定")
                }
                TextButton(onClick = onDismiss) {
                    Text("消す")
                }
            }
        }
    }
}
