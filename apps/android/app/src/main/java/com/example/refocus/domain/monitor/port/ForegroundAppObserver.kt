package com.example.refocus.domain.monitor.port

import kotlinx.coroutines.flow.Flow

/**
 * 端末上で「推定される前面アプリ」を購読する抽象。
 *
 * domain 層は UsageStatsManager 等の Android API を直接参照しないため，
 * system 層に実装を置く。
 */
interface ForegroundAppObserver {
    data class ForegroundSample(
        val packageName: String?,
        val generation: Long,
    )

    fun foregroundSampleFlow(
        pollingIntervalMs: Long = 1_000L,
        // 監視開始直後，「すでに前面にいるアプリ」を拾うためのイベント巻き戻し幅．
        // 監視開始時の lastCheckedTime を now から開始すると，直前の MOVE_TO_FOREGROUND を取り逃がしやすい．
        initialLookbackMs: Long = 10_000L,
    ): Flow<ForegroundSample>
}
