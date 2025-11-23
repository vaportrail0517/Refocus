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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
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
import com.example.refocus.core.util.formatDurationMillis
import com.example.refocus.ui.components.SettingsDialog

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

    SettingsDialog(
        title = "猶予時間",
        description = "対象アプリから離れてから、何秒以内に戻れば同じセッションとみなすかを指定します。",
        confirmLabel = "保存",
        dismissLabel = "キャンセル",
        onConfirm = { onConfirm(sliderValue.toLong()) },
        onDismiss = onDismiss,
    ) {
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
}

@Composable
fun PollingIntervalDialog(
    currentMillis: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    data class PollingOption(
        val ms: Long,
        val description: String,
    ) {
        val label: String get() = "$ms ms"
    }

    val pollingOptions = remember {
        listOf(
            PollingOption(250L, "最も素早い反応。バッテリ負荷やや高め。"),
            PollingOption(500L, "標準的なバランス。反応と電池のバランスが良い。"),
            PollingOption(1000L, "ややゆっくり。電池を少し節約したい場合。"),
            PollingOption(2000L, "最も省エネ。反応はゆっくりになる。"),
        )
    }
    var selectedPollingMs by remember(currentMillis) {
        mutableLongStateOf(currentMillis)
    }

    SettingsDialog(
        title = "監視間隔",
        description = "前面アプリをどれくらいの間隔でチェックするかを選びます。",
        confirmLabel = "保存",
        dismissLabel = "キャンセル",
        onConfirm = { onConfirm(selectedPollingMs) },
        onDismiss = onDismiss,
    ) {
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
            initialRange.start.coerceIn(
                minFontSpLimit,
                maxFontSpLimit
            )..initialRange.endInclusive.coerceIn(
                minFontSpLimit,
                maxFontSpLimit
            )
        )
    }

    SettingsDialog(
        title = "フォントサイズ",
        description = "タイマーのフォントサイズ範囲を指定します。",
        confirmLabel = "保存",
        dismissLabel = "キャンセル",
        onConfirm = { onConfirm(fontRange) },
        onDismiss = onDismiss,
    ) {
        RangeSlider(
            value = fontRange,
            onValueChange = { range -> fontRange = range },
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

    SettingsDialog(
        title = "最大サイズになるまでの時間",
        description = "フォントが最大サイズになるまでの時間（分）を指定します。",
        confirmLabel = "保存",
        dismissLabel = "キャンセル",
        onConfirm = {
            val value = timeToMaxInput.toIntOrNull()
            if (value != null) {
                val clamped = value.coerceIn(1, 720)
                onConfirm(clamped)
            }
        },
        onDismiss = onDismiss,
    ) {
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
}

@Composable
fun CorePermissionRequiredDialog(
    onDismiss: () -> Unit
) {
    SettingsDialog(
        title = "権限が必要です",
        description = "Refocus を動かすには「使用状況へのアクセス」と「他のアプリの上に表示」の 2 つの権限が必要です。上の「権限」セクションから、これらの権限を有効にしてください。",
        confirmLabel = "OK",
        dismissLabel = "",
        showDismissButton = false,
        onConfirm = onDismiss,
        onDismiss = onDismiss,
    )
}

@Composable
fun SuggestionFeatureRequiredDialog(
    onDismiss: () -> Unit
) {
    SettingsDialog(
        title = "提案が無効になっています",
        description = "「休憩の提案」を有効にするには「提案を表示する」がオンである必要があります。",
        confirmLabel = "OK",
        dismissLabel = "",
        showDismissButton = false,
        onConfirm = onDismiss,
        onDismiss = onDismiss,
    )
}