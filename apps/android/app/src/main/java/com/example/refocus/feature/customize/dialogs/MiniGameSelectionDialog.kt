package com.example.refocus.feature.customize.dialogs

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.refocus.core.model.MiniGameKind
import com.example.refocus.ui.components.SettingRow
import com.example.refocus.ui.components.SettingsBaseDialog
import com.example.refocus.ui.minigame.catalog.MiniGameRegistry

@Composable
fun MiniGameSelectionDialog(
    currentDisabledKinds: Set<MiniGameKind>,
    onConfirm: (Set<MiniGameKind>) -> Unit,
    onDismiss: () -> Unit,
) {
    val descriptors =
        remember {
            MiniGameRegistry.descriptors
        }

    var disabledKinds by remember(currentDisabledKinds) { mutableStateOf(currentDisabledKinds) }

    val totalGames = descriptors.size
    val enabledCount = descriptors.count { desc -> !disabledKinds.contains(desc.kind) }

    SettingsBaseDialog(
        title = "提案に出すミニゲーム",
        description = "オンのミニゲームのみ，提案時にランダムで表示されます．",
        scrollableBody = true,
        onConfirm = { onConfirm(disabledKinds) },
        onDismiss = onDismiss,
    ) {
        Text(
            text = "有効：$enabledCount/$totalGames",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))

        descriptors.forEach { desc ->
            val enabled = !disabledKinds.contains(desc.kind)

            fun updateEnabled(isEnabled: Boolean) {
                disabledKinds =
                    if (isEnabled) {
                        disabledKinds - desc.kind
                    } else {
                        disabledKinds + desc.kind
                    }
            }

            SettingRow(
                title = desc.title,
                subtitle = desc.description,
                trailing = {
                    Switch(
                        checked = enabled,
                        onCheckedChange = { checked -> updateEnabled(checked) },
                    )
                },
                onClick = { updateEnabled(!enabled) },
            )
        }

        if (totalGames > 0 && enabledCount == 0) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "全て無効のため，ミニゲームは表示されません．",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
