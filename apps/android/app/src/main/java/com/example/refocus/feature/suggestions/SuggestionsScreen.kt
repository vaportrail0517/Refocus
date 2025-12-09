package com.example.refocus.feature.suggestions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.refocus.core.model.Suggestion
import com.example.refocus.core.model.SuggestionDurationTag
import com.example.refocus.core.model.SuggestionPriority
import com.example.refocus.core.model.SuggestionTimeSlot
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestionsRoute(
    viewModel: SuggestionsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SuggestionsScreen(
        uiState = uiState,
        onCreateSuggestion = { title, timeSlot, durationTag, priority ->
            viewModel.createSuggestion(title, timeSlot, durationTag, priority)
        },
        onUpdateSuggestion = { id, title, timeSlot, durationTag, priority ->
            viewModel.updateSuggestion(id, title, timeSlot, durationTag, priority)
        },
        onDeleteConfirmed = { id -> viewModel.deleteSuggestion(id) },
    )
}

private enum class EditorSheetMode {
    View,
    Edit,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestionsScreen(
    uiState: SuggestionsUiState,
    onCreateSuggestion: (
        title: String,
        timeSlot: SuggestionTimeSlot,
        durationTag: SuggestionDurationTag,
        priority: SuggestionPriority,
    ) -> Unit,
    onUpdateSuggestion: (
        id: Long,
        title: String,
        timeSlot: SuggestionTimeSlot,
        durationTag: SuggestionDurationTag,
        priority: SuggestionPriority,
    ) -> Unit,
    onDeleteConfirmed: (Long) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    var pendingDeleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var isEditorVisible by rememberSaveable { mutableStateOf(false) }
    var editingSuggestionId by rememberSaveable { mutableStateOf<Long?>(null) }
    var isCreatingNew by rememberSaveable { mutableStateOf(false) }
    var draftTitle by rememberSaveable { mutableStateOf("") }
    var draftTimeSlot by rememberSaveable { mutableStateOf(SuggestionTimeSlot.Anytime) }
    var draftDurationTag by rememberSaveable { mutableStateOf(SuggestionDurationTag.Medium) }
    var draftPriority by rememberSaveable { mutableStateOf(SuggestionPriority.Normal) }
    var initialTitle by rememberSaveable { mutableStateOf("") }
    var initialTimeSlot by rememberSaveable { mutableStateOf(SuggestionTimeSlot.Anytime) }
    var initialDurationTag by rememberSaveable { mutableStateOf(SuggestionDurationTag.Medium) }
    var initialPriority by rememberSaveable { mutableStateOf(SuggestionPriority.Normal) }
    var showDiscardConfirm by rememberSaveable { mutableStateOf(false) }
    var sheetMode by rememberSaveable { mutableStateOf(EditorSheetMode.View) }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = false,
    )

    LaunchedEffect(isEditorVisible) {
        if (isEditorVisible) {
            coroutineScope.launch {
                sheetState.expand()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("提案") },
                windowInsets = WindowInsets(0.dp),
            )
        },
        contentWindowInsets = WindowInsets(0.dp),
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
                                            onClick = {
                                                editingSuggestionId = suggestion.id
                                                isCreatingNew = false
                                                // 初期値（開いた瞬間の値）
                                                initialTitle = suggestion.title
                                                initialTimeSlot = suggestion.timeSlot
                                                initialDurationTag = suggestion.durationTag
                                                initialPriority = suggestion.priority
                                                // 編集用ドラフト（最初は初期値と同じ）
                                                draftTitle = suggestion.title
                                                draftTimeSlot = suggestion.timeSlot
                                                draftDurationTag = suggestion.durationTag
                                                draftPriority = suggestion.priority
                                                sheetMode = EditorSheetMode.View
                                                isEditorVisible = true
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
                onClick = {
                    editingSuggestionId = null
                    isCreatingNew = true
                    // 新規の初期値
                    initialTitle = ""
                    initialTimeSlot = SuggestionTimeSlot.Anytime
                    initialDurationTag = SuggestionDurationTag.Medium
                    initialPriority = SuggestionPriority.Normal
                    // ドラフト
                    draftTitle = ""
                    draftTimeSlot = SuggestionTimeSlot.Anytime
                    draftDurationTag = SuggestionDurationTag.Medium
                    draftPriority = SuggestionPriority.Normal
                    sheetMode = EditorSheetMode.Edit
                    isEditorVisible = true
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "やりたいことを追加"
                )
            }

