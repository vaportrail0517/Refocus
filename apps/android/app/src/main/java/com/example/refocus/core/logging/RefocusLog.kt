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

    fun d(subTag: String, message: () -> String) {
        if (!debuggable) return
        Log.d(tag(subTag), message())
    }

    fun d(subTag: String, message: String) = d(subTag) { message }

    fun i(subTag: String, message: () -> String) {
        Log.i(tag(subTag), message())
    }

    fun i(subTag: String, message: String) = i(subTag) { message }

    fun w(subTag: String, throwable: Throwable? = null, message: () -> String) {
        Log.w(tag(subTag), message(), throwable)
    }

    fun w(subTag: String, message: String, throwable: Throwable? = null) = w(subTag, throwable) { message }

    fun e(subTag: String, throwable: Throwable? = null, message: () -> String) {
        Log.e(tag(subTag), message(), throwable)
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
        val now = SystemClock.elapsedRealtime()
        val last = lastLoggedAt[mapKey]
        if (last != null && now - last < intervalMs) return
        lastLoggedAt[mapKey] = now
        w(subTag, throwable, message)
    }
}
