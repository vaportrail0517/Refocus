package com.example.refocus.core.util

import java.util.Locale

enum class DisplayUnit {
    SECOND,
    MINUTE,
    HOUR,
}

enum class DurationStyle {
    JapaneseUnit, // 例: "1時間2分3秒"
    Colon, // 例: "1:02:03" or "02:03"
}

data class DurationFormatOptions(
    val style: DurationStyle = DurationStyle.JapaneseUnit,
    val maxUnit: DisplayUnit = DisplayUnit.HOUR,
    val minUnit: DisplayUnit = DisplayUnit.SECOND,
    val zeroLabel: String = "",
    val colonSeparator: String = ":",
    val labels: Map<DisplayUnit, String> =
        mapOf(
            DisplayUnit.HOUR to "時間",
            DisplayUnit.MINUTE to "分",
            DisplayUnit.SECOND to "秒",
        ),
)

private fun formatDurationCore(
    totalSeconds: Long,
    options: DurationFormatOptions,
): String {
    if (totalSeconds <= 0L && options.zeroLabel.isNotBlank()) return options.zeroLabel

    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    fun DisplayUnit.enabled(): Boolean {
        val min = options.minUnit.ordinal
        val max = options.maxUnit.ordinal
        return this.ordinal in min..max
    }

    return when (options.style) {
        DurationStyle.Colon -> {
            if (DisplayUnit.HOUR.enabled() && hours > 0) {
                String.format(
                    Locale.US,
                    "%d${options.colonSeparator}%02d${options.colonSeparator}%02d",
                    hours,
                    minutes,
                    seconds,
                )
            } else {
                val totalMinutes = totalSeconds / 60
                val sec = totalSeconds % 60
                String.format(
                    Locale.US,
                    "%02d${options.colonSeparator}%02d",
                    totalMinutes,
                    sec,
                )
            }
        }

        DurationStyle.JapaneseUnit -> {
            buildString {
                if (DisplayUnit.HOUR.enabled() && hours > 0L) {
                    append(hours)
                    append(options.labels[DisplayUnit.HOUR])
                }
                if (DisplayUnit.MINUTE.enabled() && hours + minutes > 0L) {
                    append(minutes)
                    append(options.labels[DisplayUnit.MINUTE])
                }
                if (DisplayUnit.SECOND.enabled() && !(hours + minutes > 0L && seconds == 0L)) {
                    append(seconds)
                    append(options.labels[DisplayUnit.SECOND])
                }
            }
        }
    }
}

fun formatDurationMilliSecondsOrNull(
    millisInput: Long,
    style: DurationStyle = DurationStyle.JapaneseUnit,
    maxUnit: DisplayUnit = DisplayUnit.HOUR,
    minUnit: DisplayUnit = DisplayUnit.SECOND,
): String? {
    if (millisInput <= 0L) return null
    val options =
        DurationFormatOptions(
            style = style,
            maxUnit = maxUnit,
            minUnit = minUnit,
        )
    return formatDurationCore(millisInput / 1000L, options)
}

fun formatDurationMilliSeconds(
    millisInput: Long,
    style: DurationStyle = DurationStyle.JapaneseUnit,
    maxUnit: DisplayUnit = DisplayUnit.HOUR,
    minUnit: DisplayUnit = DisplayUnit.SECOND,
    zeroLabel: String = "",
): String {
    val options =
        DurationFormatOptions(
            style = style,
            maxUnit = maxUnit,
            minUnit = minUnit,
            zeroLabel = zeroLabel,
        )
    return formatDurationCore(millisInput / 1000L, options)
}

fun formatDurationSecondsOrNull(
    secondsInput: Long,
    style: DurationStyle = DurationStyle.JapaneseUnit,
    maxUnit: DisplayUnit = DisplayUnit.HOUR,
    minUnit: DisplayUnit = DisplayUnit.SECOND,
): String? {
    if (secondsInput <= 0L) return null
    val options =
        DurationFormatOptions(
            style = style,
            maxUnit = maxUnit,
            minUnit = minUnit,
        )
    return formatDurationCore(secondsInput, options)
}

fun formatDurationSeconds(
    secondsInput: Long,
    style: DurationStyle = DurationStyle.JapaneseUnit,
    maxUnit: DisplayUnit = DisplayUnit.HOUR,
    minUnit: DisplayUnit = DisplayUnit.SECOND,
    zeroLabel: String = "",
): String {
    val options =
        DurationFormatOptions(
            style = style,
            maxUnit = maxUnit,
            minUnit = minUnit,
            zeroLabel = zeroLabel,
        )
    return formatDurationCore(secondsInput, options)
}

fun formatDurationForTimerBubble(millis: Long): String {
    val options =
        DurationFormatOptions(
            style = DurationStyle.Colon,
            maxUnit = DisplayUnit.HOUR,
            minUnit = DisplayUnit.SECOND,
        )
    return formatDurationCore(millis / 1000L, options)
}

/**
 * 通知表示用に，分単位（秒を表示しない）で経過時間を整形する．
 *
 * - 60秒未満は空文字になってしまうため，明示的に「1分未満」を返す．
 * - 60秒以上は「1時間2分」「12分」などの表記にする．
 */
fun formatDurationForNotificationMinutes(millis: Long): String {
    if (millis < 60_000L) return "1分未満"
    val options =
        DurationFormatOptions(
            style = DurationStyle.JapaneseUnit,
            maxUnit = DisplayUnit.HOUR,
            minUnit = DisplayUnit.MINUTE,
        )
    return formatDurationCore(millis / 1000L, options)
}
