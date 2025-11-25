package com.example.refocus.core.model

enum class OverlaySuggestionMode {
    Goal,   // 「やりたいこと」モード
    Rest    // 「休憩」モード
}

enum class OverlayTouchMode {
    Drag,        // タイマーをドラッグして動かす
    PassThrough, // タップは背面の UI にそのまま渡す
}

/**
 * タイマーの成長モード。
 * p: 0〜1（timeToMaxMinutes に対する経過割合）
 *
 * - Linear: 線形
 * - FastToSlow: 初めは速く、大きくなるにつれてゆっくり
 * - SlowToFast: 初めはゆっくり、後半は速く
 * - SlowFastSlow: 真ん中あたりで一番速い（スローインアウト）
 */
enum class OverlayGrowthMode {
    Linear,
    FastToSlow,
    SlowToFast,
    SlowFastSlow,
}

/**
 * タイマー背景色のモード。
 *
 * - Fixed: 単色
 * - GradientTwo: 2色グラデーション
 * - GradientThree: 3色グラデーション
 */
enum class OverlayColorMode {
    Fixed,
    GradientTwo,
    GradientThree,
}