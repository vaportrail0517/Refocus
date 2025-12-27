package com.example.refocus.feature.suggestions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.refocus.core.model.Suggestion
import com.example.refocus.core.model.SuggestionDurationTag
import com.example.refocus.core.model.SuggestionPriority
import com.example.refocus.core.model.SuggestionTimeSlot

@Composable
internal fun SuggestionCard(
    suggestion: Suggestion,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = suggestion.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwipeToDeleteBackground(
) {
    val bgColor = MaterialTheme.colorScheme.errorContainer
    val shape = MaterialTheme.shapes.medium
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(shape)
            .background(bgColor)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "削除",
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "削除",
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

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
        modifier = Modifier
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
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp),
    ) {
        // 上部ヘッダー: 左に×, 右に保存
        Row(
            modifier = Modifier
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
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            singleLine = true,
            keyboardOptions = KeyboardOptions.Default.copy(
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus(force = true)
                },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 64.dp),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
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

@Composable
internal fun SuggestionTagChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(label) },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SuggestionTagsRow(
    timeSlots: Set<SuggestionTimeSlot>,
    durationTag: SuggestionDurationTag,
    priority: SuggestionPriority,
    // true: 編集モード（タップ可）, false: ビューモード（タップ不可）
    interactive: Boolean,
    onToggleTimeSlot: (SuggestionTimeSlot) -> Unit,
    onDurationTagChange: (SuggestionDurationTag) -> Unit,
    onPriorityChange: (SuggestionPriority) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 時間帯
        Column {
            val normalized = normalizeTimeSlots(timeSlots)
            val hintText = if (normalized == setOf(SuggestionTimeSlot.Anytime)) {
                "いつでも"
            } else {
                normalized.sortedBy { slotOrder().indexOf(it) }
                    .joinToString(" / ") { "${it.labelJa()}（${it.hintJa()}）" }
            }
            Text(
                text = "時間帯: $hintText",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val slots = normalizeTimeSlots(timeSlots)
                slotOrder().forEach { slot ->
                    SuggestionTagChip(
                        label = slot.labelJa(),
                        selected = slots.contains(slot),
                        enabled = interactive,
                        onClick = { onToggleTimeSlot(slot) },
                    )
                }
            }
        }

        // 所要時間
        Column {
            Text(
                text = "所要時間",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SuggestionTagChip(
                    label = "短め",
                    selected = durationTag == SuggestionDurationTag.Short,
                    enabled = interactive,
                    onClick = { onDurationTagChange(SuggestionDurationTag.Short) },
                )
                SuggestionTagChip(
                    label = "ふつう",
                    selected = durationTag == SuggestionDurationTag.Medium,
                    enabled = interactive,
                    onClick = { onDurationTagChange(SuggestionDurationTag.Medium) },
                )
                SuggestionTagChip(
                    label = "じっくり",
                    selected = durationTag == SuggestionDurationTag.Long,
                    enabled = interactive,
                    onClick = { onDurationTagChange(SuggestionDurationTag.Long) },
                )
            }
        }

        // 優先度
        Column {
            Text(
                text = "優先度",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SuggestionTagChip(
                    label = "低",
                    selected = priority == SuggestionPriority.Low,
                    enabled = interactive,
                    onClick = { onPriorityChange(SuggestionPriority.Low) },
                )
                SuggestionTagChip(
                    label = "通常",
                    selected = priority == SuggestionPriority.Normal,
                    enabled = interactive,
                    onClick = { onPriorityChange(SuggestionPriority.Normal) },
                )
                SuggestionTagChip(
                    label = "高",
                    selected = priority == SuggestionPriority.High,
                    enabled = interactive,
                    onClick = { onPriorityChange(SuggestionPriority.High) },
                )
            }
        }
    }
}
