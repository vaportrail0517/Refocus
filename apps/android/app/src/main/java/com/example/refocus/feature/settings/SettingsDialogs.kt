package com.example.refocus.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.refocus.core.util.formatDurationMillis
import com.example.refocus.core.util.formatDurationSeconds
import com.example.refocus.ui.components.InfoDialog
import com.example.refocus.ui.components.IntInputDialog
import com.example.refocus.ui.components.LongSliderDialog
import com.example.refocus.ui.components.RangeSliderDialog
import com.example.refocus.ui.components.SingleChoiceDialog

sealed interface SettingsDialogType {
    data object GraceTime : SettingsDialogType
    data object PollingInterval : SettingsDialogType
    data object FontRange : SettingsDialogType
    data object TimeToMax : SettingsDialogType
    data object CorePermissionRequired : SettingsDialogType
    data object SuggestionFeatureRequired : SettingsDialogType
    data object SuggestionTriggerTime : SettingsDialogType
    data object SuggestionForegroundStable : SettingsDialogType
    data object SuggestionCooldown : SettingsDialogType
    data object SuggestionTimeout : SettingsDialogType
    data object SuggestionInteractionLockout : SettingsDialogType
}

/**
 * 猶予時間（gracePeriodMillis）のダイアログ。
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
        description = "対象アプリから離れてから、何秒以内に戻れば同じセッションとみなすかを指定します。",
        min = 0L,
        max = maxGraceMillis,
        step = stepMillis,
        initial = currentMillis,
        valueLabel = { value -> formatDurationMillis(value) },
        hintLabel = "0〜10分 / 30秒刻み",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/**
 * 前面アプリ監視間隔（pollingIntervalMillis）のダイアログ。
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
            PollingOption(250L, "最も素早い反応。バッテリ負荷やや高め。"),
            PollingOption(500L, "標準的なバランス。反応と電池のバランスが良い。"),
            PollingOption(1000L, "ややゆっくり。電池を少し節約したい場合。"),
            PollingOption(2000L, "最も省エネ。反応はゆっくりになる。"),
        )
    }

    val initialOption = pollingOptions.minByOrNull { option ->
        kotlin.math.abs(option.ms - currentMillis)
    } ?: pollingOptions[1]

    SingleChoiceDialog(
        title = "監視間隔",
        description = "前面アプリをどれくらいの間隔でチェックするかを選びます。",
        options = pollingOptions,
        initialSelection = initialOption,
        optionLabel = { it.label },
        optionDescription = { it.description },
        onConfirm = { selected -> onConfirm(selected.ms) },
        onDismiss = onDismiss,
    )
}

/**
 * タイマーのフォントサイズ範囲のダイアログ。
 */
