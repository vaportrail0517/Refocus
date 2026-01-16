package com.example.refocus.feature.suggestions

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import com.example.refocus.core.model.Suggestion
import com.example.refocus.core.model.SuggestionAction
import com.example.refocus.core.model.SuggestionDurationTag
import com.example.refocus.core.model.SuggestionPriority
import com.example.refocus.core.model.SuggestionTimeSlot

internal enum class EditorSheetMode {
    View,
    Edit,
}

internal enum class SuggestionActionKind {
    None,
    Url,
    App,
}

internal data class SuggestionDraft(
    val title: String = "",
    val timeSlots: Set<SuggestionTimeSlot> = setOf(SuggestionTimeSlot.Anytime),
    val durationTag: SuggestionDurationTag = SuggestionDurationTag.Medium,
    val priority: SuggestionPriority = SuggestionPriority.Normal,
    /**
     * 遷移先種別．
     * - フェーズ3では Url のみ編集対応．
     */
    val actionKind: SuggestionActionKind = SuggestionActionKind.None,
    /**
     * actionKind に対応する値．
     * - Url: URL 入力
     * - App: packageName
     */
    val actionValue: String = "",
    /**
     * 表示用の短いラベル（任意）．
     * - Url: host など
     * - App: アプリ名など
     */
    val actionDisplay: String = "",
)

internal data class SuggestionsScreenState(
    val pendingDeleteId: Long? = null,
    val isEditorVisible: Boolean = false,
    val editingSuggestionId: Long? = null,
    val isCreatingNew: Boolean = false,
    val draft: SuggestionDraft = SuggestionDraft(),
    val initial: SuggestionDraft = SuggestionDraft(),
    val showDiscardConfirm: Boolean = false,
    val sheetMode: EditorSheetMode = EditorSheetMode.View,
) {
    val isDirty: Boolean
        get() = draft != initial

    fun openForView(suggestion: Suggestion): SuggestionsScreenState {
        val draft = suggestion.toDraft()
        return copy(
            pendingDeleteId = null,
            isEditorVisible = true,
            editingSuggestionId = suggestion.id,
            isCreatingNew = false,
            draft = draft,
            initial = draft,
            showDiscardConfirm = false,
            sheetMode = EditorSheetMode.View,
        )
    }

    fun openForCreate(): SuggestionsScreenState {
        val draft = SuggestionDraft()
        return copy(
            pendingDeleteId = null,
            isEditorVisible = true,
            editingSuggestionId = null,
            isCreatingNew = true,
            draft = draft,
            initial = draft,
            showDiscardConfirm = false,
            sheetMode = EditorSheetMode.Edit,
        )
    }

    fun closeEditor(clearSelection: Boolean = true): SuggestionsScreenState =
        copy(
            isEditorVisible = false,
            showDiscardConfirm = false,
            sheetMode = EditorSheetMode.View,
            editingSuggestionId = if (clearSelection) null else editingSuggestionId,
            isCreatingNew = if (clearSelection) false else isCreatingNew,
        )
}

private const val NULL_ID: Long = -1L

