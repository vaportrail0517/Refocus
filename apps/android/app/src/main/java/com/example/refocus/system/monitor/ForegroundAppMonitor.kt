package com.example.refocus.system.monitor

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import com.example.refocus.core.util.SystemTimeSource
import com.example.refocus.core.util.TimeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class ForegroundAppMonitor(
    context: Context,
    private val timeSource: TimeSource = SystemTimeSource(),
) {
    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    // 初回だけ少し広めに見る。30秒〜60秒くらいなら十分軽いはず。
    private val initialLookbackMs: Long = 30_000L
    fun foregroundAppFlow(
        pollingIntervalMs: Long = 500L
    ): Flow<String?> = flow {
        var lastPackage: String? = null
        var lastQueryEnd: Long = timeSource.nowMillis() - initialLookbackMs
        while (true) {
            val now = timeSource.nowMillis()
            val begin = lastQueryEnd.coerceAtLeast(0L)

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

            lastQueryEnd = now

            // 「新しい topApp が取れればそれを使い、そうでなければ前回の値を使う」
            val effectiveTop = topApp ?: lastPackage
            if (effectiveTop != null) {
                if (effectiveTop != lastPackage) {
                    Log.d(
                        "ForegroundAppMonitor",
                        "emit foreground=$effectiveTop (changed from $lastPackage)"
                    )
                } else {
                    Log.d(
                        "ForegroundAppMonitor",
                        "emit foreground=$effectiveTop (same as last)"
                    )
                }
                lastPackage = effectiveTop
                emit(effectiveTop)
            }

            delay(pollingIntervalMs)
        }
    }
}
