package com.example.refocus.feature.suggestions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.refocus.core.model.Suggestion
import com.example.refocus.core.model.SuggestionDurationTag
import com.example.refocus.core.model.SuggestionPriority
import com.example.refocus.core.model.SuggestionTimeSlot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestionsRoute(
    viewModel: SuggestionsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SuggestionsScreen(
        uiState = uiState,
        onAddClicked = { viewModel.addSuggestionAndStartEditing() },
        onStartEditing = { id -> viewModel.startEditing(id) },
        onCommitEdit = { id, text -> viewModel.commitEdit(id, text) },
        onDeleteConfirmed = { id -> viewModel.deleteSuggestion(id) },
        onUpdateTags = { id, timeSlot, durationTag, priority ->
            viewModel.updateTags(id, timeSlot, durationTag, priority)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestionsScreen(
    uiState: SuggestionsUiState,
    onAddClicked: () -> Unit,
    onStartEditing: (Long) -> Unit,
    onCommitEdit: (Long, String) -> Unit,
    onDeleteConfirmed: (Long) -> Unit,
    onUpdateTags: (
        id: Long,
        timeSlot: SuggestionTimeSlot,
        durationTag: SuggestionDurationTag,
        priority: SuggestionPriority,
    ) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var pendingDeleteId by rememberSaveable { mutableStateOf<Long?>(null) }

    // 戻るボタン → まずフォーカスを外す（＝キーボードを閉じる）
    //    BackHandler(enabled = uiState.editingId != null) {
    //        focusManager.clearFocus(force = true)
    //    }

    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    // キーボードが閉じられた瞬間にフォーカスを外す
    LaunchedEffect(imeVisible) {
        if (!imeVisible) {
            focusManager.clearFocus(force = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("やりたいこと") },
                windowInsets = WindowInsets(0.dp),
            )
        },
        contentWindowInsets = WindowInsets(0.dp),
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // 画面のどこをタップしてもフォーカスを外す
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    focusManager.clearFocus(force = true)
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                if (uiState.suggestions.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "やりたいことはまだ登録されていません。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    val listState = rememberLazyListState()
                    // 「編集対象ID」が変わったら、そのカード位置までスクロール
                    LaunchedEffect(uiState.editingId, uiState.suggestions) {
                        val targetId = uiState.editingId ?: return@LaunchedEffect
                        val index = uiState.suggestions.indexOfFirst { it.id == targetId }
                        if (index >= 0) {
                            listState.animateScrollToItem(index)
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        items(
                            items = uiState.suggestions,
                            key = { it.id },
                        ) { suggestion ->
                            val isEditing = uiState.editingId == suggestion.id
                            val dismissState = rememberSwipeToDismissBoxState()

                            LaunchedEffect(dismissState.currentValue) {
                                val value = dismissState.currentValue
                                if (value != SwipeToDismissBoxValue.Settled) {
                                    pendingDeleteId = suggestion.id
                                    dismissState.snapTo(SwipeToDismissBoxValue.Settled)
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 4.dp)
                            ) {
                                SwipeToDismissBox(
                                    state = dismissState,
                                    enableDismissFromStartToEnd = true,
                                    enableDismissFromEndToStart = false,
                                    backgroundContent = {
                                        SwipeToDeleteBackground(dismissState)
                                    },
                                    content = {
                                        SuggestionCard(
                                            suggestion = suggestion,
                                            isEditing = isEditing,
                                            onStartEditing = { onStartEditing(suggestion.id) },
                                            onCommitEdit = { text ->
                                                onCommitEdit(suggestion.id, text)
                                            },
                                            onUpdateTags = { timeSlot, durationTag, priority ->
                                                onUpdateTags(
                                                    suggestion.id,
                                                    timeSlot,
                                                    durationTag,
                                                    priority,
                                                )
                                            },
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            FloatingActionButton(
                onClick = onAddClicked,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "やりたいことを追加"
                )
            }

            if (pendingDeleteId != null) {
                AlertDialog(
                    onDismissRequest = { pendingDeleteId = null },
                    title = { Text("やりたいことを削除しますか？") },
                    text = { Text("このやりたいことを削除すると元には戻せません。") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val id = pendingDeleteId
                                pendingDeleteId = null
                                if (id != null) {
                                    onDeleteConfirmed(id)
                                }
                            }
                        ) {
                            Text("削除")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingDeleteId = null }) {
                            Text("キャンセル")
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SuggestionCard(
    suggestion: Suggestion,
    isEditing: Boolean,
    onStartEditing: () -> Unit,
    onCommitEdit: (String) -> Unit,
    onUpdateTags: (
        SuggestionTimeSlot,
        SuggestionDurationTag,
        SuggestionPriority,
    ) -> Unit,
) {
    var text by remember(suggestion.id) { mutableStateOf(suggestion.title) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    // 「この編集で既に確定処理を呼んだか」を記録して二重実行を防ぐ
    var committed by remember(suggestion.id) { mutableStateOf(false) }
    // 「一度でもフォーカスが当たったか（＝単なる初期状態の onFocusChanged を避ける）」
    var hadFocus by remember(suggestion.id) { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        onClick = {
            if (!isEditing) {
                onStartEditing()
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isEditing) {
                LaunchedEffect(isEditing) {
                    if (isEditing) {
                        committed = false
                        hadFocus = false
                        focusRequester.requestFocus()
                    }
                }

                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions.Default.copy(
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (!committed) {
                                committed = true
                                onCommitEdit(text)
                            }
                            focusManager.clearFocus(force = true)
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { state ->
                            if (state.isFocused) {
                                hadFocus = true
                            } else if (!state.isFocused && hadFocus && !committed) {
                                // どこかをタップしてフォーカスが外れた場合もここに来る
                                committed = true
                                onCommitEdit(text)
                            }
                        },
                )
            } else {
                Text(
                    text = suggestion.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TagSelectorRow(
                timeSlot = suggestion.timeSlot,
                durationTag = suggestion.durationTag,
                priority = suggestion.priority,
                onChange = onUpdateTags,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDeleteBackground(
    dismissState: SwipeToDismissBoxState,
) {
    // スワイプ中かどうか（方向が付いているあいだ＝ユーザがドラッグしているあいだ）
    val isSwiping = dismissState.dismissDirection != null

    val bgColor = if (isSwiping) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        Color.Transparent
    }
    val shape = MaterialTheme.shapes.medium
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(shape)
            .background(bgColor)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        if (isSwiping) {
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
}

@Composable
private fun TagSelectorRow(
    timeSlot: SuggestionTimeSlot,
    durationTag: SuggestionDurationTag,
    priority: SuggestionPriority,
    onChange: (
        SuggestionTimeSlot,
        SuggestionDurationTag,
        SuggestionPriority,
    ) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // 時間帯
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "時間帯:",
                style = MaterialTheme.typography.labelMedium,
            )
            TagChip(
                label = "いつでも",
                selected = timeSlot == SuggestionTimeSlot.Anytime,
                onClick = { onChange(SuggestionTimeSlot.Anytime, durationTag, priority) },
            )
            TagChip(
                label = "朝",
                selected = timeSlot == SuggestionTimeSlot.Morning,
                onClick = { onChange(SuggestionTimeSlot.Morning, durationTag, priority) },
            )
            TagChip(
                label = "昼",
                selected = timeSlot == SuggestionTimeSlot.Afternoon,
                onClick = { onChange(SuggestionTimeSlot.Afternoon, durationTag, priority) },
            )
            TagChip(
                label = "夕方",
                selected = timeSlot == SuggestionTimeSlot.Evening,
                onClick = { onChange(SuggestionTimeSlot.Evening, durationTag, priority) },
            )
            TagChip(
                label = "夜",
                selected = timeSlot == SuggestionTimeSlot.Night,
                onClick = { onChange(SuggestionTimeSlot.Night, durationTag, priority) },
            )
        }

        // 所要時間
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "長さ:",
                style = MaterialTheme.typography.labelMedium,
            )
            TagChip(
                label = "短い",
                selected = durationTag == SuggestionDurationTag.Short,
                onClick = { onChange(timeSlot, SuggestionDurationTag.Short, priority) },
            )
            TagChip(
                label = "普通",
                selected = durationTag == SuggestionDurationTag.Medium,
                onClick = { onChange(timeSlot, SuggestionDurationTag.Medium, priority) },
            )
            TagChip(
                label = "長い",
                selected = durationTag == SuggestionDurationTag.Long,
                onClick = { onChange(timeSlot, SuggestionDurationTag.Long, priority) },
            )
        }

        // 優先度
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "優先度:",
                style = MaterialTheme.typography.labelMedium,
            )
            TagChip(
                label = "低",
                selected = priority == SuggestionPriority.Low,
                onClick = { onChange(timeSlot, durationTag, SuggestionPriority.Low) },
            )
            TagChip(
                label = "普通",
                selected = priority == SuggestionPriority.Normal,
                onClick = { onChange(timeSlot, durationTag, SuggestionPriority.Normal) },
            )
            TagChip(
                label = "高",
                selected = priority == SuggestionPriority.High,
                onClick = { onChange(timeSlot, durationTag, SuggestionPriority.High) },
            )
        }
    }
}

@Composable
private fun TagChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    }
    val border = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(background)
            .border(
                width = 1.dp,
                color = border,
                shape = MaterialTheme.shapes.small,
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
    )
}
