package com.example.refocus.domain.overlay

import com.example.refocus.core.model.Customize

/**
 * OverlayCoordinator が保持する「ランタイム状態」を 1 つの StateFlow で集約するためのモデル．
 *
 * 永続化される設定（Customize）は SettingsDataStore 側が正としつつ，
 * Coordinator 内では「最新設定スナップショット」を状態として保持し，
 * タイマー表示や判定で参照する．
 */
data class OverlayRuntimeState(
    val isScreenOn: Boolean = true,
    val currentForegroundPackage: String? = null,
    val overlayPackage: String? = null,
    val trackingPackage: String? = null,
    val timerVisible: Boolean = false,
    val overlayState: OverlayState = OverlayState.Idle,
    val lastTargetPackages: Set<String> = emptySet(),

    /**
     * 「この論理セッションだけ」オーバーレイタイマーを非表示にするフラグ．
     * キーは packageName．
     */
    val timerSuppressedForSession: Map<String, Boolean> = emptyMap(),

    /**
     * 最新の設定スナップショット．
     * SettingsDataStore の Flow を購読して差し替える．
     */
    val customize: Customize = Customize(),
)
