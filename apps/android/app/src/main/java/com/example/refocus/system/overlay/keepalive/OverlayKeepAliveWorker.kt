package com.example.refocus.system.overlay.keepalive

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.refocus.core.logging.RefocusLog
import com.example.refocus.core.model.OverlayHealthSnapshot
import com.example.refocus.core.util.TimeSource
import com.example.refocus.domain.overlay.port.OverlayHealthStore
import com.example.refocus.domain.repository.SettingsRepository
import com.example.refocus.system.overlay.requestOverlaySelfHeal
import com.example.refocus.system.overlay.startOverlayService
import com.example.refocus.system.permissions.PermissionHelper
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

/**
 * Overlay 監視サービスの keep-alive（保険）
 *
 * - overlayEnabled=true かつ必須権限が揃っているのに heartbeat が更新されていない場合，サービス再起動を試みる
 * - アプリを開かなくても復旧できる経路として使う
 */
class OverlayKeepAliveWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    companion object {
        private const val TAG = "OverlayKeepAliveWorker"

        // OverlayService 側の heartbeat 更新（10 秒）を前提に，「30 秒以上更新が無い」なら停止扱いにする
        private const val HEARTBEAT_FRESH_THRESHOLD_MS: Long = 30_000L

        // 前面アプリ監視は 10 秒ごとに liveness を更新する想定．
        // そのため 60 秒以上更新が無い場合は監視の不調を疑う．
        private const val MONITOR_FRESH_THRESHOLD_MS: Long = 60_000L

        // 端末時計のズレ・手動変更をある程度許容する
        private const val CLOCK_SKEW_ALLOWANCE_MS: Long = 5 * 60_000L

        private const val ERROR_SUMMARY_MAX = 160
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun settingsRepository(): SettingsRepository

        fun overlayHealthStore(): OverlayHealthStore

