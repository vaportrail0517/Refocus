package com.example.refocus.domain.stats

import com.example.refocus.core.model.AppUsageStats
import com.example.refocus.core.model.DailyStats
import com.example.refocus.core.model.Session
import com.example.refocus.core.model.SessionEvent
import com.example.refocus.core.model.SessionPart
import com.example.refocus.core.model.SessionStats
import com.example.refocus.core.model.TimeBucketStats
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 日別統計を計算する純粋なユーティリティ。
 *
 * フェーズ1では「セッション数・平均・最長」と「日合計利用時間」だけ計算する。
 */
object DailyStatsCalculator {

    /**
     * 指定した日付の DailyStats を計算する。
     *
     * @param sessions 全セッション一覧（終了・未終了含む）
     * @param sessionStats SessionStatsCalculator から得た 1セッション単位の統計
     * @param sessionParts SessionPartGenerator から得た日付ごとの切片
     * @param eventsBySessionId
     * @param targetDate 日付
     * @param zoneId セッションが属するローカルタイムゾーン
     * @param bucketSizeMinutes バケット幅（デフォルト 30 分）
     */
    fun calculateDailyStats(
        sessions: List<Session>,
        sessionStats: List<SessionStats>,
        sessionParts: List<SessionPart>,
        eventsBySessionId: Map<Long, List<SessionEvent>>,
        targetDate: LocalDate,
        zoneId: ZoneId,
        bucketSizeMinutes: Int = 30,
    ): DailyStats {
        // この日に「少しでも関係している」セッションを集める
        val sessionsOnDate = sessions.filter { session ->
            val stats = sessionStats.find { it.id == session.id } ?: return@filter false
            val startDate = Instant.ofEpochMilli(stats.startedAtMillis)
                .atZone(zoneId).toLocalDate()
            val endDate = stats.endedAtMillis?.let {
                Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate()
            } ?: startDate
            !targetDate.isBefore(startDate) && !targetDate.isAfter(endDate)
        }

        val statsOnDate = sessionStats.filter { stats ->
            sessionsOnDate.any { it.id == stats.id }
        }

        val sessionCount = statsOnDate.size
        val averageDuration =
            if (sessionCount == 0) 0L
            else statsOnDate.map { it.durationMillis }.average().toLong()
        val longestDuration = statsOnDate.maxOfOrNull { it.durationMillis } ?: 0L

        // SessionPart から「この日の合計利用時間」を計算
        val partsOnDate = sessionParts.filter { it.date == targetDate }
        val totalUsageMillis = partsOnDate.sumOf { it.durationMillis }

        // アプリ別統計
        val appUsageStats = buildAppUsageStats(
            sessionsOnDate = sessionsOnDate,
            statsOnDate = statsOnDate,
            partsOnDate = partsOnDate,
        )

        // 時間帯バケット統計
        val timeBuckets = buildTimeBuckets(
            partsOnDate = partsOnDate,
            bucketSizeMinutes = bucketSizeMinutes,
        )

        val suggestionStats = SuggestionStatsCalculator.buildDailyStats(
            sessions = sessions,
            eventsBySessionId = eventsBySessionId,
            targetDate = targetDate,
            zoneId = zoneId,
        )

        return DailyStats(
            date = targetDate,
            sessionCount = sessionCount,
            averageSessionDurationMillis = averageDuration,
            longestSessionDurationMillis = longestDuration,
            totalUsageMillis = totalUsageMillis,
            appUsageStats = appUsageStats,
            timeBuckets = timeBuckets,
            suggestionStats = suggestionStats,
        )
    }

    private fun buildAppUsageStats(
        sessionsOnDate: List<Session>,
        statsOnDate: List<SessionStats>,
        partsOnDate: List<SessionPart>,
    ): List<AppUsageStats> {
        // 1. partsOnDate をアプリでグループ化して、totalUsage を計算
        val usageByPackage = partsOnDate
            .groupBy { it.packageName }
            .mapValues { (_, parts) -> parts.sumOf { it.durationMillis } }

        // 2. sessionsOnDate / statsOnDate から、アプリごとの
        //    sessionCount と averageSessionDuration を計算
        val statsBySessionId = statsOnDate.associateBy { it.id }
        val sessionsByPackage = sessionsOnDate.groupBy { it.packageName }

        return usageByPackage.map { (pkg, totalUsage) ->
            val sessionsForApp = sessionsByPackage[pkg].orEmpty()
            val sessionIds = sessionsForApp.mapNotNull { it.id }
            val statsForApp = sessionIds.mapNotNull { statsBySessionId[it] }

            val sessionCount = statsForApp.size
            val avgSessionDuration =
                if (sessionCount == 0) 0L
                else statsForApp.map { it.durationMillis }.average().toLong()

            AppUsageStats(
                packageName = pkg,
                totalUsageMillis = totalUsage,
                averageSessionDurationMillis = avgSessionDuration,
                sessionCount = sessionCount,
            )
        }.sortedByDescending { it.totalUsageMillis }
    }

    private fun buildTimeBuckets(
        partsOnDate: List<SessionPart>,
        bucketSizeMinutes: Int,
    ): List<TimeBucketStats> {
        if (partsOnDate.isEmpty()) return emptyList()

        val bucketCount = (24 * 60) / bucketSizeMinutes
        val buckets = MutableList(bucketCount) { index ->
            val start = index * bucketSizeMinutes
            val end = start + bucketSizeMinutes
            // 一旦空の集計
            BucketAccum(
                startMinutesOfDay = start,
                endMinutesOfDay = end,
                usageByPackage = mutableMapOf(),
            )
        }

        // 各 SessionPart を、交差するバケットに割り当てていく
        for (part in partsOnDate) {
            val partStart = part.startMinutesOfDay
            val partEnd = part.endMinutesOfDay

            val firstBucketIndex = partStart / bucketSizeMinutes
            val lastBucketIndex = (partEnd - 1).coerceAtLeast(partStart) / bucketSizeMinutes

            for (i in firstBucketIndex..lastBucketIndex) {
                val bucket = buckets.getOrNull(i) ?: continue
                val overlapStart = maxOf(partStart, bucket.startMinutesOfDay)
                val overlapEnd = minOf(partEnd, bucket.endMinutesOfDay)
                if (overlapStart >= overlapEnd) continue

                val overlapMillis =
                    (overlapEnd - overlapStart) * 60L * 1000L

                val usageMap = bucket.usageByPackage
                usageMap[part.packageName] =
                    (usageMap[part.packageName] ?: 0L) + overlapMillis
            }
        }

        return buckets.map { bucket ->
            val totalUsage = bucket.usageByPackage.values.sum()
            val topPackage = bucket.usageByPackage.maxByOrNull { it.value }?.key
            TimeBucketStats(
                startMinutesOfDay = bucket.startMinutesOfDay,
                endMinutesOfDay = bucket.endMinutesOfDay,
                totalUsageMillis = totalUsage,
                topPackageName = topPackage,
            )
        }
    }

    // 内部用の集計構造
    private data class BucketAccum(
        val startMinutesOfDay: Int,
        val endMinutesOfDay: Int,
        val usageByPackage: MutableMap<String, Long>,
    )
}
