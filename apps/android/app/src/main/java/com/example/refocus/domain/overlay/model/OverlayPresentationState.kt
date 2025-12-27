package com.example.refocus.domain.overlay.model

import com.example.refocus.core.model.TimerTimeMode
import com.example.refocus.core.model.TimerTouchMode

/**
 * system 層（Service / Notification / OverlayView）へ渡すための，
 * 「表示に必要な状態」をひとまとめにしたスナップショット．
 *
 * - domain 側で「表示値の意味」を統一し，system 側は購読して描画するだけにする．
 * - アプリラベル解決など Android 依存の情報は system 側で解決する（ここには入れない）．
 */
data class OverlayPresentationState(
    val trackingPackage: String?,
    val timerDisplayMillis: Long?,
    val isTimerVisible: Boolean,
    val touchMode: TimerTouchMode,
    val timerTimeMode: TimerTimeMode,
) {
    val isTracking: Boolean get() = trackingPackage != null
}