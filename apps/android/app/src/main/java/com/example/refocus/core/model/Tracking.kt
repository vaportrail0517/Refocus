package com.example.refocus.core.model

/**
 * Refocus が「監視できていた時間帯」を表すモデル。
 * OverlayService が起動している間などを 1 レコードとする。
 */
data class MonitoringPeriod(
    val startMillis: Long,
    val endMillis: Long?, // null = まだ継続中
)
