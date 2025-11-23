package com.example.refocus.core.util

fun formatDurationMillis(millis: Long): String {
    if (millis <= 0L) return "なし"
    val totalSeconds = millis / 1000L
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

fun formatDurationSeconds(secondsInput: Int): String {
    if (secondsInput <= 0) return "なし"
    val minutes = secondsInput / 60
    val seconds = secondsInput % 60
    return when {
        minutes > 0 && seconds > 0 ->
            "${minutes}分${seconds}秒"

        minutes > 0 ->
            "${minutes}分"

        else ->
            "${seconds}秒"
    }
}

// 00:00 / 12:34 / 1:23:45 みたいな表記
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