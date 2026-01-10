package com.example.refocus.core.model

/**
 * オーバーレイ監視サービスの稼働健全性スナップショット．
 */
data class OverlayHealthSnapshot(
    /** 最後にハートビートを更新した時点の elapsedRealtime（ミリ秒）． */
    val lastHeartbeatElapsedRealtimeMillis: Long? = null,
    /** 最後にハートビートを更新した時点の wall-clock（epoch millis）． */
    val lastHeartbeatWallClockMillis: Long? = null,
    /** 最後に前面アプリサンプルを観測した時点の elapsedRealtime（ミリ秒）． */
    val lastForegroundSampleElapsedRealtimeMillis: Long? = null,
    /** 監視ループの再起動回数（自己復旧の回数）． */
    val monitorRestartCount: Int = 0,
    /** 直近の致命的エラー要約（あれば）． */
    val lastErrorSummary: String? = null,

    /** keep-alive worker が走った回数． */
    val keepAliveRunCount: Int = 0,
    /** keep-alive からサービス起動を試行した回数． */
    val keepAliveStartAttemptCount: Int = 0,
    /** keep-alive からサービス起動を試行し，成功扱いになった回数． */
    val keepAliveStartSuccessCount: Int = 0,
    /** keep-alive からサービス起動を試行し，失敗した回数． */
    val keepAliveStartFailureCount: Int = 0,

    /** keep-alive worker が最後に走った時点の elapsedRealtime（ミリ秒）． */
    val lastKeepAliveRunElapsedRealtimeMillis: Long? = null,
    /** keep-alive worker が最後に走った時点の wall-clock（epoch millis）． */
    val lastKeepAliveRunWallClockMillis: Long? = null,
    /** keep-alive の直近判断（skip，ok，start など）． */
    val lastKeepAliveDecision: String? = null,
    /** keep-alive の直近エラー要約（あれば）． */
    val lastKeepAliveErrorSummary: String? = null,
)
