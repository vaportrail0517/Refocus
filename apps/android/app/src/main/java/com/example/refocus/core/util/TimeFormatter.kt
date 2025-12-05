package com.example.refocus.core.util

import android.annotation.SuppressLint

private fun formatMinutesAndSecondsOrNull(totalSeconds: Long): String? {
    if (totalSeconds <= 0L) return null

    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60

    return when {
        minutes > 0 && seconds > 0 ->
            "${minutes}分${seconds}秒"

        minutes > 0 ->
            "${minutes}分"

        else ->
            "${seconds}秒"
    }
}

fun formatDurationMillis(millis: Long, zeroMessage: String = "なし"): String {
    return formatMinutesAndSecondsOrNull(millis / 1000L) ?: zeroMessage
}

fun formatDurationMillisOrNull(millis: Long): String? {
    return formatMinutesAndSecondsOrNull(millis / 1000L)
}

fun formatDurationSeconds(secondsInput: Int, zeroMessage: String = "なし"): String {
    return formatMinutesAndSecondsOrNull(secondsInput.toLong()) ?: zeroMessage
}

@SuppressLint("DefaultLocale")
fun formatDurationForTimerBubble(millis: Long): String {
    val totalSeconds = millis / 1000
    val seconds = (totalSeconds % 60).toInt()
    val minutes = ((totalSeconds / 60) % 60).toInt()
    val hours = (totalSeconds / 3600).toInt()

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
