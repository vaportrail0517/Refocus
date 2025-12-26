package com.example.refocus.core.logging

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Logging utility for Refocus.
 *
 * - Tag format is always "Refocus/<SubTag>" for easy filtering.
 * - Debug logs are emitted only for debuggable builds (no BuildConfig dependency).
 * - Rate-limited warnings prevent log spam on frequently failing paths.
 */
object RefocusLog {

    private const val TAG_PREFIX = "Refocus/"
    private const val DEFAULT_RATE_LIMIT_MS = 30_000L

    @Volatile
    private var debuggable: Boolean = false

    private val lastLoggedAt = ConcurrentHashMap<String, Long>()

    fun init(context: Context) {
        debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    private fun tag(subTag: String): String {
        return if (subTag.startsWith(TAG_PREFIX)) subTag else TAG_PREFIX + subTag
    }

    /**
     * android.util.Log / android.os.SystemClock はローカル JVM の unit test では "not mocked" 例外になる．
     * その場合でもテストが落ちないように，安全なフォールバックを用意する．
     */
    private fun elapsedRealtimeMs(): Long {
        return try {
            SystemClock.elapsedRealtime()
        } catch (e: RuntimeException) {
            // unit test 実行時のフォールバック（精度より安全性を優先）
            System.currentTimeMillis()
        }
    }

    private fun safeLog(
        level: Char,
        subTag: String,
        message: String,
        throwable: Throwable? = null
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

    fun d(subTag: String, message: () -> String) {
        if (!debuggable) return
        safeLog('d', subTag, message())
    }

    fun d(subTag: String, message: String) = d(subTag) { message }

    fun i(subTag: String, message: () -> String) {
        safeLog('i', subTag, message())
    }

    fun i(subTag: String, message: String) = i(subTag) { message }

    fun w(subTag: String, throwable: Throwable? = null, message: () -> String) {
        safeLog('w', subTag, message(), throwable)
    }

    fun w(subTag: String, message: String, throwable: Throwable? = null) = w(subTag, throwable) { message }

    fun e(subTag: String, throwable: Throwable? = null, message: () -> String) {
        safeLog('e', subTag, message(), throwable)
    }

    fun e(subTag: String, message: String, throwable: Throwable? = null) = e(subTag, throwable) { message }

    fun wRateLimited(
        subTag: String,
        key: String,
        intervalMs: Long = DEFAULT_RATE_LIMIT_MS,
        throwable: Throwable? = null,
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
