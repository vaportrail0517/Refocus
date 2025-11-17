package com.example.refocus.feature.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import com.example.refocus.ui.theme.RefocusTheme
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

/**
 * Service など Activity 以外のコンテキストから、
 * TYPE_APPLICATION_OVERLAY で Compose の View を表示するためのコントローラ。
 */
class OverlayController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
) {
    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    var overlaySettings: OverlaySettings = OverlaySettings()

    /**
     * Timer を表示する。既に表示中なら何もしない。
     * baseElapsedRealtime は起動開始時刻（SystemClock.elapsedRealtime の値）
     */
    fun showTimer(
        initialElapsedMillis: Long,
        onPositionChanged: ((x: Int, y: Int) -> Unit)? = null,
    ) {
        if (overlayView != null) {
            Log.d("OverlayController", "showTimer: already showing")
            return
        }
        Log.d("OverlayController", "showTimer: creating overlay view")
        // ★ここで最新の設定をスナップショットして使う
        val settings = overlaySettings
        // 基本フラグ
        val baseFlags =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        // タッチモードに応じて NOT_TOUCHABLE を付け足す
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
            // ★左上基準に変更
            gravity = Gravity.TOP or Gravity.START
            // ★設定から初期位置を設定
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
                        initialElapsedMillis = initialElapsedMillis,
                        settings = settings
                    )
                }
            }
            // ★タッチモードが Drag のときだけドラッグを有効化
            if (settings.touchMode == OverlayTouchMode.Drag) {
                setOnTouchListener(object : View.OnTouchListener {
                    private var initialX = 0
                    private var initialY = 0
                    private var initialTouchX = 0f
                    private var initialTouchY = 0f
                    override fun onTouch(v: View, event: MotionEvent): Boolean {
                        val lp = this@OverlayController.layoutParams ?: params
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
                                // layoutParams にも反映しておく
                                this@OverlayController.layoutParams = lp
                                return true
                            }
                            MotionEvent.ACTION_UP -> {
                                // ★ドラッグ終了時の位置をコールバック
                                onPositionChanged?.invoke(lp.x, lp.y)
                                return true
                            }
                        }
                        return false
                    }
                })
            } else {
                // PassThrough の場合はタッチハンドラなし（全て背面へ）
                setOnTouchListener(null)
            }
        }
        overlayView = composeView
        layoutParams = params
        try {
            windowManager.addView(composeView, params)
            Log.d("OverlayController", "showTimer: addView success")
        } catch (e: Exception) {
            Log.e("OverlayController", "showTimer: addView failed", e)
        }
    }






    /**
     * Timer を非表示にする。
     */
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
