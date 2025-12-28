package com.example.refocus.gateway

import android.graphics.drawable.Drawable
import java.util.concurrent.TimeUnit

/**
 * ランチャーに表示されるアプリ一覧を解決するための gateway．
 *
 * UsageStats 取得や PackageManager 参照など platform 依存を feature から切り離す．
 */
data class LaunchableAppInfo(
    val label: String,
    val packageName: String,
    val usageTimeMs: Long,
    val icon: Drawable?,
)

interface LaunchableAppProvider {
    /**
     * ランチャーに表示されるアプリ一覧を返す．
     * UsageStats が取れない場合は usageTimeMs=0 のまま返す．
     */
    suspend fun loadLaunchableApps(
        lookbackMillis: Long = TimeUnit.DAYS.toMillis(7),
        excludePackages: Set<String> = emptySet(),
        excludeSelf: Boolean = true,
    ): List<LaunchableAppInfo>
}
