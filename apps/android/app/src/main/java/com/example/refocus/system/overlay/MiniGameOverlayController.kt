package com.example.refocus.system.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.refocus.core.logging.RefocusLog
import com.example.refocus.domain.overlay.port.MiniGameOverlayUiModel
import com.example.refocus.system.overlay.ui.minigame.MiniGameHostOverlay
import com.example.refocus.ui.theme.RefocusTheme
import java.util.concurrent.atomic.AtomicBoolean

class MiniGameOverlayController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
) {
    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var miniGameView: View? = null
    private var miniGameToken: Long? = null

    fun showMiniGameOverlay(
        model: MiniGameOverlayUiModel,
        token: Long? = null,
    ): Boolean {
        // すでに表示中のものがある場合でも，「別トークンで再表示」なら入れ替える。
        val existing = miniGameView
        if (existing != null) {
            if (token != null && miniGameToken == token) {
                RefocusLog.d("MiniGameOverlay") { "showMiniGameOverlay: already showing (same token)" }
                return true
            }

            // 以前の view が残っている状態で再表示されたケース（hide がまだ Main に積まれている等）
            // -> いったん安全に remove して入れ替える。
            try {
                if (existing is ComposeView) {
                    existing.setContent { }
                }
                windowManager.removeView(existing)
            } catch (e: Exception) {
                RefocusLog.e("MiniGameOverlay", e) { "showMiniGameOverlay: removeView(existing) failed" }
            } finally {
                miniGameView = null
                miniGameToken = null
            }
        }

        val callbackFired = AtomicBoolean(false)

        fun runOnce(block: () -> Unit) {
            if (callbackFired.compareAndSet(false, true)) {
                block()
            }
        }

        val params =
            WindowManager
                .LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                }

        val composeView =
            ComposeView(context).apply {
                setViewTreeLifecycleOwner(lifecycleOwner)
                val savedStateOwner = OverlaySavedStateOwner()
                setViewTreeSavedStateRegistryOwner(savedStateOwner)
                setContent {
                    RefocusTheme {
                        MiniGameHostOverlay(
                            kind = model.kind,
                            seed = model.seed,
                            onFinished = {
                                runOnce {
                                    hideMiniGameOverlay(token = token)
                                    model.onFinished()
                                }
                            },
                        )
                    }
                }
            }

        return try {
            windowManager.addView(composeView, params)
            miniGameView = composeView
            miniGameToken = token
            true
        } catch (e: Exception) {
            RefocusLog.e("MiniGameOverlay", e) { "showMiniGameOverlay: addView failed" }
            miniGameView = null
            miniGameToken = null
            false
        }
    }

    fun hideMiniGameOverlay(token: Long? = null) {
        val view = miniGameView ?: return
        if (token != null && miniGameToken != token) {
            RefocusLog.d("MiniGameOverlay") { "hideMiniGameOverlay: token mismatch (ignore)" }
            return
        }
        try {
            if (view is ComposeView) {
                view.setContent { }
            }
            windowManager.removeView(view)
        } catch (e: Exception) {
            RefocusLog.e("MiniGameOverlay", e) { "hideMiniGameOverlay: removeView failed" }
        } finally {
            miniGameView = null
            miniGameToken = null
        }
    }
}