internal val SuggestionsScreenStateSaver: Saver<SuggestionsScreenState, Any> =
    listSaver(
        save = { state ->
            listOf(
                state.pendingDeleteId ?: NULL_ID,
                state.isEditorVisible,
                state.editingSuggestionId ?: NULL_ID,
                state.isCreatingNew,
                state.draft.title,
                timeSlotNames(state.draft.timeSlots),
                state.draft.durationTag.name,
                state.draft.priority.name,
                state.draft.actionKind.name,
                state.draft.actionValue,
                state.draft.actionDisplay,
                state.initial.title,
                timeSlotNames(state.initial.timeSlots),
                state.initial.durationTag.name,
                state.initial.priority.name,
                state.initial.actionKind.name,
                state.initial.actionValue,
                state.initial.actionDisplay,
                state.showDiscardConfirm,
                state.sheetMode.name,
            )
        },
        restore = { list ->
            val pendingDeleteIdRaw = list[0] as Long
            val pendingDeleteId = pendingDeleteIdRaw.takeUnless { it == NULL_ID }
            val isEditorVisible = list[1] as Boolean

            val editingSuggestionIdRaw = list[2] as Long
            val editingSuggestionId = editingSuggestionIdRaw.takeUnless { it == NULL_ID }
            val isCreatingNew = list[3] as Boolean

            val draftTitle = list[4] as String
            val draftTimeSlots = parseTimeSlots(list[5])
            val draftDurationTag =
                parseEnumOrDefault(list[6], SuggestionDurationTag.Medium)
            val draftPriority =
                parseEnumOrDefault(list[7], SuggestionPriority.Normal)
            val draftActionKind =
                parseEnumOrDefault(list[8], SuggestionActionKind.None)
            val draftActionValue = list[9] as String
            val draftActionDisplay = list[10] as String

            val initialTitle = list[11] as String
            val initialTimeSlots = parseTimeSlots(list[12])
            val initialDurationTag =
                parseEnumOrDefault(list[13], SuggestionDurationTag.Medium)
            val initialPriority =
                parseEnumOrDefault(list[14], SuggestionPriority.Normal)
            val initialActionKind =
                parseEnumOrDefault(list[15], SuggestionActionKind.None)
            val initialActionValue = list[16] as String
            val initialActionDisplay = list[17] as String

            val showDiscardConfirm = list[18] as Boolean
            val sheetMode = parseEnumOrDefault(list[19], EditorSheetMode.View)

            SuggestionsScreenState(
                pendingDeleteId = pendingDeleteId,
                isEditorVisible = isEditorVisible,
                editingSuggestionId = editingSuggestionId,
                isCreatingNew = isCreatingNew,
                draft =
                    SuggestionDraft(
                        title = draftTitle,
                        timeSlots = draftTimeSlots,
                        durationTag = draftDurationTag,
                        priority = draftPriority,
                        actionKind = draftActionKind,
                        actionValue = draftActionValue,
                        actionDisplay = draftActionDisplay,
                    ),
                initial =
                    SuggestionDraft(
                        title = initialTitle,
                        timeSlots = initialTimeSlots,
                        durationTag = initialDurationTag,
                        priority = initialPriority,
                        actionKind = initialActionKind,
                        actionValue = initialActionValue,
                        actionDisplay = initialActionDisplay,
                    ),
                showDiscardConfirm = showDiscardConfirm,
                sheetMode = sheetMode,
            )
        },
    )

private fun Suggestion.toDraft(): SuggestionDraft {
    val (actionKind, actionValue, actionDisplay) = action.toDraftFields()
    return SuggestionDraft(
        title = title,
        timeSlots = normalizeTimeSlots(timeSlots),
        durationTag = durationTag,
        priority = priority,
        actionKind = actionKind,
        actionValue = actionValue,
        actionDisplay = actionDisplay,
    )
}

private fun SuggestionAction.toDraftFields(): Triple<SuggestionActionKind, String, String> {
    return when (this) {
        SuggestionAction.None -> Triple(SuggestionActionKind.None, "", "")
        is SuggestionAction.Url ->
            Triple(
                SuggestionActionKind.Url,
                url,
                display.orEmpty(),
            )

        is SuggestionAction.App ->
            Triple(
                SuggestionActionKind.App,
                packageName,
                display.orEmpty(),
            )
    }
}

private fun timeSlotNames(slots: Set<SuggestionTimeSlot>): List<String> = normalizeTimeSlots(slots).map { it.name }

private fun parseTimeSlots(raw: Any): Set<SuggestionTimeSlot> {
    val names = (raw as? List<*>)?.filterIsInstance<String>().orEmpty()
    val parsed =
        names
            .mapNotNull { runCatching { SuggestionTimeSlot.valueOf(it) }.getOrNull() }
            .toSet()
    return normalizeTimeSlots(parsed)
}

private inline fun <reified T : Enum<T>> parseEnumOrDefault(
    raw: Any,
    default: T,
): T {
    val name = raw as? String ?: return default
    return runCatching { enumValueOf<T>(name) }.getOrElse { default }
}
