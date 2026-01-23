package com.example.refocus.system.monitor

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.example.refocus.core.logging.RefocusLog
import com.example.refocus.core.util.TimeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * UsageStatsManager 経由で「前面にいるアプリ」の推定を行うクラス．
 *
 * - MOVE_TO_FOREGROUND / ACTIVITY_RESUMED を最後に受け取ったパッケージを「前面」とみなす
 * - ホームアプリ（Launcher）や SystemUI を一時的に観測しても，直ちに null へ落とさず，
 *   直前の通常アプリを保持する（通知シェードやナビゲーション操作での誤 null 化を避ける）
 * - ただし home-like 状態が長時間継続し，かつ復帰イベントが観測できない場合は，
 *   誤って前面が残り続けるのを防ぐために null へフォールバックする
 * - イベントが何もないループでは前回の値をそのまま維持する
 */
class ForegroundAppMonitor(
    private val context: Context,
    private val timeSource: TimeSource,
) {
    companion object {
        private const val TAG = "Foreground"

        // home-like（Launcher / SystemUI）への遷移を観測した後に，
        // MOVE_TO_BACKGROUND を取り逃がしても前面判定が残り続けないようにするためのハードタイムアウト．
        // 短すぎると通知シェード操作などで不要に null になりやすい．
        private const val HOME_LIKE_HARD_CLEAR_DELAY_MS: Long = 8_000L

        // ハードタイムアウト到達時に，「直近の非 home-like の前面イベント」を再確認するためのウィンドウ．
        // OEM によっては復帰側イベントが欠落することがあるため，短い巻き戻しで拾える場合は拾う．
        private const val HOME_LIKE_RECHECK_WINDOW_MS: Long = 2_500L
    }

    /**
     * packageName が同じでも「前面になった（= MOVE_TO_FOREGROUND を観測した）」を区別するためのサンプル．
     *
     * generation は「非 home-like の MOVE_TO_FOREGROUND / ACTIVITY_RESUMED を観測するたび」に単調増加させる．
     * これにより，OverlayCoordinator 側で「同一パッケージだが復帰した」を検知できる．
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

        RefocusLog.d(TAG) { "homePackages = $result" }
        return result
    }

    private fun isHomeLikePackage(packageName: String?): Boolean {
        if (packageName.isNullOrEmpty()) return false
        return packageName in homePackages
    }

    /**
     * 一定間隔で前面アプリの推定値を Flow<String?> として返す．
     *
     * - 前面アプリが通常のアプリ → その packageName
     * - home-like を観測しても直ちに null へ落とさず，直前の通常アプリを維持する
     * - home-like が長く続き，復帰イベントも見えない場合のみ null へフォールバックする
     */
    fun foregroundAppFlow(
        pollingIntervalMs: Long = 1_000L,
        // 起動直後，「すでに前面にいるアプリ」を拾うためのイベント巻き戻し幅
        // (lastCheckedTime を now から開始すると，直前の MOVE_TO_FOREGROUND を取り逃がしやすい)
        initialLookbackMs: Long = 10_000L,
    ): Flow<String?> =
        foregroundSampleFlow(
            pollingIntervalMs = pollingIntervalMs,
            initialLookbackMs = initialLookbackMs,
        ).map { it.packageName }
            .distinctUntilChanged()

    /**
     * packageName に加えて generation も流す版．
     * Overlay 側だけが使う想定（タイムライン記録など他用途は foregroundAppFlow を維持）．
     */
    fun foregroundSampleFlow(
        pollingIntervalMs: Long = 1_000L,
        initialLookbackMs: Long = 10_000L,
    ): Flow<ForegroundSample> =
        flow {
            val usm = usageStatsManager
            if (usm == null) {
                RefocusLog.w(TAG) { "UsageStatsManager is null, cannot monitor foreground app" }
                emit(ForegroundSample(packageName = null, generation = 0L))
                while (true) delay(pollingIntervalMs)
            }

            var lastEmitted: ForegroundSample? = null

            val startNow = timeSource.nowMillis()
            var lastCheckedTime: Long = (startNow - initialLookbackMs).coerceAtLeast(0L)
            val event = UsageEvents.Event()

            var currentTop: String? = null
            var generation: Long = 0L

            // home-like（Launcher / SystemUI）を観測した期間．
            // この間に復帰イベントが欠落すると，従来は currentTop が null へ落ちたままになることがあったため，
            // 直前の通常アプリ（currentTop）を維持しつつ，長時間継続した場合のみ null へフォールバックする．
            var homeLikeSinceMillis: Long? = null
            var homeLikePackage: String? = null

            fun clearHomeLike(reason: String) {
                if (homeLikeSinceMillis != null || homeLikePackage != null) {
                    RefocusLog.d(TAG) {
                        "homeLike cleared: reason=$reason " +
                            "since=$homeLikeSinceMillis pkg=$homeLikePackage " +
                            "currentTop=$currentTop"
                    }
                }
                homeLikeSinceMillis = null
                homeLikePackage = null
            }

            fun markHomeLike(
                packageName: String,
                nowMillis: Long,
            ) {
                val since = homeLikeSinceMillis
                val prevPkg = homeLikePackage

                if (since == null) {
                    homeLikeSinceMillis = nowMillis
                    homeLikePackage = packageName
                    RefocusLog.d(TAG) {
                        "homeLike started: pkg=$packageName now=$nowMillis " +
                            "currentTop=$currentTop generation=$generation"
                    }
                } else {
                    homeLikePackage = packageName
                    if (prevPkg != packageName) {
                        RefocusLog.d(TAG) {
                            "homeLike updated: pkg=$packageName now=$nowMillis since=$since currentTop=$currentTop"
                        }
                    }
                }
            }

            fun queryLastNonHomeForeground(nowMillis: Long): String? {
                val start = (nowMillis - HOME_LIKE_RECHECK_WINDOW_MS).coerceAtLeast(0L)
                val usageEvents: UsageEvents = usm.queryEvents(start, nowMillis)

                val recheckEvent = UsageEvents.Event()
                var lastNonHome: String? = null
                while (usageEvents.hasNextEvent()) {
                    usageEvents.getNextEvent(recheckEvent)
                    val pkg = recheckEvent.packageName ?: continue

                    when (recheckEvent.eventType) {
                        UsageEvents.Event.MOVE_TO_FOREGROUND,
                        UsageEvents.Event.ACTIVITY_RESUMED,
                        -> {
                            if (!isHomeLikePackage(pkg)) {
                                lastNonHome = pkg
                            }
                        }

                        else -> {
                            // ignore
                        }
                    }
                }

                return lastNonHome
            }

            fun applyHomeLikeHardClearIfNeeded(nowMillis: Long) {
                val since = homeLikeSinceMillis ?: return
                val elapsed = nowMillis - since
                if (elapsed < HOME_LIKE_HARD_CLEAR_DELAY_MS) return

                RefocusLog.d(TAG) {
                    "homeLike hard-clear due: elapsedMs=$elapsed homePkg=$homeLikePackage currentTop=$currentTop"
                }

                val recheckedTop: String? =
                    try {
                        queryLastNonHomeForeground(nowMillis)
                    } catch (e: SecurityException) {
                        RefocusLog.wRateLimited(
                            TAG,
                            "homeLike_recheck_security",
                            60_000L,
                            e,
                        ) { "homeLike recheck queryEvents failed (missing Usage Access permission?)" }
                        null
                    } catch (e: Exception) {
                        RefocusLog.wRateLimited(
                            TAG,
                            "homeLike_recheck_generic",
                            60_000L,
                            e,
                        ) { "homeLike recheck queryEvents failed" }
                        null
                    }

                if (recheckedTop != null) {
                    if (recheckedTop != currentTop) {
                        currentTop = recheckedTop
                        generation += 1L
                        RefocusLog.d(TAG) {
                            "homeLike recheck found top: pkg=$recheckedTop -> " +
                                "currentTop=$currentTop generation=$generation"
                        }
                    } else {
                        generation += 1L
                        RefocusLog.d(TAG) {
                            "homeLike recheck reaffirmed top: pkg=$recheckedTop generation=$generation"
                        }
                    }
                } else {
                    RefocusLog.d(TAG) {
                        "homeLike hard-clear: no non-home foreground found -> currentTop=null"
                    }
                    currentTop = null
                }

                clearHomeLike(reason = "hard_clear")
            }

            fun onMovedToForeground(
                packageName: String,
                nowMillis: Long,
            ) {
                if (isHomeLikePackage(packageName)) {
                    markHomeLike(packageName, nowMillis)
                    return
                }

                clearHomeLike(reason = "non_home_foreground")
                currentTop = packageName
                generation += 1L

                RefocusLog.d(TAG) {
                    "onMovedToForeground: pkg=$packageName now=$nowMillis -> " +
                        "currentTop=$currentTop generation=$generation"
                }
            }

            fun onMovedToBackground(packageName: String) {
                if (isHomeLikePackage(packageName)) {
                    clearHomeLike(reason = "home_like_background:$packageName")
                    return
                }

                // いま前面扱いしているアプリが BACKGROUND に落ちたら前面なし
                if (packageName == currentTop) {
                    RefocusLog.d(TAG) {
                        "onMovedToBackground: pkg=$packageName matched currentTop -> null (generation=$generation)"
                    }
                    clearHomeLike(reason = "current_top_background")
                    currentTop = null
                }
            }

            while (true) {
                val now = timeSource.nowMillis()

                try {
                    val usageEvents: UsageEvents = usm.queryEvents(lastCheckedTime, now)
                    while (usageEvents.hasNextEvent()) {
                        usageEvents.getNextEvent(event)
                        val pkg = event.packageName ?: continue

                        when (event.eventType) {
                            UsageEvents.Event.MOVE_TO_FOREGROUND,
                            UsageEvents.Event.ACTIVITY_RESUMED,
                            -> {
                                onMovedToForeground(pkg, now)
                            }

                            UsageEvents.Event.MOVE_TO_BACKGROUND,
                            UsageEvents.Event.ACTIVITY_PAUSED,
                            -> {
                                onMovedToBackground(pkg)
                            }

                            else -> {
                                // ignore
                            }
                        }
                    }
                } catch (e: SecurityException) {
                    RefocusLog.wRateLimited(
                        TAG,
                        "queryEvents_security",
                        60_000L,
                        e,
                    ) { "queryEvents failed (missing Usage Access permission?)" }
                } catch (e: Exception) {
                    RefocusLog.wRateLimited(
                        TAG,
                        "queryEvents_generic",
                        60_000L,
                        e,
                    ) { "queryEvents failed" }
                }

                applyHomeLikeHardClearIfNeeded(now)

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
