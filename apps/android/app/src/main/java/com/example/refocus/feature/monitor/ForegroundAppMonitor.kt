package com.example.refocus.feature.monitor

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import com.example.refocus.core.util.TimeSource
import com.example.refocus.core.util.SystemTimeSource

class ForegroundAppMonitor(
    context: Context,
    private val timeSource: TimeSource = SystemTimeSource(),
) {
    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    fun foregroundAppFlow(
        pollingIntervalMs: Long = 500L
    ): Flow<String?> = flow {
        var lastPackage: String? = null
        while (true) {
            val now = timeSource.nowMillis()
            val begin = now - 2_000

            val topApp: String? = try {
                val events = usageStatsManager.queryEvents(begin, now)
                var pkg: String? = null
                val event = UsageEvents.Event()
                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                        pkg = event.packageName
                    }
                }
                Log.d("ForegroundAppMonitor", "queryEvents topApp=$pkg")
                pkg
            } catch (e: SecurityException) {
                Log.w("ForegroundAppMonitor", "SecurityException in queryEvents", e)
                null
            }

            if (topApp != null) {
                // lastPackage はログの参考程度に残したければ更新だけする
                if (topApp != lastPackage) {
                    Log.d("ForegroundAppMonitor", "emit foreground=$topApp (changed from $lastPackage)")
                } else {
                    Log.d("ForegroundAppMonitor", "emit foreground=$topApp (same as last)")
                }
                lastPackage = topApp
                emit(topApp)
            }

            delay(pollingIntervalMs)
        }
    }
}
