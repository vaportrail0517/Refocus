package com.example.refocus.system.time

import android.os.SystemClock
import com.example.refocus.core.util.TimeSource

/**
 * 実機用の TimeSource 実装．
 */
class SystemTimeSource : TimeSource {
    override fun nowMillis(): Long = System.currentTimeMillis()
    override fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()
}
