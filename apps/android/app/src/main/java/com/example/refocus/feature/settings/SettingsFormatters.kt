package com.example.refocus.feature.settings

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
