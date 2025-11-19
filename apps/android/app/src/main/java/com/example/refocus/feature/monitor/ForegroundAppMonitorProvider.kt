package com.example.refocus.feature.monitor

import android.content.Context

/**
 * ForegroundAppMonitor をアプリ全体で共有するための簡易 Provider。
 *
 * 実装としては applicationContext を使ったシングルトン。
 * 将来、監視フローを共有 StateFlow にしたくなった場合もここを差し替えればよい。
 */
object ForegroundAppMonitorProvider {

    @Volatile
    private var instance: ForegroundAppMonitor? = null

    fun get(context: Context): ForegroundAppMonitor {
        val appContext = context.applicationContext
        val current = instance
        if (current != null) return current
        return synchronized(this) {
            instance ?: ForegroundAppMonitor(appContext).also { instance = it }
        }
    }
}
