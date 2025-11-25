package com.example.refocus.system.monitor

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.example.refocus.core.util.SystemTimeSource
import com.example.refocus.core.util.TimeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * UsageStatsManager 経由で「前面にいるアプリ」の推定を行うクラス。
 *
 * - MOVE_TO_FOREGROUND イベントを最後に受け取ったパッケージを「前面」とみなす
 * - ホームアプリ（Launcher）や SystemUI へ遷移した場合は null を emit する
 * - イベントが何もないループでは前回の値をそのまま維持する
 */
class ForegroundAppMonitor(
    private val context: Context,
    private val timeSource: TimeSource = SystemTimeSource(),
) {

    companion object {
        private const val TAG = "ForegroundAppMonitor"
    }

    private val usageStatsManager: UsageStatsManager? =
        context.getSystemService(UsageStatsManager::class.java)

    // ホームアプリ（Launcher）のパッケージ一覧を初期化時に解決しておく
    private val homePackages: Set<String> = resolveHomePackages(context)

    private fun resolveHomePackages(context: Context): Set<String> {
        val pm: PackageManager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfos = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)

        val result = mutableSetOf<String>()
        for (info in resolveInfos) {
            val pkg = info.activityInfo?.packageName
            if (!pkg.isNullOrEmpty()) {
                result += pkg
            }
        }

        // よくある SystemUI のパッケージも念のため含めておく
        result += "com.android.systemui"

        Log.d(TAG, "homePackages = $result")
        return result
    }

    private fun isHomeLikePackage(packageName: String?): Boolean {
        if (packageName.isNullOrEmpty()) return false
        return packageName in homePackages
    }

    /**
     * 一定間隔で前面アプリの推定値を Flow<String?> として返す。
     *
     * - 前面アプリが通常のアプリ → その packageName
     * - ホームアプリ / SystemUI → null
     */
    fun foregroundAppFlow(
        pollingIntervalMs: Long = 1_000L
    ): Flow<String?> = flow {
        if (usageStatsManager == null) {
            Log.w(TAG, "UsageStatsManager is null, cannot monitor foreground app")
            // 何も分からないのでずっと null を流す
            while (true) {
                emit(null)
                delay(pollingIntervalMs)
            }
        }

        var lastEmittedPackage: String? = null
        var lastCheckedTime: Long = timeSource.nowMillis()

        val events = UsageEvents.Event()

        while (true) {
            val now = timeSource.nowMillis()
            val usm = usageStatsManager

            var rawTop: String? = null
            if (usm != null) {
                try {
                    val usageEvents: UsageEvents = usm.queryEvents(lastCheckedTime, now)
                    while (usageEvents.hasNextEvent()) {
                        usageEvents.getNextEvent(events)
                        if (events.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                            rawTop = events.packageName
                        }
                    }
                } catch (e: SecurityException) {
                    Log.e(TAG, "queryEvents failed (missing Usage Access permission?)", e)
                } catch (e: Exception) {
                    Log.e(TAG, "queryEvents failed", e)
                }
            }

            lastCheckedTime = now

            val effectiveTop: String? = when {
                rawTop == null -> {
                    // 今回新しい MOVE_TO_FOREGROUND がなければ前回値を維持
                    lastEmittedPackage
                }

                isHomeLikePackage(rawTop) -> {
                    // ホームに遷移したとみなして null を emit
                    null
                }

                else -> rawTop
            }

            // ログ用に「変化したかどうか」は見ても良い
            if (effectiveTop != lastEmittedPackage) {
                Log.d(TAG, "foreground changed: $lastEmittedPackage -> $effectiveTop")
            }

            // ★ ここで前回値を更新しつつ、毎回 emit する
            lastEmittedPackage = effectiveTop
            emit(effectiveTop)

            delay(pollingIntervalMs)
        }
    }
}
