package com.example.refocus.feature.customize

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.refocus.core.model.TimerColorMode
import com.example.refocus.core.model.TimerGrowthMode
import com.example.refocus.core.model.TimerTimeMode
import com.example.refocus.core.model.TimerVisualTimeBasis
import com.example.refocus.ui.components.IntInputDialog
import com.example.refocus.ui.components.RangeSliderDialog
import com.example.refocus.ui.components.SingleChoiceDialog

/**
 * タイマーのフォントサイズ範囲のダイアログ．
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
        description = "タイマーのフォントサイズ範囲を指定します．",
        valueRange = minFontSpLimit..maxFontSpLimit,
        initialRange = clampedInitial,
        steps = (maxFontSpLimit - minFontSpLimit).toInt() - 1,
        labelFormatter = { range ->
            "最小: ${"%.1f".format(range.start)} sp / 最大: ${"%.1f".format(range.endInclusive)} sp"
        },
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/**
 * タイマーが最大サイズになるまでの時間（分）のダイアログ．
 */
@Composable
fun TimeToMaxDialog(
    currentMinutes: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    IntInputDialog(
        title = "最大サイズになるまでの時間",
        description = "フォントが最大サイズになるまでの時間（分）を指定します．",
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
                "今開いている対象アプリの論理セッションがどれくらい続いているかを表示します．"
            ),
            Option(
                TimerTimeMode.TodayThisTarget,
                "このアプリの今日の累計使用時間",
                "この対象アプリを今日どれくらい使ったかの合計を表示します．"
            ),
            Option(
                TimerTimeMode.TodayAllTargets,
                "全対象アプリの今日の累計使用時間",
                "対象アプリ全体を今日どれくらい使ったかの合計を表示します．"
            ),
        )
    }

    val initial = options.firstOrNull { it.mode == current } ?: options.first()

    SingleChoiceDialog(
        title = "タイマーに表示する時間",
        description = "オーバーレイに表示する時間の種類を選びます．",
        options = options,
        initialSelection = initial,
        optionLabel = { it.label },
        optionDescription = { it.description },
        onConfirm = { onConfirm(it.mode) },
        onDismiss = onDismiss,
    )
}

@Composable
fun TimerVisualTimeBasisDialog(
    current: TimerVisualTimeBasis,
    onConfirm: (TimerVisualTimeBasis) -> Unit,
    onDismiss: () -> Unit,
) {
    data class Option(
        val basis: TimerVisualTimeBasis,
        val label: String,
        val description: String,
    )

    val options = remember {
        listOf(
            Option(
                TimerVisualTimeBasis.SessionElapsed,
                "セッション経過時間（論理）",
                "色とサイズの変化を，論理セッションの経過時間に基づいて行います．新しいセッション開始で 0 から始まります．"
            ),
            Option(
                TimerVisualTimeBasis.FollowDisplayTime,
                "表示している時間",
                "タイマーに表示している時間と同じ基準で，色とサイズが変化します．"
            ),
        )
    }

    val initial = options.firstOrNull { it.basis == current } ?: options.first()

    SingleChoiceDialog(
        title = "色とサイズの変化の基準",
        description = "タイマーの色やサイズが，どの時間を基準に変化するかを選びます．",
        options = options,
        initialSelection = initial,
        optionLabel = { it.label },
        optionDescription = { it.description },
        onConfirm = { onConfirm(it.basis) },
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
                "時間に比例して一定のペースで大きくなります．"
            ),
            GrowthOption(
                TimerGrowthMode.FastToSlow,
                "スローイン（初め速く→だんだん遅く）",
                "序盤でぐっと大きくなり，その後はゆっくり変化します．"
            ),
            GrowthOption(
                TimerGrowthMode.SlowToFast,
                "スローアウト（初め遅く→だんだん速く）",
                "最初は控えめで，長く使うほど目立つようになります．"
            ),
            GrowthOption(
                TimerGrowthMode.SlowFastSlow,
                "スローインアウト",
                "真ん中の時間帯で一番ペースが速くなります．"
            ),
        )
    }

    val initial = options.firstOrNull { it.mode == current } ?: options.first()

    SingleChoiceDialog(
        title = "タイマーの成長モード",
        description = "タイマーの文字サイズが時間に応じてどのように変化するかを選びます．",
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
                "タイマーの背景色を一色で固定します．"
            ),
            ColorModeOption(
                TimerColorMode.GradientTwo,
                "2色グラデーション",
                "開始色から終了色へ，時間に応じて滑らかに変化します．"
            ),
            ColorModeOption(
                TimerColorMode.GradientThree,
                "3色グラデーション",
                "開始色 → 中間色 → 終了色と，時間に応じて変化します．"
            ),
        )
    }

    val initial = options.firstOrNull { it.mode == current } ?: options.first()

    SingleChoiceDialog(
        title = "タイマーの色モード",
        description = "時間に応じたタイマー背景色の変化方法を選びます．",
        options = options,
        initialSelection = initial,
        optionLabel = { it.label },
        optionDescription = { it.description },
        onConfirm = { onConfirm(it.mode) },
        onDismiss = onDismiss,
    )
}
