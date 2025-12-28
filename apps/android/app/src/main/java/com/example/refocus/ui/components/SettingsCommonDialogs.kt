package com.example.refocus.ui.components

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun LongSliderDialog(
    title: String,
    description: String? = null,
    min: Long,
    max: Long,
    step: Long,
    initial: Long,
    valueLabel: (Long) -> String,
    hintLabel: String? = null,
    confirmLabel: String = "保存",
    dismissLabel: String = "キャンセル",
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val clampedInitial = initial.coerceIn(min, max)
    var sliderValue by remember(clampedInitial) { mutableFloatStateOf(clampedInitial.toFloat()) }
    val currentValue = sliderValue.toLong()
    val stepsCount = ((max - min) / step).toInt().coerceAtLeast(0)

    SettingsBaseDialog(
        title = title,
        description = description,
        confirmLabel = confirmLabel,
        dismissLabel = dismissLabel,
        onConfirm = { onConfirm(currentValue) },
        onDismiss = onDismiss,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "現在: ${valueLabel(currentValue)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (hintLabel != null) {
                Text(
                    text = hintLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Slider(
            value = sliderValue,
            onValueChange = { raw ->
                // min..max の中で step ごとの最近傍にスナップ
                val offset = (raw.toLong() - min).coerceIn(0L, max - min)
                val index = (offset / step).toInt()
                val snapped = min + index * step
                sliderValue = snapped.toFloat()
            },
            valueRange = min.toFloat()..max.toFloat(),
            steps = if (stepsCount > 0) stepsCount - 1 else 0,
        )
    }
}

/**
 * ラジオボタンで 1 つ選ぶ汎用ダイアログ．
 *
 * options: 選択肢のリスト
 * optionLabel: 表示名
 * optionDescription: サブテキスト（任意）
 */
@Composable
fun <T> SingleChoiceDialog(
    title: String,
    description: String? = null,
    options: List<T>,
    initialSelection: T,
    optionLabel: (T) -> String,
    optionDescription: ((T) -> String)? = null,
    confirmLabel: String = "保存",
    dismissLabel: String = "キャンセル",
    onConfirm: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember(initialSelection) { mutableStateOf(initialSelection) }

    SettingsBaseDialog(
        title = title,
        description = description,
        confirmLabel = confirmLabel,
        dismissLabel = dismissLabel,
        onConfirm = { onConfirm(selected) },
        onDismiss = onDismiss,
    ) {
        options.forEach { option ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { selected = option }
                        .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = selected == option,
                    onClick = { selected = option },
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = optionLabel(option),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    optionDescription?.let { descFn ->
                        Text(
                            text = descFn(option),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Float の範囲を RangeSlider で選ぶ汎用ダイアログ．
 */
@Composable
fun RangeSliderDialog(
    title: String,
    description: String? = null,
    valueRange: ClosedFloatingPointRange<Float>,
    initialRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    labelFormatter: (ClosedFloatingPointRange<Float>) -> String,
    confirmLabel: String = "保存",
    dismissLabel: String = "キャンセル",
    onConfirm: (ClosedFloatingPointRange<Float>) -> Unit,
    onDismiss: () -> Unit,
) {
    var range by remember(initialRange) { mutableStateOf(initialRange) }

    SettingsBaseDialog(
        title = title,
        description = description,
        confirmLabel = confirmLabel,
        dismissLabel = dismissLabel,
        onConfirm = { onConfirm(range) },
        onDismiss = onDismiss,
    ) {
        RangeSlider(
            value = range,
            onValueChange = { newRange -> range = newRange },
            valueRange = valueRange,
            steps = steps,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = labelFormatter(range),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/**
 * Int 値をテキスト入力で設定する汎用ダイアログ．
 */
@Composable
fun IntInputDialog(
    title: String,
    description: String? = null,
    label: String,
    initialValue: Int,
    minValue: Int,
    maxValue: Int,
    confirmLabel: String = "保存",
    dismissLabel: String = "キャンセル",
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var textValue by remember(initialValue) { mutableStateOf(initialValue.toString()) }

    SettingsBaseDialog(
        title = title,
        description = description,
        confirmLabel = confirmLabel,
        dismissLabel = dismissLabel,
        onConfirm = {
            val value = textValue.toIntOrNull() ?: return@SettingsBaseDialog
            onConfirm(value.coerceIn(minValue, maxValue))
        },
        onDismiss = onDismiss,
    ) {
        OutlinedTextField(
            value = textValue,
            onValueChange = { textValue = it },
            label = { Text(label) },
            singleLine = true,
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                ),
        )
    }
}

/**
 * OK だけの情報ダイアログ．
 */
@Composable
fun InfoDialog(
    title: String,
    description: String,
    confirmLabel: String = "OK",
    onDismiss: () -> Unit,
) {
    SettingsBaseDialog(
        title = title,
        description = description,
        confirmLabel = confirmLabel,
        dismissLabel = "",
        showDismissButton = false,
        onConfirm = onDismiss,
        onDismiss = onDismiss,
    )
}
