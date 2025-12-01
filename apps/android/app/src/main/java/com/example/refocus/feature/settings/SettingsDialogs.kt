package com.example.refocus.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.refocus.core.model.OverlayColorMode
import com.example.refocus.core.model.OverlayGrowthMode
import com.example.refocus.core.util.formatDurationMillis
import com.example.refocus.core.util.formatDurationSeconds
import com.example.refocus.ui.components.ColorPickerDialog
import com.example.refocus.ui.components.InfoDialog
import com.example.refocus.ui.components.IntInputDialog
import com.example.refocus.ui.components.LongSliderDialog
import com.example.refocus.ui.components.RangeSliderDialog
import com.example.refocus.ui.components.SettingsBaseDialog
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
    data object GrowthMode : SettingsDialogType
    data object ColorMode : SettingsDialogType
    data object FixedColor : SettingsDialogType
    data object GradientStartColor : SettingsDialogType
    data object GradientMiddleColor : SettingsDialogType
    data object GradientEndColor : SettingsDialogType
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
    onStartPermissionFixFlow: () -> Unit,
    onDismiss: () -> Unit
) {
    SettingsBaseDialog(
        title = "権限が必要です",
        description = """Refocus を動かすには「使用状況へのアクセス」と「他のアプリの上に表示」の 2 つの権限が必要です。「権限を設定する」をタップすると、権限を 1 つずつ案内する画面に進みます。        """.trimIndent(),
        confirmLabel = "権限を設定する",
        dismissLabel = "閉じる",
        onConfirm = onStartPermissionFixFlow,
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

@Composable
fun GrowthModeDialog(
    current: OverlayGrowthMode,
    onConfirm: (OverlayGrowthMode) -> Unit,
    onDismiss: () -> Unit,
) {
    data class GrowthOption(
        val mode: OverlayGrowthMode,
        val label: String,
        val description: String,
    )

    val options = remember {
        listOf(
            GrowthOption(
                OverlayGrowthMode.Linear,
                "線形",
                "時間に比例して一定のペースで大きくなります。"
            ),
            GrowthOption(
                OverlayGrowthMode.FastToSlow,
                "スローイン（初め速く→だんだん遅く）",
                "序盤でぐっと大きくなり、その後はゆっくり変化します。"
            ),
            GrowthOption(
                OverlayGrowthMode.SlowToFast,
                "スローアウト（初め遅く→だんだん速く）",
                "最初は控えめで、長く使うほど目立つようになります。"
            ),
            GrowthOption(
                OverlayGrowthMode.SlowFastSlow,
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
    current: OverlayColorMode,
    onConfirm: (OverlayColorMode) -> Unit,
    onDismiss: () -> Unit,
) {
    data class ColorModeOption(
        val mode: OverlayColorMode,
        val label: String,
        val description: String,
    )

    val options = remember {
        listOf(
            ColorModeOption(
                OverlayColorMode.Fixed,
                "単色",
                "タイマーの背景色を一色で固定します。"
            ),
            ColorModeOption(
                OverlayColorMode.GradientTwo,
                "2色グラデーション",
                "開始色から終了色へ、時間に応じて滑らかに変化します。"
            ),
            ColorModeOption(
                OverlayColorMode.GradientThree,
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
