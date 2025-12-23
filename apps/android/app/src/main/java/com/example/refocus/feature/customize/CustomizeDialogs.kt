package com.example.refocus.feature.customize

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.refocus.core.model.TimerColorMode
import com.example.refocus.core.model.TimerGrowthMode
import com.example.refocus.core.model.TimerTimeMode
import com.example.refocus.core.util.formatDurationMilliSeconds
import com.example.refocus.core.util.formatDurationSeconds
import com.example.refocus.ui.components.ColorPickerDialog
import com.example.refocus.ui.components.IntInputDialog
import com.example.refocus.ui.components.LongSliderDialog
import com.example.refocus.ui.components.RangeSliderDialog
import com.example.refocus.ui.components.SingleChoiceDialog

sealed interface CustomizeDialogType {
    data object GraceTime : CustomizeDialogType
    data object PollingInterval : CustomizeDialogType
    data object FontRange : CustomizeDialogType
    data object TimeToMax : CustomizeDialogType
    data object TimerTimeDisplayMode : CustomizeDialogType
    data object SuggestionTriggerTime : CustomizeDialogType
    data object SuggestionForegroundStable : CustomizeDialogType
    data object SuggestionCooldown : CustomizeDialogType
    data object SuggestionTimeout : CustomizeDialogType
    data object SuggestionInteractionLockout : CustomizeDialogType
    data object GrowthMode : CustomizeDialogType
    data object ColorMode : CustomizeDialogType
    data object FixedColor : CustomizeDialogType
    data object GradientStartColor : CustomizeDialogType
    data object GradientMiddleColor : CustomizeDialogType
    data object GradientEndColor : CustomizeDialogType
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
        valueLabel = { value -> formatDurationMilliSeconds(value, zeroLabel = "なし") },
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

@Composable
fun TimerTimeModeDialog(
    current: TimerTimeMode,
    onConfirm: (TimerTimeMode) -> Unit,
    onDismiss: () -> Unit,
) {
    data class Option(
        val mode: TimerTimeMode,
        val label: String,
        val description: String,
    )

    val options = remember {
        listOf(
            Option(
                TimerTimeMode.SessionElapsed,
                "セッションの経過時間",
                "今開いている対象アプリの論理セッションがどれくらい続いているかを表示します。"
            ),
            Option(
                TimerTimeMode.TodayThisTarget,
                "このアプリの今日の累計使用時間",
                "この対象アプリを今日どれくらい使ったかの合計を表示します。"
            ),
            Option(
                TimerTimeMode.TodayAllTargets,
                "全対象アプリの今日の累計使用時間",
                "対象アプリ全体を今日どれくらい使ったかの合計を表示します。"
            ),
        )
    }

    val initial = options.firstOrNull { it.mode == current } ?: options.first()

    SingleChoiceDialog(
        title = "タイマーに表示する時間",
        description = "オーバーレイに表示する時間の種類を選びます。",
        options = options,
        initialSelection = initial,
        optionLabel = { it.label },
        optionDescription = { it.description },
        onConfirm = { onConfirm(it.mode) },
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
        max = 60L * 30L,
        step = 60L,
        initial = currentSeconds.coerceIn(60, 60 * 60).toLong(),
        valueLabel = { seconds -> formatDurationSeconds(seconds) },
        hintLabel = "1〜30分 / 1分刻み",
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
        min = 60L,
        max = 60L * 20L,
        step = 60L, // 1分刻み
        initial = currentSeconds.coerceIn(5 * 60, 20 * 60).toLong(),
        valueLabel = { seconds -> formatDurationSeconds(seconds) },
        hintLabel = "1〜20分 / 1分刻み",
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
        max = 60L * 30L,
        step = 60L,
        initial = currentSeconds.coerceIn(60, 60 * 60).toLong(),
        valueLabel = { seconds -> formatDurationSeconds(seconds) },
        hintLabel = "1〜30分 / 1分刻み",
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
        description = "提案カードを表示してから自動で閉じるまでの時間を設定します。0秒を指定すると時間経過では閉じません。",
        min = 0L,
        max = 30L,
        step = 1L,
        initial = currentSeconds.coerceIn(0, 30).toLong(),
        valueLabel = { seconds ->
            formatDurationSeconds(
                seconds,
                zeroLabel = "時間経過で閉じない"
            )
        },
        hintLabel = "0〜30秒 / 1秒刻み",
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

@Composable
fun GrowthModeDialog(
    current: TimerGrowthMode,
    onConfirm: (TimerGrowthMode) -> Unit,
    onDismiss: () -> Unit,
) {
    data class GrowthOption(
        val mode: TimerGrowthMode,
        val label: String,
        val description: String,
    )

    val options = remember {
        listOf(
            GrowthOption(
                TimerGrowthMode.Linear,
                "線形",
                "時間に比例して一定のペースで大きくなります。"
            ),
            GrowthOption(
                TimerGrowthMode.FastToSlow,
                "スローイン（初め速く→だんだん遅く）",
                "序盤でぐっと大きくなり、その後はゆっくり変化します。"
            ),
            GrowthOption(
                TimerGrowthMode.SlowToFast,
                "スローアウト（初め遅く→だんだん速く）",
                "最初は控えめで、長く使うほど目立つようになります。"
            ),
            GrowthOption(
                TimerGrowthMode.SlowFastSlow,
                "スローインアウト",
                "真ん中の時間帯で一番ペースが速くなります。"
            ),
        )
    }

    val initial = options.firstOrNull { it.mode == current } ?: options.first()

    SingleChoiceDialog(
        title = "タイマーの成長モード",
        description = "タイマーの文字サイズが時間に応じてどのように変化するかを選びます。",
        options = options,
        initialSelection = initial,
        optionLabel = { it.label },
        optionDescription = { it.description },
        onConfirm = { onConfirm(it.mode) },
        onDismiss = onDismiss,
    )
}

@Composable
fun ColorModeDialog(
    current: TimerColorMode,
    onConfirm: (TimerColorMode) -> Unit,
    onDismiss: () -> Unit,
) {
    data class ColorModeOption(
        val mode: TimerColorMode,
        val label: String,
        val description: String,
    )

    val options = remember {
        listOf(
            ColorModeOption(
                TimerColorMode.Fixed,
                "単色",
                "タイマーの背景色を一色で固定します。"
            ),
            ColorModeOption(
                TimerColorMode.GradientTwo,
                "2色グラデーション",
                "開始色から終了色へ、時間に応じて滑らかに変化します。"
            ),
            ColorModeOption(
                TimerColorMode.GradientThree,
                "3色グラデーション",
                "開始色 → 中間色 → 終了色と、時間に応じて変化します。"
            ),
        )
    }

    val initial = options.firstOrNull { it.mode == current } ?: options.first()

    SingleChoiceDialog(
        title = "タイマーの色モード",
        description = "時間に応じたタイマー背景色の変化方法を選びます。",
        options = options,
        initialSelection = initial,
        optionLabel = { it.label },
        optionDescription = { it.description },
        onConfirm = { onConfirm(it.mode) },
        onDismiss = onDismiss,
    )
}

@Composable
fun FixedColorDialog(
    currentColorArgb: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ColorPickerDialog(
        title = "単色モードの色",
        description = "タイマー背景に使う単色を選びます。",
        initialColorArgb = currentColorArgb.takeIf { it != 0 },
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Composable
fun GradientStartColorDialog(
    currentColorArgb: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ColorPickerDialog(
        title = "グラデーション開始色",
        description = "タイマー利用開始直後に使う色を選びます。",
        initialColorArgb = currentColorArgb.takeIf { it != 0 },
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Composable
fun GradientMiddleColorDialog(
    currentColorArgb: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ColorPickerDialog(
        title = "グラデーション中間色",
        description = "タイマーのフォントサイズが中間のときに使う色を選びます。",
        initialColorArgb = currentColorArgb.takeIf { it != 0 },
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Composable
fun GradientEndColorDialog(
    currentColorArgb: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ColorPickerDialog(
        title = "グラデーション終了色",
        description = "タイマーが最大サイズになったときに使う色を選びます。",
        initialColorArgb = currentColorArgb.takeIf { it != 0 },
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}
