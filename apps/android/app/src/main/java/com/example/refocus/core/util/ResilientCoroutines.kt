package com.example.refocus.core.util

import com.example.refocus.core.logging.RefocusLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 長寿命の監視・購読ループを「キャンセル以外は自己復帰」させるためのユーティリティ．
 *
 * - collect が例外で落ちても復活する
 * - 予期せず完了しても再起動する
 * - CancellationException は正しく伝播して停止する
 */
object ResilientCoroutines {
    fun launchResilient(
        scope: CoroutineScope,
        tag: String,
        initialBackoffMs: Long = 500L,
        maxBackoffMs: Long = 10_000L,
        block: suspend CoroutineScope.() -> Unit,
    ): Job =
        scope.launch {
            var backoffMs = initialBackoffMs
            while (isActive) {
                try {
                    block()
                    // 長寿命ブロックが通常完了するのは想定外．再起動しておく
                    RefocusLog.w(tag) { "Resilient block completed unexpectedly. restart." }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    RefocusLog.e(tag, e) { "Resilient block crashed. restart." }
                }

                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(maxBackoffMs)
            }
        }
}
