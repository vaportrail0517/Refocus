package com.example.refocus.feature.suggestions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
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
import androidx.compose.ui.unit.dp
import com.example.refocus.core.model.SuggestionDurationTag
import com.example.refocus.core.model.SuggestionPriority
import com.example.refocus.core.model.SuggestionTimeSlot
import com.example.refocus.feature.suggestions.components.SuggestionCard
import com.example.refocus.feature.suggestions.components.SuggestionEditorSheet
import com.example.refocus.feature.suggestions.components.SuggestionViewSheet
import com.example.refocus.feature.suggestions.components.SwipeToDeleteBackground
import kotlinx.coroutines.launch

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
        timeSlots: Set<SuggestionTimeSlot>,
        durationTag: SuggestionDurationTag,
        priority: SuggestionPriority,
    ) -> Unit,
    onUpdateSuggestion: (
        id: Long,
        title: String,
        timeSlots: Set<SuggestionTimeSlot>,
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
    var draftTimeSlots by rememberSaveable(stateSaver = TimeSlotsStateSaver) {
        mutableStateOf(setOf(SuggestionTimeSlot.Anytime))
    }
    var draftDurationTag by rememberSaveable { mutableStateOf(SuggestionDurationTag.Medium) }
    var draftPriority by rememberSaveable { mutableStateOf(SuggestionPriority.Normal) }
    var initialTitle by rememberSaveable { mutableStateOf("") }
    var initialTimeSlots by rememberSaveable(stateSaver = TimeSlotsStateSaver) {
        mutableStateOf(setOf(SuggestionTimeSlot.Anytime))
    }
    var initialDurationTag by rememberSaveable { mutableStateOf(SuggestionDurationTag.Medium) }
    var initialPriority by rememberSaveable { mutableStateOf(SuggestionPriority.Normal) }
    var showDiscardConfirm by rememberSaveable { mutableStateOf(false) }
    var sheetMode by rememberSaveable { mutableStateOf(EditorSheetMode.View) }
    val sheetState =
        rememberModalBottomSheetState(
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
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                if (uiState.suggestions.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
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
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp, vertical = 4.dp),
                            ) {
                                SwipeToDismissBox(
                                    state = dismissState,
                                    enableDismissFromStartToEnd = true,
                                    enableDismissFromEndToStart = false,
                                    backgroundContent = {
                                        SwipeToDeleteBackground()
                                    },
                                    content = {
                                        SuggestionCard(
                                            suggestion = suggestion,
                                            onClick = {
                                                editingSuggestionId = suggestion.id
                                                isCreatingNew = false
                                                // 初期値（開いた瞬間の値）
                                                initialTitle = suggestion.title
                                                initialTimeSlots = suggestion.timeSlots
                                                initialDurationTag = suggestion.durationTag
                                                initialPriority = suggestion.priority
                                                // 編集用ドラフト（最初は初期値と同じ）
                                                draftTitle = suggestion.title
                                                draftTimeSlots = suggestion.timeSlots
                                                draftDurationTag = suggestion.durationTag
                                                draftPriority = suggestion.priority
                                                sheetMode = EditorSheetMode.View
                                                isEditorVisible = true
                                            },
                                        )
                                    },
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
                    initialTimeSlots = setOf(SuggestionTimeSlot.Anytime)
                    initialDurationTag = SuggestionDurationTag.Medium
                    initialPriority = SuggestionPriority.Normal
                    // ドラフト
                    draftTitle = ""
                    draftTimeSlots = setOf(SuggestionTimeSlot.Anytime)
                    draftDurationTag = SuggestionDurationTag.Medium
                    draftPriority = SuggestionPriority.Normal
                    sheetMode = EditorSheetMode.Edit
                    isEditorVisible = true
                },
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "やりたいことを追加",
                )
            }

            if (isEditorVisible) {
                ModalBottomSheet(
                    onDismissRequest = {
                        if (sheetMode == EditorSheetMode.Edit) {
                            val isDirty =
                                draftTitle != initialTitle ||
                                    draftTimeSlots != initialTimeSlots ||
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
                            SuggestionViewSheet(
                                title = draftTitle,
                                timeSlots = draftTimeSlots,
                                durationTag = draftDurationTag,
                                priority = draftPriority,
                                onClose = {
                                    isEditorVisible = false
                                },
                                onEdit = {
                                    sheetMode = EditorSheetMode.Edit
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
                            SuggestionEditorSheet(
                                title = draftTitle,
                                onTitleChange = { draftTitle = it },
                                onConfirm = {
                                    if (isCreatingNew) {
                                        onCreateSuggestion(
                                            draftTitle,
                                            draftTimeSlots,
                                            draftDurationTag,
                                            draftPriority,
                                        )
                                    } else {
                                        val id = editingSuggestionId
                                        if (id != null) {
                                            onUpdateSuggestion(
                                                id,
                                                draftTitle,
                                                draftTimeSlots,
                                                draftDurationTag,
                                                draftPriority,
                                            )
                                        }
                                    }
                                    isEditorVisible = false
                                },
                                onCancel = {
                                    val isDirty =
                                        draftTitle != initialTitle ||
                                            draftTimeSlots != initialTimeSlots ||
                                            draftDurationTag != initialDurationTag ||
                                            draftPriority != initialPriority

                                    if (isDirty) {
                                        showDiscardConfirm = true
                                    } else {
                                        isEditorVisible = false
                                    }
                                },
                                timeSlots = draftTimeSlots,
                                onToggleTimeSlot = { slot ->
                                    draftTimeSlots = toggleTimeSlots(draftTimeSlots, slot)
                                },
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
                                showDiscardConfirm = false
                                isEditorVisible = false
                                coroutineScope.launch {
                                    sheetState.hide()
                                }
                            },
                        ) {
                            Text("破棄する")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showDiscardConfirm = false
                                if (isEditorVisible) {
                                    coroutineScope.launch {
                                        sheetState.show()
                                    }
                                }
                            },
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
                                    onDeleteConfirmed(id)
                                }

                                pendingDeleteId = null

                                isEditorVisible = false
                                editingSuggestionId = null
                                isCreatingNew = false
                            },
                        ) {
                            Text("削除する")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                pendingDeleteId = null
                            },
                        ) {
                            Text("キャンセル")
                        }
                    },
                )
            }
        }
    }
}
