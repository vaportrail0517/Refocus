package com.example.refocus.domain.overlay.port

/**
 * Overlay 監視サービスの keep-alive スケジュール制御．
 *
 * overlayEnabled の変更に追従して，定期チェック（WorkManager 等）を有効化／無効化するために使う．
 */
interface OverlayKeepAliveScheduler {
    /** overlayEnabled の変更を通知する． */
    fun onOverlayEnabledChanged(enabled: Boolean)
}
