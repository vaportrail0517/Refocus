package com.example.refocus.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * 設定系ダイアログの共通土台。
 *
 * - タイトル
 * - 説明テキスト（任意）
 * - 本文コンテンツ（スライダー / ラジオボタン / テキストフィールドなど）
 * - 保存 / キャンセル ボタン
 *
 * を一括で扱う。
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
                    Spacer(modifier = Modifier.Companion.height(8.dp))
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
    var sliderValue by remember(clampedInitial) {
        mutableFloatStateOf(clampedInitial.toFloat())
    }
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
        // 現在値＋ヒント
        Row(
            modifier = Modifier.Companion.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Companion.CenterVertically
        ) {
            Text(
                text = "現在: ${valueLabel(currentValue)}",
                style = MaterialTheme.typography.bodyMedium
            )
            if (hintLabel != null) {
                Text(
                    text = hintLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.Companion.height(8.dp))
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
 * ラジオボタンで 1 つ選ぶ汎用ダイアログ。
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
                modifier = Modifier.Companion
                    .fillMaxWidth()
                    .clickable { selected = option }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.Companion.CenterVertically
            ) {
                RadioButton(
                    selected = selected == option,
                    onClick = { selected = option }
                )
                Spacer(modifier = Modifier.Companion.width(8.dp))
                Column {
                    Text(
                        text = optionLabel(option),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    optionDescription?.let { descFn ->
                        Text(
                            text = descFn(option),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Float の範囲を RangeSlider で選ぶ汎用ダイアログ。
 *
 * 例: フォント範囲など。
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
        Spacer(modifier = Modifier.Companion.height(8.dp))
        Text(
            text = labelFormatter(range),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/**
 * Int 値をテキスト入力で設定する汎用ダイアログ。
 *
 * 例: 「最大サイズになるまでの時間（分）」など。
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
            val value = textValue.toIntOrNull()
            if (value != null) {
                val clamped = value.coerceIn(minValue, maxValue)
                onConfirm(clamped)
            }
        },
        onDismiss = onDismiss,
    ) {
        OutlinedTextField(
            value = textValue,
            onValueChange = { textValue = it },
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Companion.Number
            ),
        )
    }
}

/**
 * OK だけの情報ダイアログ。
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
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DurationHmsPickerDialog(
    title: String,
    description: String? = null,
    initialSeconds: Int,
    minSeconds: Int = 60,
    maxSeconds: Int = 12 * 60 * 60,
    confirmLabel: String = "保存",
    dismissLabel: String = "キャンセル",
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val clampedInitial = initialSeconds.coerceIn(minSeconds, maxSeconds)

    var hour by remember { mutableStateOf(clampedInitial / 3600) }
    var minute by remember { mutableStateOf((clampedInitial % 3600) / 60) }
    var second by remember { mutableStateOf(clampedInitial % 60) }

    val maxHour = (maxSeconds / 3600).coerceAtLeast(0)

    SettingsBaseDialog(
        title = title,
        description = description,
        confirmLabel = confirmLabel,
        dismissLabel = dismissLabel,
        onConfirm = {
            val total = hour * 3600 + minute * 60 + second
            onConfirm(total.coerceIn(minSeconds, maxSeconds))
        },
        onDismiss = onDismiss,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WheelNumberPicker(
                label = "時間",
                valueRange = 0..maxHour,
                initialValue = hour,
                valueFormatter = { it.toString() },
                modifier = Modifier.weight(1f),
                onValueChanged = { hour = it },
            )

            Text(
                text = ":",
                modifier = Modifier.padding(horizontal = 8.dp),
                style = MaterialTheme.typography.titleMedium,
            )

            WheelNumberPicker(
                label = "分",
                valueRange = 0..59,
                initialValue = minute,
                valueFormatter = { "%02d".format(it) },
                modifier = Modifier.weight(1f),
                onValueChanged = { minute = it },
            )

            Text(
                text = ":",
                modifier = Modifier.padding(horizontal = 8.dp),
                style = MaterialTheme.typography.titleMedium,
            )

            WheelNumberPicker(
                label = "秒",
                valueRange = 0..59,
                initialValue = second,
                valueFormatter = { "%02d".format(it) },
                modifier = Modifier.weight(1f),
                onValueChanged = { second = it },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WheelNumberPicker(
    label: String,
    valueRange: IntRange,
    initialValue: Int,
    valueFormatter: (Int) -> String,
    modifier: Modifier = Modifier,
    onValueChanged: (Int) -> Unit,
) {
    val itemHeight: Dp = 40.dp
    val visibleCount = 5
    val paddingCount = visibleCount / 2

    val valuesCount = (valueRange.last - valueRange.first + 1).coerceAtLeast(1)
    val initialIndex = (initialValue - valueRange.first).coerceIn(0, valuesCount - 1)

    val state = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val fling = rememberSnapFlingBehavior(lazyListState = state)

    val itemHeightPx = with(LocalDensity.current) { itemHeight.toPx() }

    val selectedIndex by remember(state, itemHeightPx) {
        derivedStateOf {
            val offsetItems = ((state.firstVisibleItemScrollOffset + itemHeightPx / 2f) / itemHeightPx)
                .roundToInt()
            (state.firstVisibleItemIndex + offsetItems).coerceIn(0, valuesCount - 1)
        }
    }

    val selectedValue = valueRange.first + selectedIndex

    LaunchedEffect(state) {
        snapshotFlow { selectedValue }
            .distinctUntilChanged()
            .collect { onValueChanged(it) }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .height(itemHeight * visibleCount.toFloat())
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            LazyColumn(
                state = state,
                flingBehavior = fling,
                modifier = Modifier.fillMaxWidth(),
            ) {
                val totalItems = valuesCount + paddingCount * 2
                items(count = totalItems) { index ->
                    val valueIndex = index - paddingCount
                    val valueOrNull = if (valueIndex in 0 until valuesCount) {
                        valueRange.first + valueIndex
                    } else {
                        null
                    }

                    Box(
                        modifier = Modifier
                            .height(itemHeight)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (valueOrNull != null) {
                            val isSelected = valueOrNull == selectedValue
                            Text(
                                text = valueFormatter(valueOrNull),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                ),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(itemHeight)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
            )
        }
    }
}
