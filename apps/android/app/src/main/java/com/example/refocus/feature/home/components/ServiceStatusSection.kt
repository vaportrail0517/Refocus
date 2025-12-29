package com.example.refocus.feature.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp

@Composable
internal fun ServiceStatusSection(
    isRunning: Boolean,
    hasCorePermissions: Boolean,
    onToggleRunning: (Boolean) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "動作状況",
            style = MaterialTheme.typography.titleMedium,
        )

        ServiceStatusCard(
            isRunning = isRunning,
            hasCorePermissions = hasCorePermissions,
            onToggleRunning = onToggleRunning,
        )
    }
}

@Composable
private fun ServiceStatusCard(
    isRunning: Boolean,
    hasCorePermissions: Boolean,
    onToggleRunning: (Boolean) -> Unit,
) {
    val ui =
        when {
            !hasCorePermissions ->
                StatusUi(
                    title = "停止中",
                    description = "権限が不足しているため開始できません．",
                    actionText = "開始する",
                    actionEnabled = false,
                    action = {},
                    icon = Icons.Filled.PlayCircle,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    alpha = 0.55f,
                )
            isRunning ->
                StatusUi(
                    title = "動作中",
                    description = "対象アプリの前面でタイマーと提案が表示されます．",
                    actionText = "停止する",
                    actionEnabled = true,
                    action = { onToggleRunning(false) },
                    icon = Icons.Filled.PauseCircle,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    alpha = 1.0f,
                )
            else ->
                StatusUi(
                    title = "停止中",
                    description = "開始すると対象アプリの前面でタイマーを表示します．",
                    actionText = "開始する",
                    actionEnabled = true,
                    action = { onToggleRunning(true) },
                    icon = Icons.Filled.PlayCircle,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    alpha = 1.0f,
                )
        }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .alpha(ui.alpha),
        shape = RoundedCornerShape(20.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = ui.containerColor,
                contentColor = ui.contentColor,
            ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = ui.icon,
                contentDescription = ui.title,
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = ui.title,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = ui.description,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            FilledTonalButton(
                onClick = ui.action,
                enabled = ui.actionEnabled,
            ) {
                Text(ui.actionText)
            }
        }
    }
}

private data class StatusUi(
    val title: String,
    val description: String,
    val actionText: String,
    val actionEnabled: Boolean,
    val action: () -> Unit,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val containerColor: androidx.compose.ui.graphics.Color,
    val contentColor: androidx.compose.ui.graphics.Color,
    val alpha: Float,
)
