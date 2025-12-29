package com.example.refocus.core.util

/**
 * 時刻取得を抽象化するインターフェース．
 * - nowMillis(): 壁時計（System.currentTimeMillis）
 * - elapsedRealtime(): 経過時間測定用（monotonic）
 */
interface TimeSource {
    fun nowMillis(): Long

    fun elapsedRealtime(): Long
}
