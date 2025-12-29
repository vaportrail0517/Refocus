package com.example.refocus.feature.stats

/**
 * 統計詳細画面の種類．
 *
 * HomeScreen など他画面からも参照されるため，値の並びや名前は既存のものを維持します．
 */
enum class StatsDetailSection {
    UsageSummary,
    AppUsage,
    Timeline,
    Suggestions,
    PeakTime,
    Monitoring,
}
