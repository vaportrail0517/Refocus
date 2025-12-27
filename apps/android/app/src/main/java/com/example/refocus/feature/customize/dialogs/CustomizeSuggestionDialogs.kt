package com.example.refocus.feature.customize.dialogs

import androidx.compose.runtime.Composable
import com.example.refocus.core.util.formatDurationSeconds
import com.example.refocus.ui.components.DurationHmsPickerDialog
import com.example.refocus.ui.components.LongSliderDialog

@Composable
fun SuggestionTriggerTimeDialog(
    currentSeconds: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    DurationHmsPickerDialog(
        title = "提案を出すまでのセッション継続時間",
        description = "対象アプリを連続して使い始めてから，どれくらい経過したら提案を行うかを設定します．",
        initialSeconds = currentSeconds.coerceIn(60, 60 * 30),
        minSeconds = 60,
        maxSeconds = 60 * 30,
        onConfirm = { seconds ->
            onConfirm(seconds)
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
        description = "対象アプリが連続して前面に表示されてから，どれくらい安定していたら提案を行うかを設定します．",
        min = 60L,
        max = 60L * 20L,
        step = 60L,
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
        description = "一度提案を出してから，次の提案を行うまでどれくらい間隔を空けるかを設定します．",
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
        description = "提案カードを表示してから自動で閉じるまでの時間を設定します．0秒を指定すると時間経過では閉じません．",
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
        description = "提案カードを表示してから，誤タップで直ちに閉じてしまわないようにするためのロック時間を設定します．",
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
