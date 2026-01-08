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

class MiniGameOverlayController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
) {
    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var miniGameView: View? = null

    fun showMiniGameOverlay(model: MiniGameOverlayUiModel): Boolean {
        if (miniGameView != null) {
            RefocusLog.d("MiniGameOverlay") { "showMiniGameOverlay: already showing" }
            return true
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
                                hideMiniGameOverlay()
                                model.onFinished()
                            },
                        )
                    }
                }
            }

        return try {
            windowManager.addView(composeView, params)
            miniGameView = composeView
            true
        } catch (e: Exception) {
            RefocusLog.e("MiniGameOverlay", e) { "showMiniGameOverlay: addView failed" }
            miniGameView = null
            false
        }
    }

    fun hideMiniGameOverlay() {
        val view = miniGameView ?: return
        try {
            windowManager.removeView(view)
        } catch (e: Exception) {
            RefocusLog.e("MiniGameOverlay", e) { "hideMiniGameOverlay: removeView failed" }
        } finally {
            miniGameView = null
        }
    }
}
