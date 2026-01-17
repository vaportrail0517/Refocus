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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.refocus.core.model.SuggestionAction
import com.example.refocus.core.model.SuggestionDurationTag
import com.example.refocus.core.model.SuggestionPriority
import com.example.refocus.core.model.SuggestionTimeSlot
import com.example.refocus.feature.suggestions.components.SuggestionActionAppPickerSheet
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
        action: SuggestionAction,
    ) -> Unit,
    onUpdateSuggestion: (
        id: Long,
        title: String,
        timeSlots: Set<SuggestionTimeSlot>,
        durationTag: SuggestionDurationTag,
        priority: SuggestionPriority,
        action: SuggestionAction,
    ) -> Unit,
    onDeleteConfirmed: (Long) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()

    val appPickerViewModel: SuggestionActionAppPickerViewModel = hiltViewModel()
    val appPickerApps by appPickerViewModel.apps.collectAsState()

    var isAppPickerVisible by rememberSaveable { mutableStateOf(false) }
    var appPickerQuery by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }

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
                        if (isAppPickerVisible) {
                            isAppPickerVisible = false
                            return@ModalBottomSheet
                        }
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
                    val draft = screenState.draft

                    if (isAppPickerVisible) {
                        SuggestionActionAppPickerSheet(
                            apps = appPickerApps,
                            query = appPickerQuery,
                            onQueryChange = { appPickerQuery = it },
                            onSelect = { app ->
                                screenState =
                                    screenState.copy(
                                        draft =
                                            draft.copy(
                                                actionKind = SuggestionActionKind.App,
                                                actionValue = app.packageName,
                                                actionDisplay = app.label,
                                            ),
                                    )
                                isAppPickerVisible = false
                            },
                            onClose = {
                                isAppPickerVisible = false
                            },
                        )
                    } else {
                        val normalizedUrl =
                            if (draft.actionKind == SuggestionActionKind.Url) {
                                normalizeHttpUrlOrNull(draft.actionValue)
                            } else {
                                null
                            }

                        val urlErrorMessage =
                            if (draft.actionKind == SuggestionActionKind.Url) {
                                when {
                                    draft.actionValue.isBlank() -> "URLを入力してください．"
                                    normalizedUrl == null -> "http または https の URL を入力してください．"
                                    else -> null
                                }
                            } else {
                                null
                            }

                        val appErrorMessage =
                            if (draft.actionKind == SuggestionActionKind.App) {
                                if (draft.actionValue.trim().isBlank()) {
                                    "アプリを選択してください．"
                                } else {
                                    null
                                }
                            } else {
                                null
                            }

                        val isConfirmEnabled =
                            draft.title.trim().isNotEmpty() &&
                                (draft.actionKind != SuggestionActionKind.Url || normalizedUrl != null) &&
                                (draft.actionKind != SuggestionActionKind.App || draft.actionValue.trim().isNotEmpty())

                        when (screenState.sheetMode) {
                            EditorSheetMode.View -> {
                                SuggestionViewSheet(
                                    title = draft.title,
                                    timeSlots = draft.timeSlots,
                                    durationTag = draft.durationTag,
                                    priority = draft.priority,
                                    actionKind = draft.actionKind,
                                    actionValue = draft.actionValue,
                                    actionDisplay = draft.actionDisplay,
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
                                    title = draft.title,
                                    onTitleChange = { title ->
                                        screenState =
                                            screenState.copy(
                                                draft = draft.copy(title = title),
                                            )
                                    },
                                    onConfirm = {
                                        val action =
                                            buildSuggestionActionForSave(
                                                kind = draft.actionKind,
                                                value = draft.actionValue,
                                                display = draft.actionDisplay,
                                            )

                                        if (screenState.isCreatingNew) {
                                            onCreateSuggestion(
                                                draft.title,
                                                draft.timeSlots,
                                                draft.durationTag,
                                                draft.priority,
                                                action,
                                            )
                                        } else {
                                            val id = screenState.editingSuggestionId
                                            if (id != null) {
                                                onUpdateSuggestion(
                                                    id,
                                                    draft.title,
                                                    draft.timeSlots,
                                                    draft.durationTag,
                                                    draft.priority,
                                                    action,
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
                                    timeSlots = draft.timeSlots,
                                    onToggleTimeSlot = { slot ->
                                        screenState =
                                            screenState.copy(
                                                draft =
                                                    draft.copy(
                                                        timeSlots = toggleTimeSlots(draft.timeSlots, slot),
                                                    ),
                                            )
                                    },
                                    durationTag = draft.durationTag,
                                    onDurationTagChange = { tag ->
                                        screenState =
                                            screenState.copy(
                                                draft = draft.copy(durationTag = tag),
                                            )
                                    },
                                    priority = draft.priority,
                                    onPriorityChange = { priority ->
                                        screenState =
                                            screenState.copy(
                                                draft = draft.copy(priority = priority),
                                            )
                                    },
                                    actionKind = draft.actionKind,
                                    onActionKindChange = { kind ->
                                        val next =
                                            when (kind) {
                                                SuggestionActionKind.None ->
                                                    draft.copy(
                                                        actionKind = SuggestionActionKind.None,
                                                        actionValue = "",
                                                        actionDisplay = "",
                                                    )

                                                SuggestionActionKind.Url ->
                                                    draft.copy(
                                                        actionKind = SuggestionActionKind.Url,
                                                        actionValue =
                                                            if (draft.actionKind ==
                                                                SuggestionActionKind.Url
                                                            ) {
                                                                draft.actionValue
                                                            } else {
                                                                ""
                                                            },
                                                        actionDisplay = "",
                                                    )

                                                SuggestionActionKind.App ->
                                                    draft.copy(
                                                        actionKind = SuggestionActionKind.App,
                                                        actionValue =
                                                            if (draft.actionKind ==
                                                                SuggestionActionKind.App
                                                            ) {
                                                                draft.actionValue
                                                            } else {
                                                                ""
                                                            },
                                                        actionDisplay =
                                                            if (draft.actionKind ==
                                                                SuggestionActionKind.App
                                                            ) {
                                                                draft.actionDisplay
                                                            } else {
                                                                ""
                                                            },
                                                    )
                                            }

                                        screenState =
                                            screenState.copy(
                                                draft = next,
                                            )
                                    },
                                    actionValue = draft.actionValue,
                                    onActionValueChange = { value ->
                                        screenState =
                                            screenState.copy(
                                                draft =
                                                    draft.copy(
                                                        actionValue = value,
                                                        actionDisplay = "",
                                                    ),
                                            )
                                    },
                                    actionDisplay = draft.actionDisplay,
                                    onRequestPickApp = {
                                        appPickerQuery = TextFieldValue("")
                                        isAppPickerVisible = true
                                    },
                                    urlErrorMessage = urlErrorMessage,
                                    appErrorMessage = appErrorMessage,
                                    isConfirmEnabled = isConfirmEnabled,
                                )
                            }
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