@Composable
fun FontRangeDialog(
    initialRange: ClosedFloatingPointRange<Float>,
    onConfirm: (ClosedFloatingPointRange<Float>) -> Unit,
    onDismiss: () -> Unit
) {
    val minFontSpLimit = 8f
    val maxFontSpLimit = 96f
    val clampedInitial = initialRange.start.coerceIn(minFontSpLimit, maxFontSpLimit)..
            initialRange.endInclusive.coerceIn(minFontSpLimit, maxFontSpLimit)

    RangeSliderDialog(
        title = "フォントサイズ",
        description = "タイマーのフォントサイズ範囲を指定します。",
        valueRange = minFontSpLimit..maxFontSpLimit,
        initialRange = clampedInitial,
        steps = (maxFontSpLimit - minFontSpLimit).toInt() - 1,
        labelFormatter = { range ->
            "最小: ${"%.1f".format(range.start)} sp / 最大: ${
                "%.1f".format(
                    range.endInclusive
                )
            } sp"
        },
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/**
 * タイマーが最大サイズになるまでの時間（分）のダイアログ。
 */
@Composable
fun TimeToMaxDialog(
    currentMinutes: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    IntInputDialog(
        title = "最大サイズになるまでの時間",
        description = "フォントが最大サイズになるまでの時間（分）を指定します。",
        label = "時間（分）",
        initialValue = currentMinutes,
        minValue = 1,
        maxValue = 720,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/**
 * コア権限が不足しているときのダイアログ。
 */
@Composable
fun CorePermissionRequiredDialog(
    onDismiss: () -> Unit
) {
    InfoDialog(
        title = "権限が必要です",
        description = "Refocus を動かすには「使用状況へのアクセス」と「他のアプリの上に表示」の 2 つの権限が必要です。上の「権限」セクションから、これらの権限を有効にしてください。",
        onDismiss = onDismiss,
    )
}

/**
 * 提案機能の依存関係を満たしていないときのダイアログ。
 */
@Composable
fun SuggestionFeatureRequiredDialog(
    onDismiss: () -> Unit
) {
    InfoDialog(
        title = "提案が無効になっています",
        description = "「休憩の提案」を有効にするには「提案を表示する」がオンである必要があります。",
        onDismiss = onDismiss,
    )
}

@Composable
fun SuggestionTriggerTimeDialog(
    currentSeconds: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    LongSliderDialog(
        title = "提案を出すまでのセッション継続時間",
        description = "対象アプリを連続して使い始めてから、どれくらい経過したら提案を行うかを設定します。",
        min = 60L,
        max = 60L * 60L,
        step = 60L,
        initial = currentSeconds.coerceIn(60, 60 * 60).toLong(),
        valueLabel = { seconds -> formatDurationSeconds(seconds.toInt()) },
        hintLabel = "1〜60分 / 1分刻み",
        onConfirm = { selectedSeconds ->
            onConfirm(selectedSeconds.toInt())
        },
        onDismiss = onDismiss,
    )
}

@Composable
fun SuggestionForegroundStableDialog(
    currentSeconds: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    LongSliderDialog(
        title = "前面アプリの安定時間",
        description = "対象アプリが連続して前面に表示されてから、どれくらい安定していたら提案を行うかを設定します。",
        min = 5L * 60L,
        max = 20L * 60L,
        step = 60L, // 1分刻み
        initial = currentSeconds.coerceIn(5 * 60, 20 * 60).toLong(),
        valueLabel = { seconds -> formatDurationSeconds(seconds.toInt()) },
        hintLabel = "5〜20分 / 1分刻み",
        onConfirm = { selectedSeconds ->
            onConfirm(selectedSeconds.toInt())
        },
        onDismiss = onDismiss,
    )
}

@Composable
fun SuggestionCooldownDialog(
    currentSeconds: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    LongSliderDialog(
        title = "次の提案までの間隔",
        description = "一度提案を出してから、次の提案を行うまでどれくらい間隔を空けるかを設定します。",
        min = 60L,
        max = 60L * 60L,
        step = 60L,
        initial = currentSeconds.coerceIn(60, 60 * 60).toLong(),
        valueLabel = { seconds -> formatDurationSeconds(seconds.toInt()) },
        hintLabel = "1〜60分 / 1分刻み",
        onConfirm = { selectedSeconds ->
            onConfirm(selectedSeconds.toInt())
        },
        onDismiss = onDismiss,
    )
}

@Composable
fun SuggestionTimeoutDialog(
    currentSeconds: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    LongSliderDialog(
        title = "提案カードを自動で閉じるまでの時間",
        description = "提案カードを表示してから自動で閉じるまでの時間を設定します。",
        min = 5L,
        max = 30L,
        step = 1L,
        initial = currentSeconds.coerceIn(5, 30).toLong(),
        valueLabel = { seconds -> formatDurationSeconds(seconds.toInt()) },
        hintLabel = "5〜30秒 / 1秒刻み",
        onConfirm = { selectedSeconds ->
            onConfirm(selectedSeconds.toInt())
        },
        onDismiss = onDismiss,
    )
}

@Composable
fun SuggestionInteractionLockoutDialog(
    currentMillis: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    LongSliderDialog(
        title = "提案表示直後の誤タップ防止時間",
        description = "提案カードを表示してから、誤タップで直ちに閉じてしまわないようにするためのロック時間を設定します。",
        min = 0L,
        max = 2000L,
        step = 100L,
        initial = currentMillis.coerceIn(0L, 2000L),
        valueLabel = { millis -> "${millis} ms" },
        hintLabel = "0〜2000ms / 100ms刻み",
        onConfirm = { selectedMillis ->
            onConfirm(selectedMillis)
        },
        onDismiss = onDismiss,
    )
}