        fun timeSource(): TimeSource
    }

    override suspend fun doWork(): Result {
        val appContext = applicationContext

        val entryPoint =
            EntryPointAccessors.fromApplication(
                appContext,
                WorkerEntryPoint::class.java,
            )

        val store = entryPoint.overlayHealthStore()
        val timeSource = entryPoint.timeSource()
        val nowElapsed = timeSource.elapsedRealtime()
        val nowWall = timeSource.nowMillis()

        // worker が実行されたことは常に記録する（スケジュール取りこぼし検知に使える）
        store.update { current ->
            current.copy(
                keepAliveRunCount = current.keepAliveRunCount + 1,
                lastKeepAliveRunElapsedRealtimeMillis = nowElapsed,
                lastKeepAliveRunWallClockMillis = nowWall,
                lastKeepAliveDecision = "begin",
                lastKeepAliveErrorSummary = null,
            )
        }

        val settings = entryPoint.settingsRepository().observeOverlaySettings().first()

        // overlayEnabled が OFF の場合，何もしない（スケジュール側でも cancel するが，保険として確認する）
        if (!settings.overlayEnabled) {
            store.update { it.copy(lastKeepAliveDecision = "skip_disabled") }
            RefocusLog.d(TAG) { "overlayEnabled=false -> skip" }
            return Result.success()
        }

        // 必須権限が無い場合は起動しない（無限再起動を防ぐ）
        if (!PermissionHelper.hasAllCorePermissions(appContext)) {
            store.update { it.copy(lastKeepAliveDecision = "skip_permissions") }
            RefocusLog.d(TAG) { "core permissions missing -> skip" }
            return Result.success()
        }

        val snapshot = store.read()
        val isFresh = isHeartbeatFresh(snapshot, nowElapsed, nowWall)

        if (isFresh) {
            val lastSample = snapshot.lastForegroundSampleElapsedRealtimeMillis
            if (lastSample == null) {
                store.update { it.copy(lastKeepAliveDecision = "ok_no_sample_yet") }
                return Result.success()
            }

            val monitorFresh = isMonitorFresh(snapshot, nowElapsed)
            if (monitorFresh) {
                store.update { it.copy(lastKeepAliveDecision = "ok_fresh") }
                return Result.success()
            }

            // Service 自体は生きているが，前面アプリ監視だけが止まっている可能性があるため，軽い self-heal を試す
            store.update { it.copy(lastKeepAliveDecision = "warn_monitor_stale") }
            return try {
                appContext.requestOverlaySelfHeal()
                store.update { it.copy(lastKeepAliveDecision = "self_heal_sent") }
                Result.success()
            } catch (e: Exception) {
                store.update {
                    it.copy(
                        lastKeepAliveDecision = "self_heal_failed",
                        lastKeepAliveErrorSummary = summarizeError(e),
                    )
                }
                Result.success()
            }
        }

        // heartbeat が古い → 再起動を試みる
        store.update { current ->
            current.copy(
                keepAliveStartAttemptCount = current.keepAliveStartAttemptCount + 1,
                lastKeepAliveDecision = "start_attempt",
            )
        }

        return try {
            RefocusLog.w(TAG) {
                "heartbeat stale -> try startOverlayService, lastElapsed=${snapshot.lastHeartbeatElapsedRealtimeMillis}, lastWall=${snapshot.lastHeartbeatWallClockMillis}"
            }
            appContext.startOverlayService()

            store.update { current ->
                current.copy(
                    keepAliveStartSuccessCount = current.keepAliveStartSuccessCount + 1,
                    lastKeepAliveDecision = "start_success",
                )
            }

            Result.success()
        } catch (e: Exception) {
            val summary = summarizeError(e)
            val notAllowed = isStartNotAllowedError(e)

            store.update { current ->
                current.copy(
                    keepAliveStartFailureCount = current.keepAliveStartFailureCount + 1,
                    lastKeepAliveDecision = if (notAllowed) "start_not_allowed" else "start_failed",
                    lastKeepAliveErrorSummary = summary,
                )
            }

            if (notAllowed) {
                RefocusLog.w(TAG, e) { "Start overlay service not allowed right now. skip retry" }
                Result.success()
            } else {
                RefocusLog.e(TAG, e) { "Failed to start overlay service from keep-alive" }
                Result.retry()
            }
        }
    }

    private fun isHeartbeatFresh(
        snapshot: OverlayHealthSnapshot,
        nowElapsed: Long,
        nowWall: Long,
    ): Boolean {
        snapshot.lastHeartbeatElapsedRealtimeMillis?.let { lastElapsed ->
            val delta = nowElapsed - lastElapsed
            if (delta in 0..HEARTBEAT_FRESH_THRESHOLD_MS) return true
        }

        snapshot.lastHeartbeatWallClockMillis?.let { lastWall ->
            val delta = nowWall - lastWall
            if (delta in 0..(HEARTBEAT_FRESH_THRESHOLD_MS + CLOCK_SKEW_ALLOWANCE_MS)) return true
        }

        return false
    }

    private fun isMonitorFresh(
        snapshot: OverlayHealthSnapshot,
        nowElapsed: Long,
    ): Boolean {
        val last = snapshot.lastForegroundSampleElapsedRealtimeMillis ?: return false
        val delta = nowElapsed - last
        return delta in 0..MONITOR_FRESH_THRESHOLD_MS
    }

    private fun isStartNotAllowedError(e: Throwable): Boolean {
        // minSdk=26 のため，API 31 以降の例外クラスは直接参照せず，クラス名で判定する
        // 代表例:
        // - android.app.ForegroundServiceStartNotAllowedException (Android 12+)
        // - IllegalStateException: Background start not allowed
        val name = e::class.java.name
        if (name == "android.app.ForegroundServiceStartNotAllowedException") return true
        if (e is IllegalStateException) return true
        if (e is SecurityException) return true
        return false
    }

    private fun summarizeError(e: Throwable): String {
        val name = e::class.java.simpleName
        val msg = e.message
        val raw = if (msg.isNullOrBlank()) name else "$name: $msg"
        return if (raw.length <= ERROR_SUMMARY_MAX) raw else raw.take(ERROR_SUMMARY_MAX)
    }
}
