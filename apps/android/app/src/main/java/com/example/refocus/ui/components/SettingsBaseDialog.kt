package com.example.refocus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

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
    scrollableBody: Boolean = false,
    maxDialogHeightDp: Dp = 560.dp,
    maxDialogHeightRatio: Float = 0.80f,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val configuration = LocalConfiguration.current
    val ratio = maxDialogHeightRatio.coerceIn(0.0f, 1.0f)
    val maxDialogHeight =
        if (scrollableBody) {
            val maxByScreen = configuration.screenHeightDp.dp * ratio
            minOf(maxDialogHeightDp, maxByScreen)
        } else {
            Dp.Unspecified
        }

    val dialogModifier =
        if (scrollableBody) {
            Modifier.heightIn(max = maxDialogHeight)
        } else {
            Modifier
        }

    val scrollState = rememberScrollState()

    AlertDialog(
        modifier = dialogModifier,
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (scrollableBody) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxDialogHeight),
                ) {
                    val density = LocalDensity.current
                    val viewportHeightPx = with(density) { maxHeight.toPx() }
                    val maxScrollPx = scrollState.maxValue.toFloat()

                    Box(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                // スクロールバー分の余白を確保し，本文と重ならないようにする
                                .padding(end = 24.dp)
                                .verticalScroll(scrollState),
                        ) {
                            if (description != null) {
                                Text(
                                    text = description,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            content()
                        }

                        // スクロール可能な場合のみスクロールバーを表示する
                        if (maxScrollPx > 0f && viewportHeightPx > 0f) {
                            val contentHeightPx = viewportHeightPx + maxScrollPx
                            val rawThumbHeightPx = (viewportHeightPx * viewportHeightPx) / contentHeightPx
                            val thumbHeightDp =
                                maxOf(
                                    with(density) { rawThumbHeightPx.toDp() },
                                    24.dp,
                                )
                            val thumbHeightPx = with(density) { thumbHeightDp.toPx() }
                            val maxThumbOffsetPx = (viewportHeightPx - thumbHeightPx).coerceAtLeast(0f)
                            val thumbOffsetPx =
                                if (maxScrollPx <= 0f) {
                                    0f
                                } else {
                                    (scrollState.value.toFloat() / maxScrollPx) * maxThumbOffsetPx
                                }

                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .fillMaxHeight()
                                    .width(8.dp),
                            ) {
                                // track
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .fillMaxHeight()
                                        .width(2.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
                                        ),
                                )
                                // thumb
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .offset { IntOffset(0, thumbOffsetPx.roundToInt()) }
                                        .width(2.dp)
                                        .height(thumbHeightDp)
                                        .background(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                        ),
                                )
                            }
                        }
                    }
                }
            } else {
                Column {
                    if (description != null) {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    content()
                }
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
        },
    )
}
