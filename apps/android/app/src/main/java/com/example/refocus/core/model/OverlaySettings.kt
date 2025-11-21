package com.example.refocus.core.model

enum class OverlayTouchMode {
    Drag,        // タイマーをドラッグして動かす
    PassThrough, // タップは背面の UI にそのまま渡す
}

data class OverlaySettings(
    val gracePeriodMillis: Long = 300_000L,
    val pollingIntervalMillis: Long = 500L,
    val minFontSizeSp: Float = 12f,
    val maxFontSizeSp: Float = 40f,
    val timeToMaxMinutes: Int = 30,
    val overlayEnabled: Boolean = true,
    val autoStartOnBoot: Boolean = true,
    val positionX: Int = 24,
    val positionY: Int = 120,
    val touchMode: OverlayTouchMode = OverlayTouchMode.Drag,
    // 提案関連の時間設定
    // 何分連続利用したら提案を出すか（0以下なら「提案オフ」扱い）
    val suggestionTriggerMinutes: Int = 1,
    // 「またあとで」を押したときのスヌーズ時間（分）
    val suggestionSnoozeLaterMinutes: Int = 1,
    // タイムアウト／フリック等で閉じたときのスヌーズ時間（分）
    val suggestionDismissSnoozeMinutes: Int = 1,
    // val colorMode: OverlayColorMode = OverlayColorMode.SingleColor,
    // val colorMode: OverlayColorMode = OverlayColorMode.SingleColor,
)
