package com.example.refocus.system.overlay.service

import com.example.refocus.core.logging.RefocusLog
import com.example.refocus.core.model.OverlayHealthSnapshot
import com.example.refocus.core.util.TimeSource
import com.example.refocus.domain.overlay.port.OverlayHealthStore
import com.example.refocus.domain.overlay.port.OverlayServiceStatusProvider
import com.example.refocus.system.overlay.OverlayService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OverlayServiceStatusProviderImpl
    @Inject
    constructor(
        private val overlayHealthStore: OverlayHealthStore,
        private val timeSource: TimeSource,
    ) : OverlayServiceStatusProvider {
        companion object {
            private const val TAG = "OverlaySvcStatus"
            private const val HEARTBEAT_FRESH_THRESHOLD_MS = 30_000L
            private const val POLL_INTERVAL_MS = 5_000L
            private const val CLOCK_SKEW_ALLOWANCE_MS = 5 * 60_000L
        }

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        @Volatile
        private var cachedIsRunning: Boolean = false

        @Volatile
        private var hasLoadedSnapshot: Boolean = false

        init {
            scope.launch {
                while (isActive) {
                    refreshOnce()
                    delay(POLL_INTERVAL_MS)
                }
            }
        }

        override fun isRunning(): Boolean {
            if (!hasLoadedSnapshot) {
                scope.launch { refreshOnce() }
                return OverlayService.isRunning
            }
            return cachedIsRunning
        }

        private suspend fun refreshOnce() {
            try {
                val snapshot = overlayHealthStore.read()
                cachedIsRunning = computeIsRunning(snapshot)
                hasLoadedSnapshot = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                RefocusLog.w(TAG, e) { "Failed to refresh overlay running status from health store" }
                if (!hasLoadedSnapshot) {
                    cachedIsRunning = OverlayService.isRunning
                    hasLoadedSnapshot = true
                }
            }
        }

        private fun computeIsRunning(snapshot: OverlayHealthSnapshot): Boolean {
            val nowElapsed = timeSource.elapsedRealtime()
            val nowWall = timeSource.nowMillis()

            val lastElapsed = snapshot.lastHeartbeatElapsedRealtimeMillis
            val lastWall = snapshot.lastHeartbeatWallClockMillis

            val elapsedFresh =
                lastElapsed != null &&
                    nowElapsed >= lastElapsed &&
                    (nowElapsed - lastElapsed) <= HEARTBEAT_FRESH_THRESHOLD_MS

            val wallFresh =
                lastWall != null &&
                    (nowWall + CLOCK_SKEW_ALLOWANCE_MS) >= lastWall &&
                    (nowWall - lastWall) <= (HEARTBEAT_FRESH_THRESHOLD_MS + CLOCK_SKEW_ALLOWANCE_MS)

            return elapsedFresh || wallFresh
        }
    }
