package com.example.refocus.system.monitor

import com.example.refocus.domain.gateway.ForegroundAppObserver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * ForegroundAppMonitor（Android 実装）を domain.gateway.ForegroundAppObserver に適合させるアダプタ。
 */
class ForegroundAppObserverImpl(
    private val monitor: ForegroundAppMonitor,
) : ForegroundAppObserver {

    override fun foregroundSampleFlow(
        pollingIntervalMs: Long,
    ): Flow<ForegroundAppObserver.ForegroundSample> {
        return monitor.foregroundSampleFlow(
            pollingIntervalMs = pollingIntervalMs,
        ).map { sample ->
            ForegroundAppObserver.ForegroundSample(
                packageName = sample.packageName,
                generation = sample.generation,
            )
        }
    }
}
