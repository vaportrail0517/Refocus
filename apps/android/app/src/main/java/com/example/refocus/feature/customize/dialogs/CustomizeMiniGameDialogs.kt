package com.example.refocus.feature.customize.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.refocus.core.model.MiniGameOrder
import com.example.refocus.ui.components.SingleChoiceDialog

@Composable
fun MiniGameOrderDialog(
    current: MiniGameOrder,
    onConfirm: (MiniGameOrder) -> Unit,
    onDismiss: () -> Unit,
) {
    data class Option(
        val order: MiniGameOrder,
        val label: String,
        val description: String,
    )

    val options =
        remember {
            listOf(
                Option(
                    MiniGameOrder.BeforeSuggestion,
                    "ミニゲーム → 提案",
                    "提案を表示する前にミニゲームを挟みます．ミニゲームを閉じると提案が表示されます．",
                ),
                Option(
                    MiniGameOrder.AfterSuggestion,
                    "提案 → ミニゲーム",
                    "提案を閉じた後にミニゲームを表示します．ミニゲームを閉じると元のアプリに戻ります．",
                ),
            )
        }

    val initial = options.firstOrNull { it.order == current } ?: options.first()

    SingleChoiceDialog(
        title = "提案とミニゲームの順番",
        description = "提案とミニゲームを表示する順番を選びます．",
        options = options,
        initialSelection = initial,
        optionLabel = { it.label },
        optionDescription = { it.description },
        onConfirm = { onConfirm(it.order) },
        onDismiss = onDismiss,
    )
}
