package com.example.refocus.feature.settings

import androidx.compose.runtime.Composable
import com.example.refocus.ui.components.InfoDialog
import com.example.refocus.ui.components.SettingsBaseDialog

sealed interface SettingsDialogType {
    data object CorePermissionRequired : SettingsDialogType

    data object SuggestionFeatureRequired : SettingsDialogType

    data object AppDataReset : SettingsDialogType
}

/**
 * コア権限が不足しているときのダイアログ。
 */
@Composable
fun CorePermissionRequiredDialog(
    onStartPermissionFixFlow: () -> Unit,
    onDismiss: () -> Unit,
) {
    SettingsBaseDialog(
        title = "権限が必要です",
        description =
            """Refocus を動かすには「使用状況へのアクセス」と「他のアプリの上に表示」の 2 つの権限が必要です。「権限を設定する」をタップすると、権限を 1 つずつ案内する画面に進みます。        """
                .trimIndent(),
        confirmLabel = "権限を設定する",
        dismissLabel = "閉じる",
        onConfirm = onStartPermissionFixFlow,
        onDismiss = onDismiss,
    )
}

/**
 * 提案機能の依存関係を満たしていないときのダイアログ。
 */
@Composable
fun SuggestionFeatureRequiredDialog(onDismiss: () -> Unit) {
    InfoDialog(
        title = "提案が無効になっています",
        description = "「休憩の提案」を有効にするには「提案を表示する」がオンである必要があります。",
        onDismiss = onDismiss,
    )
}

@Composable
fun AppDataResetDialog(
    onResetAllData: () -> Unit,
    onDismiss: () -> Unit,
) {
    SettingsBaseDialog(
        title = "アプリの初期化",
        description = "削除したデータを復元することは出来ません．本当に削除しますか？",
        confirmLabel = "削除する",
        dismissLabel = "キャンセル",
        onConfirm = onResetAllData,
        onDismiss = onDismiss,
    )
}
