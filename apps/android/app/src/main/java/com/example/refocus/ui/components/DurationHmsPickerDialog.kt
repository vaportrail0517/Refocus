package com.example.refocus.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DurationHmsPickerDialog(
    title: String,
    description: String? = null,
    initialSeconds: Int,
    minSeconds: Int = 60,
    maxSeconds: Int = 12 * 60 * 60,
    allowZero: Boolean = false,
    confirmLabel: String = "保存",
    dismissLabel: String = "キャンセル",
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    // ホイールは「時間 0..23」「分 0..59」「秒 0..59」の表現に固定するため，
    // 24時間以上や 60分以上を表現できない．安全のため最大は 23:59:59 に丸める．
    val maxRepresentable = 23 * 3600 + 59 * 60 + 59
    val effectiveMax = maxSeconds.coerceAtMost(maxRepresentable).coerceAtLeast(0)
    val effectiveMin = minSeconds.coerceAtLeast(0).coerceAtMost(effectiveMax)

    val clampedInitial = initialSeconds.coerceIn(effectiveMin, effectiveMax)

    val showHour = effectiveMax >= 3600
    val showMinute = effectiveMax >= 60

    val initHour = if (showHour) (clampedInitial / 3600).coerceIn(0, 23) else 0
    val initMinute =
        when {
            !showMinute -> 0
            showHour -> ((clampedInitial % 3600) / 60).coerceIn(0, 59)
            else -> (clampedInitial / 60).coerceAtLeast(0)
        }
    val initSecond =
        when {
            !showMinute -> clampedInitial.coerceAtLeast(0)
            else -> (clampedInitial % 60).coerceIn(0, 59)
        }

    var hour by remember { mutableStateOf(initHour) }
    var minute by remember { mutableStateOf(initMinute) }
    var second by remember { mutableStateOf(initSecond) }

    // 現在の選択（hour/minute）に応じて，分・秒の上限を動的に変えることで，
    // 「見えている値が保存時に最大値へ丸められる」体験を防ぐ．
    val maxHour = if (showHour) (effectiveMax / 3600).coerceIn(0, 23) else 0

    val minuteMax by remember(showHour, showMinute, hour, effectiveMax) {
        derivedStateOf {
            if (!showMinute) {
                0
            } else if (!showHour) {
                (effectiveMax / 60).coerceAtLeast(0)
            } else {
                if (hour >= maxHour) {
                    ((effectiveMax - maxHour * 3600) / 60).coerceIn(0, 59)
                } else {
                    59
                }
            }
        }
    }

    val secondMax by remember(showHour, showMinute, hour, minute, effectiveMax, minuteMax) {
        derivedStateOf {
            if (!showMinute) {
                effectiveMax.coerceAtLeast(0)
            } else if (!showHour) {
                val maxM = (effectiveMax / 60).coerceAtLeast(0)
                if (minute >= maxM) {
                    (effectiveMax - maxM * 60).coerceIn(0, 59)
                } else {
                    59
                }
            } else {
                if (hour >= maxHour && minute >= minuteMax) {
                    (effectiveMax - maxHour * 3600 - minuteMax * 60).coerceIn(0, 59)
                } else {
                    59
                }
            }
        }
    }

    LaunchedEffect(showHour, showMinute, maxHour, minuteMax, secondMax) {
        if (!showHour && hour != 0) hour = 0
        if (!showMinute && minute != 0) minute = 0
        if (!showMinute) {
            second = second.coerceIn(0, effectiveMax)
        } else {
            hour = hour.coerceIn(0, maxHour)
            minute = minute.coerceIn(0, minuteMax)
            second = second.coerceIn(0, secondMax)
        }
    }

    SettingsBaseDialog(
        title = title,
        description = description,
        confirmLabel = confirmLabel,
        dismissLabel = dismissLabel,
        onConfirm = {
            val rawTotal = hour * 3600 + minute * 60 + second
            val zeroAdjusted = if (!allowZero && rawTotal == 0) 1 else rawTotal
            onConfirm(zeroAdjusted.coerceIn(effectiveMin, effectiveMax))
        },
        onDismiss = onDismiss,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showHour) {
                WheelNumberPicker(
                    label = "時間",
                    valueRange = 0..maxHour,
                    value = hour,
                    valueFormatter = { "%02d".format(it) },
                    modifier = Modifier.weight(1f),
                    onValueChanged = { hour = it },
                )

                Text(
                    text = ":",
                    modifier = Modifier.padding(horizontal = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            if (showMinute) {
                WheelNumberPicker(
                    label = "分",
                    valueRange = 0..minuteMax,
                    value = minute,
                    valueFormatter = { "%02d".format(it) },
                    modifier = Modifier.weight(1f),
                    onValueChanged = { minute = it },
                )

                Text(
                    text = ":",
                    modifier = Modifier.padding(horizontal = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            WheelNumberPicker(
                label = "秒",
                valueRange = 0..secondMax,
                value = second,
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
    value: Int,
    valueFormatter: (Int) -> String,
    modifier: Modifier = Modifier,
    onValueChanged: (Int) -> Unit,
) {
    val itemHeight: Dp = 40.dp
    // 3 行表示（上: 前の値 / 中: 選択中 / 下: 次の値）
    val visibleCount = 3
    val paddingCount = visibleCount / 2

    val valuesCount = (valueRange.last - valueRange.first + 1).coerceAtLeast(1)
    val clampedValue = value.coerceIn(valueRange.first, valueRange.last)
    val valueIndex = (clampedValue - valueRange.first).coerceIn(0, valuesCount - 1)

    // 端が存在しないように見せるために巨大なインデックス空間で循環させる
    val middle = Int.MAX_VALUE / 2
    val alignedMiddle = middle - floorMod(middle, valuesCount)
    val targetFirstVisibleIndex = alignedMiddle + valueIndex - paddingCount

    val state =
        remember(valuesCount) {
            // valuesCount が変わった（＝表示レンジが変わった）場合は state を作り直して選択値を揃える
            LazyListState(firstVisibleItemIndex = targetFirstVisibleIndex)
        }
    val fling = rememberSnapFlingBehavior(lazyListState = state)

    // 「見た目で中央に来ている行」を確実に選択値として扱うため，
    // viewport の中央に最も近い item を選ぶ．（scrollOffset 依存の計算よりズレに強い）
    val selectedIndex by remember(state, valuesCount) {
        derivedStateOf {
            val layoutInfo = state.layoutInfo
            val visible = layoutInfo.visibleItemsInfo
            if (visible.isEmpty()) {
                state.firstVisibleItemIndex + paddingCount
            } else {
                val viewportCenter =
                    (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                visible
                    .minBy { item ->
                        kotlin.math.abs((item.offset + item.size / 2) - viewportCenter)
                    }.index
            }
        }
    }

    val selectedValue = valueRange.first + floorMod(selectedIndex, valuesCount)

    // スクロール位置から選択値を通知する．高速スクロールでも安定するよう distinctUntilChanged をかける．
    LaunchedEffect(state, valuesCount, valueRange.first, valueRange.last) {
        snapshotFlow { valueRange.first + floorMod(selectedIndex, valuesCount) }
            .distinctUntilChanged()
            .collect { v ->
                onValueChanged(v.coerceIn(valueRange.first, valueRange.last))
            }
    }

    // 外部から value が変わった場合（例: 他列の変更で clamp された）だけ，
    // スクロールが止まったタイミングで表示位置を合わせる．
    //
    // ここで firstVisibleItemIndex を使って判定すると，
    // 「中央に見えている値」とズレて無限に巻き戻ることがあるため，
    // 必ず selectedValue（中央に最も近い item）と比較する．
    LaunchedEffect(valuesCount, valueRange.first, valueRange.last, clampedValue) {
        if (valuesCount <= 1) return@LaunchedEffect
        if (state.isScrollInProgress) return@LaunchedEffect

        val currentSelected = valueRange.first + floorMod(selectedIndex, valuesCount)
        if (currentSelected == clampedValue) return@LaunchedEffect

        val desiredOffsetInCycle = (clampedValue - valueRange.first).coerceIn(0, valuesCount - 1)
        val base = selectedIndex - floorMod(selectedIndex, valuesCount)

        val c0 = base + desiredOffsetInCycle
        val c1 = c0 + valuesCount
        val c2 = c0 - valuesCount
        val target = listOf(c0, c1, c2).minBy { kotlin.math.abs(it - selectedIndex) }

        // 中央に来るように paddingCount 分だけずらして表示する
        state.scrollToItem(target - paddingCount)
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
            modifier =
                Modifier
                    .height(itemHeight * visibleCount.toFloat())
                    .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            // 選択中の帯（背景）
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
            )

            LazyColumn(
                state = state,
                flingBehavior = fling,
                modifier = Modifier.fillMaxWidth(),
                userScrollEnabled = valuesCount > 1,
            ) {
                items(count = Int.MAX_VALUE) { index ->
                    val vIndex = floorMod(index, valuesCount)
                    val v = valueRange.first + vIndex
                    val isSelected = vIndex == floorMod(selectedIndex, valuesCount)

                    Box(
                        modifier =
                            Modifier
                                .height(itemHeight)
                                .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = valueFormatter(v),
                            style =
                                if (isSelected) {
                                    MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                } else {
                                    MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                    )
                                },
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

private fun floorMod(
    value: Int,
    modulus: Int,
): Int {
    val r = value % modulus
    return if (r >= 0) r else r + modulus
}
