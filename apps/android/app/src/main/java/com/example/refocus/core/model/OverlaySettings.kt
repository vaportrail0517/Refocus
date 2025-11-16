package com.example.refocus.core.model

data class OverlaySettings(
    val gracePeriodMillis: Long = 30_000L,
    val pollingIntervalMillis: Long = 500L,
    val minFontSizeSp: Float = 14f,
    val maxFontSizeSp: Float = 20f,
    val timeToMaxMinutes: Int = 30,
    // 将来的に:
    // val colorMode: OverlayColorMode = OverlayColorMode.SingleColor,
)
