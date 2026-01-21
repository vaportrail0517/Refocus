package com.example.refocus.feature.suggestions.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.refocus.core.model.SuggestionDurationTag
import com.example.refocus.core.model.SuggestionPriority
import com.example.refocus.core.model.SuggestionTimeSlot
import com.example.refocus.feature.suggestions.SuggestionActionKind

@Composable
internal fun SuggestionViewSheet(
    title: String,
    timeSlots: Set<SuggestionTimeSlot>,
    durationTag: SuggestionDurationTag,
    priority: SuggestionPriority,
    actionKind: SuggestionActionKind,
    actionValue: String,
    actionDisplay: String,
    onClose: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左上: 閉じるアイコン
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "閉じる",
                )
            }

            Row {
                // 右上: 編集アイコン
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "編集",
                    )
                }
                // 右上: 削除アイコン
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "削除",
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // タイトル
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
        )

        Spacer(modifier = Modifier.height(16.dp))

        SuggestionTagsRow(
            timeSlots = timeSlots,
            durationTag = durationTag,
            priority = priority,
            interactive = false,
            onToggleTimeSlot = {},
            onDurationTagChange = {},
            onPriorityChange = {},
        )

        val destinationText = buildDestinationText(actionKind, actionValue, actionDisplay)
        if (destinationText != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = destinationText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun SuggestionEditorSheet(
    title: String,
    onTitleChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    timeSlots: Set<SuggestionTimeSlot>,
    onToggleTimeSlot: (SuggestionTimeSlot) -> Unit,
    durationTag: SuggestionDurationTag,
    onDurationTagChange: (SuggestionDurationTag) -> Unit,
    priority: SuggestionPriority,
    onPriorityChange: (SuggestionPriority) -> Unit,
    actionKind: SuggestionActionKind,
    onActionKindChange: (SuggestionActionKind) -> Unit,
    actionValue: String,
    onActionValueChange: (String) -> Unit,
    actionDisplay: String,
    onRequestPickApp: () -> Unit,
    urlErrorMessage: String?,
    appErrorMessage: String?,
    isConfirmEnabled: Boolean,
) {
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp),
    ) {
        // 上部ヘッダー: 左に×, 右に保存
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCancel) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "閉じる",
                )
            }

            TextButton(
                onClick = {
                    if (isConfirmEnabled) {
                        onConfirm()
                    }
                },
                enabled = isConfirmEnabled,
            ) {
                Text("保存")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ラベル入力: 枠なし・単一行・プレースホルダ表示
        BasicTextField(
            value = title,
            onValueChange = onTitleChange,
            textStyle =
                MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            singleLine = true,
            keyboardOptions =
                KeyboardOptions.Default.copy(
                    imeAction = ImeAction.Done,
                ),
            keyboardActions =
                KeyboardActions(
                    onDone = {
                        focusManager.clearFocus(force = true)
                    },
                ),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 64.dp),
            decorationBox = { innerTextField ->
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 64.dp),
                ) {
                    if (title.isBlank()) {
                        Text(
                            text = "やりたいことを入力",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    innerTextField()
                }
            },
        )

        Spacer(modifier = Modifier.height(16.dp))

        SuggestionTagsRow(
            timeSlots = timeSlots,
            durationTag = durationTag,
            priority = priority,
            interactive = true,
            onToggleTimeSlot = onToggleTimeSlot,
            onDurationTagChange = onDurationTagChange,
            onPriorityChange = onPriorityChange,
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "遷移先",
            style = MaterialTheme.typography.titleSmall,
        )

        Spacer(modifier = Modifier.height(8.dp))

        ActionKindSelector(
            actionKind = actionKind,
            onActionKindChange = onActionKindChange,
        )

        Spacer(modifier = Modifier.height(8.dp))

        when (actionKind) {
            SuggestionActionKind.None -> {
                Text(
                    text = "設定すると，提案オーバーレイのやりたいこと表示をタップして移動できます．",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SuggestionActionKind.Url -> {
                OutlinedTextField(
                    value = actionValue,
                    onValueChange = onActionValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("URL") },
                    placeholder = { Text("https://example.com") },
                    isError = urlErrorMessage != null,
                    keyboardOptions =
                        KeyboardOptions.Default.copy(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done,
                        ),
                    keyboardActions =
                        KeyboardActions(
                            onDone = {
                                focusManager.clearFocus(force = true)
                            },
                        ),
                )

                if (urlErrorMessage != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = urlErrorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "http または https の URL を入力してください．scheme を省略すると https を補完します．",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SuggestionActionKind.App -> {
                val label = actionDisplay.trim().ifBlank { actionValue.trim() }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (label.isNotBlank()) "アプリ: $label" else "アプリ: （未選択）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = onRequestPickApp,
                    ) {
                        Text("選ぶ")
                    }
                }

                if (appErrorMessage != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = appErrorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "インストール済みアプリから選択します．",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ActionKindSelector(
    actionKind: SuggestionActionKind,
    onActionKindChange: (SuggestionActionKind) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ActionKindButton(
            label = "なし",
            selected = actionKind == SuggestionActionKind.None,
            enabled = true,
            onClick = { onActionKindChange(SuggestionActionKind.None) },
        )
        ActionKindButton(
            label = "URL",
            selected = actionKind == SuggestionActionKind.Url,
            enabled = true,
            onClick = { onActionKindChange(SuggestionActionKind.Url) },
        )
        ActionKindButton(
            label = "アプリ",
            selected = actionKind == SuggestionActionKind.App,
            enabled = true,
            onClick = { onActionKindChange(SuggestionActionKind.App) },
        )
    }
}

@Composable
private fun ActionKindButton(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
    ) {
        Text(
            text = label,
            color =
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
}

private fun buildDestinationText(
    actionKind: SuggestionActionKind,
    actionValue: String,
    actionDisplay: String,
): String? {
    return when (actionKind) {
        SuggestionActionKind.None -> null
        SuggestionActionKind.Url -> {
            val label = actionDisplay.trim().ifBlank { actionValue.trim() }
            if (label.isNotBlank()) "リンク: $label" else "リンク"
        }

        SuggestionActionKind.App -> {
            val label = actionDisplay.trim().ifBlank { actionValue.trim() }
            if (label.isNotBlank()) "アプリ: $label" else "アプリ"
        }
    }
}
