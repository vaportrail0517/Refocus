package com.example.refocus.core.util

import android.annotation.SuppressLint

enum class DisplayUnit {
    SECOND,
    MINUTE,
    HOUR,
}

enum class DurationStyle {
    JapaneseUnit, // 例: "1時間2分3秒"
    Colon,        // 例: "1:02:03" or "02:03"
}

data class DurationFormatOptions(
    val style: DurationStyle = DurationStyle.JapaneseUnit,
    val maxUnit: DisplayUnit = DisplayUnit.HOUR,
    val minUnit: DisplayUnit = DisplayUnit.SECOND,
    val zeroLabel: String = "",
    val colonSeparator: String = ":",
    val labels: Map<DisplayUnit, String> = mapOf(
        DisplayUnit.HOUR to "時間",
        DisplayUnit.MINUTE to "分",
        DisplayUnit.SECOND to "秒",
    )
)

private fun formatDurationCore(
    totalSeconds: Long,
    options: DurationFormatOptions
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
            return if (DisplayUnit.HOUR.enabled() && hours > 0) {
                String.format(
                    "%d${options.colonSeparator}%02d${options.colonSeparator}%02d",
                    hours,
                    minutes,
                    seconds
                )
            } else {
                val totalMinutes = totalSeconds / 60
                val sec = totalSeconds % 60
                String.format(
                    "%02d${options.colonSeparator}%02d",
                    totalMinutes,
                    sec
                )
            }
        }

        DurationStyle.JapaneseUnit -> {
            buildString {
                if (DisplayUnit.HOUR.enabled() && hours > 0) {
                    append(hours)
                    append(options.labels[DisplayUnit.HOUR])
                }
                if (DisplayUnit.MINUTE.enabled() && hours + minutes > 0) {
                    append(minutes)
                    append(options.labels[DisplayUnit.MINUTE])
                }
                if (DisplayUnit.SECOND.enabled()) {
                    append(seconds)
                    append(options.labels[DisplayUnit.SECOND])
                }
            }
        }
    }
}

fun formatDurationMilliSecondsOrNull(
    secondsInput: Long,
    style: DurationStyle = DurationStyle.JapaneseUnit,
    maxUnit: DisplayUnit = DisplayUnit.MINUTE,
    minUnit: DisplayUnit = DisplayUnit.SECOND,
): String? {
    if (secondsInput <= 0L) return null
    val options = DurationFormatOptions(
        style = style,
        maxUnit = maxUnit,
        minUnit = minUnit,
    )
    return formatDurationCore(secondsInput / 1000L, options)
}

fun formatDurationMilliSeconds(
    secondsInput: Long,
    style: DurationStyle = DurationStyle.JapaneseUnit,
    maxUnit: DisplayUnit = DisplayUnit.MINUTE,
    minUnit: DisplayUnit = DisplayUnit.SECOND,
    zeroLabel: String = "",
): String {
    val options = DurationFormatOptions(
        style = style,
        maxUnit = maxUnit,
        minUnit = minUnit,
        zeroLabel = zeroLabel,
    )
    return formatDurationCore(secondsInput / 1000L, options)
}

fun formatDurationSecondsOrNull(
    secondsInput: Long,
    style: DurationStyle = DurationStyle.JapaneseUnit,
    maxUnit: DisplayUnit = DisplayUnit.MINUTE,
    minUnit: DisplayUnit = DisplayUnit.SECOND,
): String? {
    if (secondsInput <= 0L) return null
    val options = DurationFormatOptions(
        style = style,
        maxUnit = maxUnit,
        minUnit = minUnit,
    )
    return formatDurationCore(secondsInput, options)
}

fun formatDurationSeconds(
    secondsInput: Long,
    style: DurationStyle = DurationStyle.JapaneseUnit,
    maxUnit: DisplayUnit = DisplayUnit.MINUTE,
    minUnit: DisplayUnit = DisplayUnit.SECOND,
    zeroLabel: String = "",
): String {
    val options = DurationFormatOptions(
        style = style,
        maxUnit = maxUnit,
        minUnit = minUnit,
        zeroLabel = zeroLabel,
    )
    return formatDurationCore(secondsInput, options)
}

@SuppressLint("DefaultLocale")
fun formatDurationForTimerBubble(millis: Long): String {
    val options = DurationFormatOptions(
        style = DurationStyle.Colon,
        maxUnit = DisplayUnit.HOUR,
        minUnit = DisplayUnit.SECOND,
    )
    return formatDurationCore(millis / 1000L, options)
}

