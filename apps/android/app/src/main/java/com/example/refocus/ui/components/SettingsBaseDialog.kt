package com.example.refocus.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 設定系ダイアログの共通土台．
 *
 * - タイトル
 * - 説明テキスト（任意）
 * - 本文コンテンツ（スライダー，ラジオボタン，テキストフィールドなど）
 * - 保存，キャンセル ボタン
 *
 * を一括で扱う．
 */
@Composable
fun SettingsBaseDialog(
    title: String,
    description: String? = null,
    confirmLabel: String = "保存",
    dismissLabel: String = "キャンセル",
    showDismissButton: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                content()
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            if (showDismissButton) {
                TextButton(onClick = onDismiss) {
                    Text(dismissLabel)
                }
            }
        }
    )
}
