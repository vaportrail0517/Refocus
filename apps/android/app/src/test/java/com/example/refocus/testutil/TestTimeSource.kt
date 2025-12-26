package com.example.refocus.testutil

import com.example.refocus.core.util.TimeSource

/**
 * JVM 単体テスト用の TimeSource．
 * nowMillis / elapsedRealtime を任意に進められる．
 */
class TestTimeSource(
    initialNowMillis: Long,
    initialElapsedRealtime: Long,
) : TimeSource {

    private var nowMillisValue: Long = initialNowMillis
    private var elapsedRealtimeValue: Long = initialElapsedRealtime

    override fun nowMillis(): Long = nowMillisValue

    override fun elapsedRealtime(): Long = elapsedRealtimeValue

    fun advanceMillis(deltaMillis: Long) {
        nowMillisValue += deltaMillis
        elapsedRealtimeValue += deltaMillis
    }

    fun setNowMillis(nowMillis: Long) {
        nowMillisValue = nowMillis
    }

    fun setElapsedRealtime(elapsedRealtime: Long) {
        elapsedRealtimeValue = elapsedRealtime
    }
}
