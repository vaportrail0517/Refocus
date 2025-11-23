package com.example.refocus.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun GraceTimeDialog(
    currentMillis: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val maxGraceMillis = 10 * 60_000L
    val stepMillis = 30_000L
    val initialMillis = currentMillis.coerceIn(0L, maxGraceMillis)
    var sliderValue by remember(initialMillis) {
        mutableFloatStateOf(initialMillis.toFloat())
    }
    val currentLabel = formatDurationMillis(sliderValue.toLong())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("猶予時間") },
        text = {
            Column {
                Text(
                    text = "対象アプリから離れてから、何秒以内に戻れば同じセッションとみなすかを指定します。",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "現在: $currentLabel",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "0〜10分 / 30秒刻み",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Slider(
                    value = sliderValue,
                    onValueChange = { raw ->
                        val steps = (maxGraceMillis / stepMillis).toInt()
                        val index = (raw / stepMillis)
                            .toInt()
                            .coerceIn(0, steps)
                        val snapped = index * stepMillis
                        sliderValue = snapped.toFloat()
                    },
                    valueRange = 0f..maxGraceMillis.toFloat(),
                    steps = (maxGraceMillis / stepMillis).toInt() - 1,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(sliderValue.toLong())
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}

@Composable
fun PollingIntervalDialog(
    currentMillis: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    data class PollingOption(val ms: Long, val label: String, val description: String)

    val pollingOptions = remember {
        listOf(
            PollingOption(
                ms = 250L,
                label = "250 ms",
                description = "最も素早い反応。バッテリ負荷やや高め。"
            ),
            PollingOption(
                ms = 500L,
                label = "500 ms",
                description = "標準的なバランス。反応と電池のバランスが良い。"
            ),
            PollingOption(
                ms = 1000L,
                label = "1000 ms",
                description = "ややゆっくり。電池を少し節約したい場合。"
            ),
            PollingOption(
                ms = 2000L,
                label = "2000 ms",
                description = "最も省エネ。反応はゆっくりになる。"
            ),
        )
    }
    var selectedPollingMs by remember(currentMillis) {
        mutableLongStateOf(currentMillis)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("監視間隔") },
        text = {
            Column {
                Text(
                    text = "前面アプリをどれくらいの間隔でチェックするかを選びます。",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                pollingOptions.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedPollingMs = option.ms }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedPollingMs == option.ms,
                            onClick = { selectedPollingMs = option.ms }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = option.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedPollingMs) }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}

@Composable
fun FontRangeDialog(
    initialRange: ClosedFloatingPointRange<Float>,
    onConfirm: (ClosedFloatingPointRange<Float>) -> Unit,
    onDismiss: () -> Unit
) {
    val minFontSpLimit = 8f
    val maxFontSpLimit = 96f
    var fontRange by remember(initialRange) {
        mutableStateOf(
            initialRange.start.coerceIn(minFontSpLimit, maxFontSpLimit)..
                    initialRange.endInclusive.coerceIn(minFontSpLimit, maxFontSpLimit)
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("フォントサイズ") },
        text = {
            Column {
                Text(
                    text = "タイマーのフォントサイズ範囲を指定します。",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                RangeSlider(
                    value = fontRange,
                    onValueChange = { range ->
                        fontRange = range
                    },
                    valueRange = minFontSpLimit..maxFontSpLimit,
                    steps = (maxFontSpLimit - minFontSpLimit).toInt() - 1,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "最小: ${"%.1f".format(fontRange.start)} sp / 最大: ${
                        "%.1f".format(
                            fontRange.endInclusive
                        )
                    } sp",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(fontRange) }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}

@Composable
fun TimeToMaxDialog(
    currentMinutes: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var timeToMaxInput by remember(currentMinutes) {
        mutableStateOf(currentMinutes.toString())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("最大サイズになるまでの時間") },
        text = {
            Column {
                Text(
                    text = "フォントが最大サイズになるまでの時間（分）を指定します。",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = timeToMaxInput,
                    onValueChange = { timeToMaxInput = it },
                    label = { Text("時間（分）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val value = timeToMaxInput.toIntOrNull()
                    if (value != null) {
                        val clamped = value.coerceIn(1, 720)
                        onConfirm(clamped)
                    }
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}

@Composable
fun CorePermissionRequiredDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("権限が必要です")
        },
        text = {
            Text(
                "Refocus を動かすには「使用状況へのアクセス」と「他のアプリの上に表示」の 2 つの権限が必要です。" +
                        "上の「権限」セクションから、これらの権限を有効にしてください。"
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

@Composable
fun SuggestionFeatureRequiredDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("提案が無効になっています")
        },
        text = {
            Text(
                "「休憩の提案」を有効にするには「提案を表示する」がオンである必要があります。"
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}