            // 編集用のボトムシート
            if (isEditorVisible) {
                ModalBottomSheet(
                    onDismissRequest = {
                        if (sheetMode == EditorSheetMode.Edit) {
                            val isDirty =
                                draftTitle != initialTitle ||
                                        draftTimeSlot != initialTimeSlot ||
                                        draftDurationTag != initialDurationTag ||
                                        draftPriority != initialPriority
                            if (isDirty) {
                                showDiscardConfirm = true
                            } else {
                                isEditorVisible = false
                            }
                        } else {
                            isEditorVisible = false
                        }
                    },
                    sheetState = sheetState,
                ) {
                    when (sheetMode) {
                        EditorSheetMode.View -> {
                            // 閲覧用シート
                            SuggestionViewSheet(
                                title = draftTitle,
                                timeSlot = draftTimeSlot,
                                durationTag = draftDurationTag,
                                priority = draftPriority,
                                onClose = {
                                    isEditorVisible = false
                                },
                                onEdit = {
                                    // 編集モードへ切り替え
                                    sheetMode = EditorSheetMode.Edit
                                    // initialXXX はすでにセット済みなのでそのままで OK
                                },
                                onDelete = {
                                    val id = editingSuggestionId
                                    if (id != null) {
                                        pendingDeleteId = id
                                    }
                                },
                            )
                        }

                        EditorSheetMode.Edit -> {
                            // 既存の編集用シート（前回追加した discard 判定付き）
                            SuggestionEditorSheet(
                                title = draftTitle,
                                onTitleChange = { draftTitle = it },
                                isNew = isCreatingNew,
                                onConfirm = {
                                    if (isCreatingNew) {
                                        onCreateSuggestion(
                                            draftTitle,
                                            draftTimeSlot,
                                            draftDurationTag,
                                            draftPriority,
                                        )
                                    } else {
                                        val id = editingSuggestionId
                                        if (id != null) {
                                            onUpdateSuggestion(
                                                id,
                                                draftTitle,
                                                draftTimeSlot,
                                                draftDurationTag,
                                                draftPriority,
                                            )
                                        }
                                    }
                                    isEditorVisible = false
                                },
                                onDelete = if (!isCreatingNew && editingSuggestionId != null) {
                                    {
                                        val id = editingSuggestionId
                                        if (id != null) {
                                            pendingDeleteId = id
                                        }
                                    }
                                } else null,
                                onCancel = {
                                    // 前回説明した discard 判定ロジックをここに
                                    val isDirty =
                                        draftTitle != initialTitle ||
                                                draftTimeSlot != initialTimeSlot ||
                                                draftDurationTag != initialDurationTag ||
                                                draftPriority != initialPriority

                                    if (isDirty) {
                                        showDiscardConfirm = true
                                    } else {
                                        isEditorVisible = false
                                    }
                                },
                                timeSlot = draftTimeSlot,
                                onTimeSlotChange = { draftTimeSlot = it },
                                durationTag = draftDurationTag,
                                onDurationTagChange = { draftDurationTag = it },
                                priority = draftPriority,
                                onPriorityChange = { draftPriority = it },
                            )
                        }
                    }
                }
            }

