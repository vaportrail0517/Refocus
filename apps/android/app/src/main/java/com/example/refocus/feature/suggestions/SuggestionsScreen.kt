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

    var screenState by rememberSaveable(stateSaver = SuggestionsScreenStateSaver) {
        mutableStateOf(SuggestionsScreenState())
    }

    val sheetState =
        rememberModalBottomSheetState(
            skipPartiallyExpanded = false,
        )

    LaunchedEffect(screenState.isEditorVisible) {
        if (screenState.isEditorVisible) {
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
                                    screenState =
                                        screenState.copy(
                                            pendingDeleteId = suggestion.id,
                                        )
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
                                                screenState = screenState.openForView(suggestion)
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
                    screenState = screenState.openForCreate()
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

            if (screenState.isEditorVisible) {
                ModalBottomSheet(
                    onDismissRequest = {
                        if (screenState.sheetMode == EditorSheetMode.Edit && screenState.isDirty) {
                            screenState =
                                screenState.copy(
                                    showDiscardConfirm = true,
                                )
                        } else {
                            screenState =
                                screenState.copy(
                                    isEditorVisible = false,
                                    showDiscardConfirm = false,
                                )
                        }
                    },
                    sheetState = sheetState,
                ) {
                    when (screenState.sheetMode) {
                        EditorSheetMode.View -> {
                            SuggestionViewSheet(
                                title = screenState.draft.title,
                                timeSlots = screenState.draft.timeSlots,
                                durationTag = screenState.draft.durationTag,
                                priority = screenState.draft.priority,
                                onClose = {
                                    screenState = screenState.closeEditor()
                                },
                                onEdit = {
                                    screenState =
                                        screenState.copy(
                                            sheetMode = EditorSheetMode.Edit,
                                        )
                                },
                                onDelete = {
                                    val id = screenState.editingSuggestionId
                                    if (id != null) {
                                        screenState =
                                            screenState.copy(
                                                pendingDeleteId = id,
                                            )
                                    }
                                },
                            )
                        }

                        EditorSheetMode.Edit -> {
                            SuggestionEditorSheet(
                                title = screenState.draft.title,
                                onTitleChange = { title ->
                                    screenState =
                                        screenState.copy(
                                            draft = screenState.draft.copy(title = title),
                                        )
                                },
                                onConfirm = {
                                    if (screenState.isCreatingNew) {
                                        onCreateSuggestion(
                                            screenState.draft.title,
                                            screenState.draft.timeSlots,
                                            screenState.draft.durationTag,
                                            screenState.draft.priority,
                                        )
                                    } else {
                                        val id = screenState.editingSuggestionId
                                        if (id != null) {
                                            onUpdateSuggestion(
                                                id,
                                                screenState.draft.title,
                                                screenState.draft.timeSlots,
                                                screenState.draft.durationTag,
                                                screenState.draft.priority,
                                            )
                                        }
                                    }
                                    screenState = screenState.closeEditor()
                                },
                                onCancel = {
                                    if (screenState.isDirty) {
                                        screenState =
                                            screenState.copy(
                                                showDiscardConfirm = true,
                                            )
                                    } else {
                                        screenState = screenState.closeEditor()
                                    }
                                },
                                timeSlots = screenState.draft.timeSlots,
                                onToggleTimeSlot = { slot ->
                                    screenState =
                                        screenState.copy(
                                            draft =
                                                screenState.draft.copy(
                                                    timeSlots = toggleTimeSlots(screenState.draft.timeSlots, slot),
                                                ),
                                        )
                                },
                                durationTag = screenState.draft.durationTag,
                                onDurationTagChange = { tag ->
                                    screenState =
                                        screenState.copy(
                                            draft = screenState.draft.copy(durationTag = tag),
                                        )
                                },
                                priority = screenState.draft.priority,
                                onPriorityChange = { priority ->
                                    screenState =
                                        screenState.copy(
                                            draft = screenState.draft.copy(priority = priority),
                                        )
                                },
                            )
                        }
                    }
                }
            }

            if (screenState.showDiscardConfirm) {
                AlertDialog(
                    onDismissRequest = {
                        screenState =
                            screenState.copy(
                                showDiscardConfirm = false,
                            )
                        if (screenState.isEditorVisible) {
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
                                screenState = screenState.closeEditor()
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
                                screenState =
                                    screenState.copy(
                                        showDiscardConfirm = false,
                                    )
                                if (screenState.isEditorVisible) {
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

            if (screenState.pendingDeleteId != null) {
                AlertDialog(
                    onDismissRequest = {
                        screenState =
                            screenState.copy(
                                pendingDeleteId = null,
                            )
                    },
                    title = { Text("やりたいことを削除しますか？") },
                    text = { Text("このやりたいことを削除すると元には戻せません。") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val id = screenState.pendingDeleteId
                                if (id != null) {
                                    onDeleteConfirmed(id)
                                }

                                screenState =
                                    screenState.copy(
                                        pendingDeleteId = null,
                                        isEditorVisible = false,
                                        editingSuggestionId = null,
                                        isCreatingNew = false,
                                        showDiscardConfirm = false,
                                    )
                            },
                        ) {
                            Text("削除する")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                screenState =
                                    screenState.copy(
                                        pendingDeleteId = null,
                                    )
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
