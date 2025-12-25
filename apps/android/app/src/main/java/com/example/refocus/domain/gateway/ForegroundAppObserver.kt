package com.example.refocus.domain.gateway

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
    ): Flow<ForegroundSample>
}
