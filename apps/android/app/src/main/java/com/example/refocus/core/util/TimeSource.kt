package com.example.refocus.core.util

import android.os.SystemClock

/**
 * 時刻取得を抽象化するインターフェース。
 * - nowMillis(): 壁時計（System.currentTimeMillis）
 * - elapsedRealtime(): 経過時間測定用
 */
interface TimeSource {
    fun nowMillis(): Long
    fun elapsedRealtime(): Long
}

/**
 * 実機用の実装。
 */
class SystemTimeSource : TimeSource {
    override fun nowMillis(): Long = System.currentTimeMillis()
    override fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()
}
