package com.example.refocus.core.logging

import java.util.concurrent.ConcurrentHashMap

/**
 * Android に依存しないデフォルト logger．
 *
 * - JVM unit test や Application 起動初期でも安全に使える．
 */
class StdoutLogger(
    private val debuggable: Boolean = true,
) : Logger {

    private val lastLoggedAt = ConcurrentHashMap<String, Long>()

    private fun tag(subTag: String): String {
        return if (subTag.startsWith("Refocus/")) subTag else "Refocus/" + subTag
    }

    private fun logLine(level: Char, subTag: String, message: String, throwable: Throwable? = null) {
        val t = tag(subTag)
        val suffix = if (throwable != null) "\n" + throwable.stackTraceToString() else ""
        val line = "$t [$level] $message$suffix"
        if (level == 'w' || level == 'e') {
            System.err.println(line)
        } else {
            println(line)
        }
    }

    override fun d(subTag: String, message: () -> String) {
        if (!debuggable) return
        logLine('d', subTag, message())
    }

    override fun i(subTag: String, message: () -> String) {
        logLine('i', subTag, message())
    }

    override fun w(subTag: String, throwable: Throwable?, message: () -> String) {
        logLine('w', subTag, message(), throwable)
    }

    override fun e(subTag: String, throwable: Throwable?, message: () -> String) {
        logLine('e', subTag, message(), throwable)
    }

    override fun wRateLimited(
        subTag: String,
        key: String,
        intervalMs: Long,
        throwable: Throwable?,
        message: () -> String
    ) {
        val mapKey = tag(subTag) + "#" + key
        val now = System.currentTimeMillis()
        val last = lastLoggedAt[mapKey]
        if (last != null && now - last < intervalMs) return
        lastLoggedAt[mapKey] = now
        w(subTag, throwable, message)
    }
}
