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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.refocus.core.model.SuggestionDurationTag
import com.example.refocus.core.model.SuggestionPriority
import com.example.refocus.core.model.SuggestionTimeSlot

@Composable
internal fun SuggestionViewSheet(
    title: String,
    timeSlots: Set<SuggestionTimeSlot>,
    durationTag: SuggestionDurationTag,
    priority: SuggestionPriority,
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
                    if (title.isNotBlank()) {
                        onConfirm()
                    }
                },
                enabled = title.isNotBlank(),
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
    }
}
