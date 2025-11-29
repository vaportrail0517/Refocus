package com.example.refocus.system.overlay.window

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
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.refocus.core.model.OverlayTouchMode
import com.example.refocus.core.model.Settings
import com.example.refocus.core.util.TimeSource
import com.example.refocus.ui.overlay.TimerOverlay
import com.example.refocus.ui.theme.RefocusTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TimerOverlayController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val timeSource: TimeSource,
    private val scope: CoroutineScope,
) {
    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    // ドラッグで位置を変えたときに呼び出すコールバックを保持しておく
    private var onPositionChangedCallback: ((Int, Int) -> Unit)? = null

    // Compose が監視するステート本体
    private var overlaySettingsState by mutableStateOf(Settings())

    // Compose が監視する経過時間（TimerOverlay にそのまま渡す）
    private var elapsedMillis by mutableStateOf(0L)

    // 経過時間を更新するためのジョブ
    private var timerJob: Job? = null

    // 外から触るプロパティ。変更時に onSettingsChanged を呼ぶ
    var overlaySettings: Settings
        get() = overlaySettingsState
        set(value) {
            val old = overlaySettingsState
            overlaySettingsState = value
            onSettingsChanged(old, value)
        }

    private fun onSettingsChanged(old: Settings, new: Settings) {
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
                            this@TimerOverlayController.layoutParams = lp
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
        // SessionManager から経過時間をもらうための provider
        elapsedMillisProvider: (Long) -> Long,
        onPositionChanged: ((Int, Int) -> Unit)? = null
    ) {
        if (overlayView != null) {
            Log.d("TimerOverlayController", "showTimer: already showing")
            return
        }

        val settings = overlaySettings
        onPositionChangedCallback = onPositionChanged

        // 表示開始時に経過時間をリセット
        elapsedMillis = 0L

        // 既存のジョブがあれば止める
        timerJob?.cancel()

        // コルーチンで 200ms ごとに elapsedMillis を更新
        timerJob = scope.launch {
            while (isActive) {
                val nowElapsed = timeSource.elapsedRealtime()
                elapsedMillis = elapsedMillisProvider(nowElapsed)
                delay(200L)
            }
        }

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
                    TimerOverlay(
                        settings = overlaySettingsState,
                        elapsedMillis = elapsedMillis,
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
            Log.e("TimerOverlayController", "showTimer: addView failed", e)
        }
    }

    fun hideTimer() {
        // 経過時間更新ジョブを停止
        timerJob?.cancel()
        timerJob = null
        elapsedMillis = 0L

        val view = overlayView ?: return
        try {
            if (view is ComposeView) {
                view.setContent { } // 空のコンテンツを set → 既存 Composition が dispose される
            }
            windowManager.removeView(view)
            Log.d("TimerOverlayController", "hideTimer: removeView success")
        } catch (e: Exception) {
            Log.e("TimerOverlayController", "hideTimer: removeView failed", e)
        } finally {
            overlayView = null
            layoutParams = null
        }
    }

}