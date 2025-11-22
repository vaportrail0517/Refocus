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
    // 提案機能が有効かどうか
    val suggestionEnabled: Boolean = true,
    // 何秒連続利用したら提案を出すか（0以下なら「提案オフ」扱い）
    val suggestionTriggerSeconds: Int = 30,
    // 提案カードの自動クローズまでの秒数
    val suggestionTimeoutSeconds: Int = 8,
    // 提案を再度出すまでのクールダウン秒数（全アプリ共通）
    // 「あとで」「スワイプで消す」「タイムアウト」後に適用される
    val suggestionCooldownSeconds: Int = 30,
    // 対象アプリが前面に来てから何秒以上経過していれば「提案を出してよい」とみなすか
    val suggestionForegroundStableSeconds: Int = 20,
    // やりたいことが登録されていない場合に「休憩」をフォールバックとして提案するか
    val restSuggestionEnabled: Boolean = true,
    // 提案表示直後の誤操作を防ぐロックアウト時間（ミリ秒）
    val suggestionInteractionLockoutMillis: Long = 400L,
    // val colorMode: OverlayColorMode = OverlayColorMode.SingleColor,
)
