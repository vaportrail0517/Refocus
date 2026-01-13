package com.example.refocus.feature.home

/**
 * OverlayService の起動失敗をホーム画面で表示するための UI モデル．
 */
data class ServiceStartFailureUiModel(
    val occurredAtText: String,
    val source: String?,
    val summary: String,
) {
    val summaryShort: String
        get() = if (summary.length <= 90) summary else summary.take(90) + "…"
}
