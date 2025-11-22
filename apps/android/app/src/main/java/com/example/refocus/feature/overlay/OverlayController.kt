package com.example.refocus.feature.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.refocus.core.model.OverlaySettings
import com.example.refocus.core.model.OverlayTouchMode
import com.example.refocus.ui.theme.RefocusTheme

class OverlayController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
) {
    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var suggestionView: View? = null

    // ドラッグで位置を変えたときに呼び出すコールバックを保持しておく
    private var onPositionChangedCallback: ((Int, Int) -> Unit)? = null

    // Compose が監視するステート本体
    private var overlaySettingsState by mutableStateOf(OverlaySettings())

    // 外から触るプロパティ。変更時に onSettingsChanged を呼ぶ
    var overlaySettings: OverlaySettings
        get() = overlaySettingsState
        set(value) {
            val old = overlaySettingsState
            overlaySettingsState = value
            onSettingsChanged(old, value)
        }

    private fun onSettingsChanged(old: OverlaySettings, new: OverlaySettings) {
        val view = overlayView ?: return
        val lp = layoutParams ?: return
        // タッチモードが変わった場合のみ、flag とリスナーを差し替える
        if (old.touchMode != new.touchMode) {
            val baseFlags =
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            lp.flags = when (new.touchMode) {
                OverlayTouchMode.Drag ->
                    baseFlags

                OverlayTouchMode.PassThrough ->
                    baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            }
            windowManager.updateViewLayout(view, lp)
            applyTouchListener(
                view = view,
                lp = lp,
                touchMode = new.touchMode
            )
        }
    }

    private fun applyTouchListener(
        view: View,
        lp: WindowManager.LayoutParams,
        touchMode: OverlayTouchMode
    ) {
        if (touchMode == OverlayTouchMode.Drag) {
            view.setOnTouchListener(object : View.OnTouchListener {
                private var initialX = 0
                private var initialY = 0
                private var initialTouchX = 0f
                private var initialTouchY = 0f
                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = lp.x
                            initialY = lp.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            return true
                        }

                        MotionEvent.ACTION_MOVE -> {
                            lp.x = initialX + (event.rawX - initialTouchX).toInt()
                            lp.y = initialY + (event.rawY - initialTouchY).toInt()
                            windowManager.updateViewLayout(v, lp)
                            // 状態としても保持しておく
                            this@OverlayController.layoutParams = lp
                            return true
                        }

                        MotionEvent.ACTION_UP -> {
                            // 位置の永続化コールバック
                            onPositionChangedCallback?.invoke(lp.x, lp.y)
                            return true
                        }
                    }
                    return false
                }
            })
        } else {
            // 透過モード：タッチを受けず、そのまま背後に流す
            view.setOnTouchListener(null)
        }
    }

    fun showTimer(
        // initialElapsedMillis の代わりに provider を受け取る
        elapsedMillisProvider: (Long) -> Long,
        onPositionChanged: ((x: Int, y: Int) -> Unit)? = null,
    ) {
        if (overlayView != null) {
            Log.d("OverlayController", "showTimer: already showing")
            return
        }
        val settings = overlaySettings
        onPositionChangedCallback = onPositionChanged
        val baseFlags =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        val flags = when (settings.touchMode) {
            OverlayTouchMode.Drag ->
                baseFlags

            OverlayTouchMode.PassThrough ->
                baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = settings.positionX
            y = settings.positionY
        }
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            val savedStateOwner = OverlaySavedStateOwner()
            setViewTreeSavedStateRegistryOwner(savedStateOwner)
            setContent {
                RefocusTheme {
                    OverlayTimerBubble(
                        // ★ここで provider を渡す
                        settings = overlaySettingsState,
                        elapsedMillisProvider = elapsedMillisProvider
                    )
                }
            }
        }
        overlayView = composeView
        layoutParams = params
        applyTouchListener(
            view = composeView,
            lp = params,
            touchMode = settings.touchMode
        )
        try {
            windowManager.addView(composeView, params)
        } catch (e: Exception) {
            Log.e("OverlayController", "showTimer: addView failed", e)
        }
    }

    fun hideTimer() {
        val view = overlayView ?: return
        try {
            windowManager.removeView(view)
            Log.d("OverlayController", "hideTimer: removeView success")
        } catch (e: Exception) {
            Log.e("OverlayController", "hideTimer: removeView failed", e)
        } finally {
            overlayView = null
            layoutParams = null
        }
    }

    fun showSuggestionOverlay(
        title: String,
        mode: SuggestionOverlayMode,
        autoDismissMillis: Long,
        interactionLockoutMillis: Long,
        onSnoozeLater: () -> Unit,
        onDisableThisSession: () -> Unit,
        onDismissOnly: () -> Unit,
    ) {
        if (suggestionView != null) {
            Log.d("OverlayController", "showSuggestionOverlay: already showing")
            return
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

        suggestionView = composeView
        try {
            windowManager.addView(composeView, params)
        } catch (e: Exception) {
            Log.e("OverlayController", "showSuggestionOverlay: addView failed", e)
            suggestionView = null
        }
    }

    fun hideSuggestionOverlay() {
        val view = suggestionView ?: return
        try {
            windowManager.removeView(view)
        } catch (e: Exception) {
            Log.e("OverlayController", "hideSuggestionOverlay: removeView failed", e)
        } finally {
            suggestionView = null
        }
    }

}

/**
 * Service とは独立した Lifecycle を持つ、SavedStateRegistryOwner のダミー実装。
 *
 * - Compose 側は ViewTreeSavedStateRegistryOwner が存在することだけを要求する
 * - 実際の状態保存/復元は行わない（Bundle は常に null）
 */
private class OverlaySavedStateOwner : SavedStateRegistryOwner {

    // 自前の LifecycleRegistry を持つ
    private val lifecycleRegistry = LifecycleRegistry(this)

    // SavedStateRegistryController を自前の Lifecycle 上に構成
    private val controller = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = controller.savedStateRegistry

    init {
        // INITIALIZED の状態で attach/restore を実行する
        lifecycleRegistry.currentState = Lifecycle.State.INITIALIZED
        controller.performRestore(null)
        // 最低限 CREATED に遷移させておく（以降は特に進めなくても問題なし）
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }
}
