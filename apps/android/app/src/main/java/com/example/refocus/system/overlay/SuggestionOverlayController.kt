package com.example.refocus.system.overlay

import android.content.Context
import android.graphics.PixelFormat
import com.example.refocus.core.logging.RefocusLog
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.refocus.core.model.SuggestionMode
import com.example.refocus.ui.overlay.SuggestionOverlay
import com.example.refocus.ui.theme.RefocusTheme

class SuggestionOverlayController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
) {
    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var suggestionView: View? = null

    fun showSuggestionOverlay(
        title: String,
        mode: SuggestionMode,
        autoDismissMillis: Long,
        interactionLockoutMillis: Long,
        onSnoozeLater: () -> Unit,
        onDisableThisSession: () -> Unit,
        onDismissOnly: () -> Unit,
    ): Boolean {
        if (suggestionView != null) {
            RefocusLog.d("SuggestionOverlay") { "showSuggestionOverlay: already showing" }
            return true
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            val savedStateOwner = OverlaySavedStateOwner()
            setViewTreeSavedStateRegistryOwner(savedStateOwner)
            setContent {
                RefocusTheme {
                    SuggestionOverlay(
                        title = title,
                        mode = mode,
                        autoDismissMillis = autoDismissMillis,
                        interactionLockoutMillis = interactionLockoutMillis,
                        onSnoozeLater = {
                            hideSuggestionOverlay()
                            onSnoozeLater()
                        },
                        onDisableThisSession = {
                            hideSuggestionOverlay()
                            onDisableThisSession()
                        },
                        onDismissOnly = {
                            hideSuggestionOverlay()
                            onDismissOnly()
                        }
                    )
                }
            }
        }

        try {
            windowManager.addView(composeView, params)
            suggestionView = composeView
            return true
        } catch (e: Exception) {
            RefocusLog.e("SuggestionOverlay", e) { "showSuggestionOverlay: addView failed" }
            suggestionView = null
            return false
        }
    }

    fun hideSuggestionOverlay() {
        val view = suggestionView ?: return
        try {
            windowManager.removeView(view)
        } catch (e: Exception) {
            RefocusLog.e("SuggestionOverlay", e) { "hideSuggestionOverlay: removeView failed" }
        } finally {
            suggestionView = null
        }
    }
}