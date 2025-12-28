package com.example.refocus.core.logging

/**
 * Refocus 共通ログ API．
 *
 * core は Android API に依存しない．実機向け実装は system 側で差し替える．
 */
object RefocusLog {
    private const val DEFAULT_RATE_LIMIT_MS = 30_000L

    @Volatile
    private var logger: Logger = StdoutLogger(debuggable = false)

    /**
     * platform 実装（例: AndroidLogger）をインストールする．
     */
    fun install(logger: Logger) {
        this.logger = logger
    }

    /**
     * unit test での後片付け用．
     */
    fun resetForTest() {
        logger = StdoutLogger(debuggable = false)
    }

    fun d(
        subTag: String,
        message: () -> String,
    ) {
        logger.d(subTag, message)
    }

    fun d(
        subTag: String,
        message: String,
    ) = d(subTag) { message }

    fun i(
        subTag: String,
        message: () -> String,
    ) {
        logger.i(subTag, message)
    }

    fun i(
        subTag: String,
        message: String,
    ) = i(subTag) { message }

    fun w(
        subTag: String,
        throwable: Throwable? = null,
        message: () -> String,
    ) {
        logger.w(subTag, throwable, message)
    }

    fun w(
        subTag: String,
        message: String,
        throwable: Throwable? = null,
    ) = w(subTag, throwable) { message }

    fun e(
        subTag: String,
        throwable: Throwable? = null,
        message: () -> String,
    ) {
        logger.e(subTag, throwable, message)
    }

    fun e(
        subTag: String,
        message: String,
        throwable: Throwable? = null,
    ) = e(subTag, throwable) { message }

    fun wRateLimited(
        subTag: String,
        key: String,
        intervalMs: Long = DEFAULT_RATE_LIMIT_MS,
        throwable: Throwable? = null,
        message: () -> String,
    ) {
        logger.wRateLimited(subTag, key, intervalMs, throwable, message)
    }
}
