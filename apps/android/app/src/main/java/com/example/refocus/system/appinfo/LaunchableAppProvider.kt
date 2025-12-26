package com.example.refocus.system.appinfo

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

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

@Singleton
class AndroidLaunchableAppProvider @Inject constructor(
    @ApplicationContext context: Context,
) : LaunchableAppProvider {

    private val pm: PackageManager = context.packageManager
    private val usageStatsManager: UsageStatsManager? =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    private val selfPackageName: String = context.packageName

    override suspend fun loadLaunchableApps(
        lookbackMillis: Long,
        excludePackages: Set<String>,
        excludeSelf: Boolean,
    ): List<LaunchableAppInfo> {
        val exclude = HashSet<String>(excludePackages.size + 1)
        exclude.addAll(excludePackages)
        if (excludeSelf) exclude.add(selfPackageName)

        val launchIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val activities: List<ResolveInfo> = pm.queryIntentActivities(launchIntent, 0)
        val usageMap = queryUsageTime(lookbackMillis)

        return activities
            .asSequence()
            .map { info ->
                val pkg = info.activityInfo.packageName
                val label = info.loadLabel(pm).toString()

                val icon = try {
                    info.loadIcon(pm)
                } catch (_: Throwable) {
                    null
                }

                LaunchableAppInfo(
                    label = label,
                    packageName = pkg,
                    usageTimeMs = usageMap[pkg] ?: 0L,
                    icon = icon,
                )
            }
            .filter { it.packageName !in exclude }
            .sortedByDescending { it.usageTimeMs }
            .toList()
    }

    private fun queryUsageTime(lookbackMillis: Long): Map<String, Long> {
        val usm = usageStatsManager ?: return emptyMap()

        val end = System.currentTimeMillis()
        val start = (end - lookbackMillis).coerceAtLeast(0L)
        val stats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            start,
            end,
        ) ?: return emptyMap()

        val map = mutableMapOf<String, Long>()
        for (s in stats) {
            val pkg = s.packageName
            val time = s.totalTimeInForeground
            map[pkg] = (map[pkg] ?: 0L) + time
        }
        return map
    }
}
