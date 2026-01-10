package com.example.refocus.system.overlay

/**
 * Foreground service start が OS により禁止されているかどうかをざっくり判定するヘルパ．
 *
 * - Android 12+ では background start で IllegalStateException が投げられる端末がある
 * - Android 15 では ForegroundServiceStartNotAllowedException が使われるケースがある
 *
 * 厳密な判定が目的ではなく，「バックグラウンドからは自動復旧できない」ケースを
 * 取りこぼさずユーザへ再開導線を出すためのユーティリティ．
 */
internal fun isForegroundServiceStartNotAllowedError(e: Throwable): Boolean {
    val name = e::class.java.name
    if (name == "android.app.ForegroundServiceStartNotAllowedException") return true
    if (e is IllegalStateException) return true
    if (e is SecurityException) return true
    return false
}
