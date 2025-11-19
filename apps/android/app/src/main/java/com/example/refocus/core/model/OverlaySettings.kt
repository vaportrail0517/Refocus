package com.example.refocus.core.model

enum class OverlayTouchMode {
    Drag,        // タイマーをドラッグして動かす
    PassThrough, // タップは背面の UI にそのまま渡す
}

data class OverlaySettings(
    val gracePeriodMillis: Long = 300_000L,
    val pollingIntervalMillis: Long = 500L,
    val minFontSizeSp: Float = 12f,
    val maxFontSizeSp: Float = 28f,
    val timeToMaxMinutes: Int = 30,
    val positionX: Int = 24,
    val positionY: Int = 120,
    val touchMode: OverlayTouchMode = OverlayTouchMode.Drag,
    // val colorMode: OverlayColorMode = OverlayColorMode.SingleColor,
)
