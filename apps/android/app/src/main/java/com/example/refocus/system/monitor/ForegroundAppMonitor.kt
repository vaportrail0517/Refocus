package com.example.refocus.system.monitor

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.example.refocus.core.util.TimeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * UsageStatsManager 経由で「前面にいるアプリ」の推定を行うクラス。
 *
 * - MOVE_TO_FOREGROUND イベントを最後に受け取ったパッケージを「前面」とみなす
 * - ホームアプリ（Launcher）や SystemUI へ遷移した場合は null を emit する
 * - イベントが何もないループでは前回の値をそのまま維持する
 */
class ForegroundAppMonitor(
    private val context: Context,
    private val timeSource: TimeSource,
) {

    companion object {
        private const val TAG = "ForegroundAppMonitor"
    }

    /**
     * packageName が同じでも「前面になった（= MOVE_TO_FOREGROUND を観測した）」を区別するためのサンプル。
     *
     * generation は「非 home-like の MOVE_TO_FOREGROUND を観測するたび」に単調増加させる。
     * これにより、OverlayCoordinator 側で「同一パッケージだが復帰した」を検知できる。
     */
    data class ForegroundSample(
        val packageName: String?,
        val generation: Long,
    )

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
        pollingIntervalMs: Long = 1_000L,
        // 起動直後、「すでに前面にいるアプリ」を拾うためのイベント巻き戻し幅
        // (lastCheckedTime を now から開始すると、直前の MOVE_TO_FOREGROUND を取り逃がしやすい)
        initialLookbackMs: Long = 10_000L
    ): Flow<String?> =
        foregroundSampleFlow(
            pollingIntervalMs = pollingIntervalMs,
            initialLookbackMs = initialLookbackMs
        )
            .map { it.packageName }
            .distinctUntilChanged()

    /**
     * packageName に加えて generation も流す版。
     * Overlay 側だけが使う想定（タイムライン記録など他用途は foregroundAppFlow を維持）。
     */
    fun foregroundSampleFlow(
        pollingIntervalMs: Long = 1_000L,
        initialLookbackMs: Long = 10_000L
    ): Flow<ForegroundSample> = flow {
        if (usageStatsManager == null) {
            Log.w(TAG, "UsageStatsManager is null, cannot monitor foreground app")
            emit(ForegroundSample(packageName = null, generation = 0L))
            while (true) delay(pollingIntervalMs)
        }

        var lastEmitted: ForegroundSample? = null
        val startNow = timeSource.nowMillis()
        var lastCheckedTime: Long = (startNow - initialLookbackMs).coerceAtLeast(0L)
        val events = UsageEvents.Event()
        var currentTop: String? = null
        var generation: Long = 0L
        while (true) {
            val now = timeSource.nowMillis()
            val usm = usageStatsManager
            if (usm != null) {
                try {
                    val usageEvents: UsageEvents = usm.queryEvents(lastCheckedTime, now)
                    while (usageEvents.hasNextEvent()) {
                        usageEvents.getNextEvent(events)
                        val pkg = events.packageName ?: continue
                        when (events.eventType) {
                            UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                                // SystemUI / Launcher が「前面」として出ることがあるが、
                                // それは通知シェードやオーバーレイ操作などで
                                // 実際のアプリが BACKGROUND に落ちていないケースを含む。
                                // ここで currentTop を null にすると誤判定でタイマーが消えるため、
                                // home-like は currentTop を上書きしない（離脱確定は BACKGROUND 側で判定）。
                                if (!isHomeLikePackage(pkg)) {
                                    currentTop = pkg
                                    generation += 1L
                                }
                            }

                            UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                                // いま前面扱いしているアプリが BACKGROUND に落ちたら前面なし
                                if (pkg == currentTop) {
                                    currentTop = null
                                }
                            }

                            else -> {
                                // それ以外のイベントは無視
                            }
                        }
                    }
                } catch (e: SecurityException) {
                    Log.e(TAG, "queryEvents failed (missing Usage Access permission?)", e)
                } catch (e: Exception) {
                    Log.e(TAG, "queryEvents failed", e)
                }
            }
            lastCheckedTime = now
            val sample = ForegroundSample(packageName = currentTop, generation = generation)
            if (lastEmitted == null || sample != lastEmitted) {
                lastEmitted = sample
                emit(sample)
            }

            delay(pollingIntervalMs)
        }
    }
}
