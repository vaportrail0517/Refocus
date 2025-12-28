package com.example.refocus.system.overlay.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import com.example.refocus.core.logging.RefocusLog
import com.example.refocus.domain.timeline.EventRecorder
import com.example.refocus.system.overlay.OverlayCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class OverlayScreenStateReceiver(
    private val context: Context,
    private val scope: CoroutineScope,
    private val overlayCoordinator: OverlayCoordinator,
    private val eventRecorder: EventRecorder,
    private val onScreenOn: () -> Unit,
) {
    private var registered: Boolean = false

    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF,
                    Intent.ACTION_SHUTDOWN,
                    -> {
                        RefocusLog.d("OverlayService") { "ACTION_SCREEN_OFF / SHUTDOWN received" }
                        overlayCoordinator.setScreenOn(false)
                        overlayCoordinator.onScreenOff()

                        scope.launch {
                            try {
                                eventRecorder.onScreenOff()
                            } catch (e: Exception) {
                                RefocusLog.e(
                                    "OverlayService",
                                    e,
                                ) { "Failed to record screen off event" }
                            }
                        }
                    }

                    Intent.ACTION_USER_PRESENT,
                    Intent.ACTION_SCREEN_ON,
                    -> {
                        RefocusLog.d("OverlayService") { "ACTION_USER_PRESENT / SCREEN_ON received" }
                        overlayCoordinator.setScreenOn(true)

                        // 設定画面から戻ってきたタイミングなどで権限が変わっている可能性がある
                        onScreenOn()

                        scope.launch {
                            try {
                                eventRecorder.onScreenOn()
                            } catch (e: Exception) {
                                RefocusLog.e("OverlayService", e) { "Failed to record screen on event" }
                            }
                        }
                    }
                }
            }
        }

    fun register() {
        if (registered) return
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SHUTDOWN)
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_SCREEN_ON)
            }
        context.registerReceiver(receiver, filter)
        registered = true
    }

    fun unregister() {
        if (!registered) return
        try {
            context.unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            RefocusLog.w("OverlayService", e) { "unregisterReceiver failed" }
        } finally {
            registered = false
        }
    }

    fun syncInitialScreenState() {
        // registerReceiver は sticky ではないため，サービス起動時点の画面状態は取りこぼし得る．
        // ここで現在の状態を同期して，起動直後に screenOn の初期値が誤らないようにする．
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isInteractive = pm?.isInteractive ?: true
        RefocusLog.d("OverlayService") { "syncInitialScreenState: isInteractive=$isInteractive" }
        overlayCoordinator.setScreenOn(isInteractive)
    }
}