            if (showDiscardConfirm) {
                AlertDialog(
                    onDismissRequest = {
                        showDiscardConfirm = false
                        // ダイアログの外側タップなどでも編集を続けたいならここでも show()
                        if (isEditorVisible) {
                            coroutineScope.launch {
                                sheetState.show()
                            }
                        }
                    },
                    title = { Text("入力中のやりたいことを破棄しますか？") },
                    text = { Text("変更内容は保存されません。") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                // 破棄を確定
                                showDiscardConfirm = false
                                isEditorVisible = false
                                // アニメーションさせたいなら hide() を呼んでもよい
                                coroutineScope.launch {
                                    sheetState.hide()
                                }
                            }
                        ) {
                            Text("破棄する")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                // キャンセル → ダイアログだけ閉じてシートを戻す
                                showDiscardConfirm = false
                                if (isEditorVisible) {
                                    coroutineScope.launch {
                                        sheetState.show()
                                    }
                                }
                            }
                        ) {
                            Text("キャンセル")
                        }
                    },
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
                                if (id != null) {
                                    onDeleteConfirmed(id)   // ← リポジトリ経由で削除
                                }

                                // ダイアログの状態をリセット
                                pendingDeleteId = null

                                // ここを追加：シートを閉じる
                                isEditorVisible = false
                                editingSuggestionId = null
                                isCreatingNew = false
                                // 必要なら sheetMode や draftXXX もリセットしてよい
                                // sheetMode = EditorSheetMode.View
                            }
                        ) {
                            Text("削除する")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                // 削除をキャンセル → ダイアログだけ閉じてシートは残す
                                pendingDeleteId = null
                            }
                        ) {
                            Text("キャンセル")
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SuggestionCard(
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
private fun SuggestionViewSheet(
    title: String,
    timeSlot: SuggestionTimeSlot,
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
            .padding(horizontal = 16.dp)
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
            timeSlot = timeSlot,
            durationTag = durationTag,
            priority = priority,
            interactive = false, // ← 編集なのでタップ可能
            onTimeSlotChange = {},
            onDurationTagChange = {},
            onPriorityChange = {},
        )
        // 必要なら作成日時やメモなどもここに表示できる
    }
}

@Composable
private fun SuggestionEditorSheet(
    title: String,
    onTitleChange: (String) -> Unit,
    isNew: Boolean,
    onConfirm: () -> Unit,
    onDelete: (() -> Unit)?,
    onCancel: () -> Unit,
    timeSlot: SuggestionTimeSlot,
    onTimeSlotChange: (SuggestionTimeSlot) -> Unit,
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
            .padding(horizontal = 16.dp)
    ) {
        // 上部ヘッダー: 左に×, 右に保存
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onCancel) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "閉じる",
                    )
                }
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

        // ラベル入力: 枠なし・複数行・プレースホルダ表示
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
            timeSlot = timeSlot,
            durationTag = durationTag,
            priority = priority,
            interactive = true, // ← 編集なのでタップ可能
            onTimeSlotChange = onTimeSlotChange,
            onDurationTagChange = onDurationTagChange,
            onPriorityChange = onPriorityChange,
        )
    }
}

@Composable
private fun SuggestionTagChip(
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
private fun SuggestionTagsRow(
    timeSlot: SuggestionTimeSlot,
    durationTag: SuggestionDurationTag,
    priority: SuggestionPriority,
    // true: 編集モード（タップ可）, false: ビューモード（タップ不可）
    interactive: Boolean,
    onTimeSlotChange: (SuggestionTimeSlot) -> Unit,
    onDurationTagChange: (SuggestionDurationTag) -> Unit,
    onPriorityChange: (SuggestionPriority) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 時間帯
        Column {
            Text(
                text = "時間帯",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SuggestionTagChip(
                    label = "いつでも",
                    selected = timeSlot == SuggestionTimeSlot.Anytime,
                    enabled = interactive,
                    onClick = { onTimeSlotChange(SuggestionTimeSlot.Anytime) },
                )
                SuggestionTagChip(
                    label = "朝",
                    selected = timeSlot == SuggestionTimeSlot.Morning,
                    enabled = interactive,
                    onClick = { onTimeSlotChange(SuggestionTimeSlot.Morning) },
                )
                SuggestionTagChip(
                    label = "昼",
                    selected = timeSlot == SuggestionTimeSlot.Afternoon,
                    enabled = interactive,
                    onClick = { onTimeSlotChange(SuggestionTimeSlot.Afternoon) },
                )
                SuggestionTagChip(
                    label = "夜",
                    selected = timeSlot == SuggestionTimeSlot.Evening,
                    enabled = interactive,
                    onClick = { onTimeSlotChange(SuggestionTimeSlot.Evening) },
                )
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
