package com.example.refocus.feature.customize.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.refocus.core.util.formatDurationMilliSeconds
import com.example.refocus.ui.components.LongSliderDialog
import com.example.refocus.ui.components.SingleChoiceDialog

/**
 * 猶予時間（gracePeriodMillis）のダイアログ．
 */
@Composable
fun GraceTimeDialog(
    currentMillis: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val maxGraceMillis = 10 * 60_000L
    val stepMillis = 30_000L

    LongSliderDialog(
        title = "猶予時間",
        description = "対象アプリから離れてから，何秒以内に戻れば同じセッションとみなすかを指定します．",
        min = 0L,
        max = maxGraceMillis,
        step = stepMillis,
        initial = currentMillis,
        valueLabel = { value -> formatDurationMilliSeconds(value, zeroLabel = "なし") },
        hintLabel = "0〜10分 / 30秒刻み",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/**
 * 前面アプリ監視間隔（pollingIntervalMillis）のダイアログ．
 */
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
            PollingOption(250L, "最も素早い反応．バッテリ負荷やや高め．"),
            PollingOption(500L, "標準的なバランス．反応と電池のバランスが良い．"),
            PollingOption(1000L, "ややゆっくり．電池を少し節約したい場合．"),
            PollingOption(2000L, "最も省エネ．反応はゆっくりになる．"),
        )
    }

    val initialOption = pollingOptions.minByOrNull { option ->
        kotlin.math.abs(option.ms - currentMillis)
    } ?: pollingOptions[1]

    SingleChoiceDialog(
        title = "監視間隔",
        description = "前面アプリをどれくらいの間隔でチェックするかを選びます．",
        options = pollingOptions,
        initialSelection = initialOption,
        optionLabel = { it.label },
        optionDescription = { it.description },
        onConfirm = { selected -> onConfirm(selected.ms) },
        onDismiss = onDismiss,
    )
}
