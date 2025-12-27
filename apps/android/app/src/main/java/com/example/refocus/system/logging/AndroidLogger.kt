package com.example.refocus.system.logging

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import android.util.Log
import com.example.refocus.core.logging.Logger
import java.util.concurrent.ConcurrentHashMap

/**
 * Android 実機向け Logger 実装．
 */
class AndroidLogger(context: Context) : Logger {

    private companion object {
        private const val TAG_PREFIX = "Refocus/"
    }

    private val debuggable: Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private val lastLoggedAt = ConcurrentHashMap<String, Long>()

    private fun tag(subTag: String): String {
        return if (subTag.startsWith(TAG_PREFIX)) subTag else TAG_PREFIX + subTag
    }

    private fun elapsedRealtimeMs(): Long {
        return try {
            SystemClock.elapsedRealtime()
        } catch (e: RuntimeException) {
            // ローカル unit test などで android.os.SystemClock が "not mocked" になる場合のフォールバック
            System.currentTimeMillis()
        }
    }

    private fun safeLog(
        level: Char,
        subTag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        val t = tag(subTag)
        try {
            when (level) {
                'd' -> Log.d(t, message)
                'i' -> Log.i(t, message)
                'w' -> Log.w(t, message, throwable)
                'e' -> Log.e(t, message, throwable)
            }
        } catch (e: RuntimeException) {
            // unit test などで android.util.Log が使えない場合
            val suffix = if (throwable != null) "\n" + throwable.stackTraceToString() else ""
            val line = "$t [$level] $message$suffix"
            if (level == 'w' || level == 'e') {
                System.err.println(line)
            } else {
                println(line)
            }
        }
    }

    override fun d(subTag: String, message: () -> String) {
        if (!debuggable) return
        safeLog('d', subTag, message())
    }

    override fun i(subTag: String, message: () -> String) {
        safeLog('i', subTag, message())
    }

    override fun w(subTag: String, throwable: Throwable?, message: () -> String) {
        safeLog('w', subTag, message(), throwable)
    }

    override fun e(subTag: String, throwable: Throwable?, message: () -> String) {
        safeLog('e', subTag, message(), throwable)
    }

    override fun wRateLimited(
        subTag: String,
        key: String,
        intervalMs: Long,
        throwable: Throwable?,
        message: () -> String
    ) {
        val mapKey = tag(subTag) + "#" + key
        val now = elapsedRealtimeMs()
        val last = lastLoggedAt[mapKey]
        if (last != null && now - last < intervalMs) return
        lastLoggedAt[mapKey] = now
        w(subTag, throwable, message)
    }
}
