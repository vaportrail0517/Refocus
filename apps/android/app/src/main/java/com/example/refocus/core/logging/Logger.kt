package com.example.refocus.core.logging

/**
 * platform 実装（android.util.Log など）から切り離すためのロガー抽象．
 *
 * 文字列生成コストを抑えるため，message は lambda を基本とする．
 */
interface Logger {
    fun d(
        subTag: String,
        message: () -> String,
    )

    fun i(
        subTag: String,
        message: () -> String,
    )

    fun w(
        subTag: String,
        throwable: Throwable?,
        message: () -> String,
    )

    fun e(
        subTag: String,
        throwable: Throwable?,
        message: () -> String,
    )

    fun wRateLimited(
        subTag: String,
        key: String,
        intervalMs: Long,
        throwable: Throwable?,
        message: () -> String,
    )
}
