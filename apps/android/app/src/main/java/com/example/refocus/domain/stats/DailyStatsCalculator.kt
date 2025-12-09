package com.example.refocus.domain.stats

import com.example.refocus.core.model.AppUsageStats
import com.example.refocus.core.model.DailyStats
import com.example.refocus.core.model.MonitoringPeriod
import com.example.refocus.core.model.Session
import com.example.refocus.core.model.SessionEvent
import com.example.refocus.core.model.SessionPart
import com.example.refocus.core.model.SessionStats
import com.example.refocus.core.model.TimeBucketStats
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId


private const val LONG_SESSION_THRESHOLD_MINUTES = 30
private const val VERY_LONG_SESSION_THRESHOLD_MINUTES = 60

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
        monitoringPeriods: List<MonitoringPeriod>, // ← 新規
        targetDate: LocalDate,
        zoneId: ZoneId,
        nowMillis: Long,
        bucketSizeMinutes: Int = 30,
    ): DailyStats {
        // 1. この日に関係するセッション（既存ロジック）
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

        // 日境界（ミリ秒）を計算
        val dayStartMillis = targetDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val dayEndMillis = targetDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()

        // 2. SessionPart からこの日の利用時間を計算（既存）
        val partsOnDate = sessionParts.filter { it.date == targetDate }
        val totalUsageMillis = partsOnDate.sumOf { it.durationMillis }

        // 3. MonitoringPeriod から、この日の監視時間を集計（新規）
        val periodsOnDate = monitoringPeriods.filter { period ->
            val start = period.startMillis
            val end = period.endMillis ?: dayEndMillis
            end > dayStartMillis && start < dayEndMillis
        }


        val dayStart = targetDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val dayEnd = targetDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val monitoringTotalMinutes = sumMonitoringMinutesForDay(
            periods = monitoringPeriods,
            dayStartMillis = dayStart,
            dayEndMillis = dayEnd,
            nowMillis = nowMillis,
        )
        val monitoringWithTargetMinutes = sumMonitoringWithTargetMinutes(
            partsOnDate = partsOnDate,
            periods = monitoringPeriods,
            dayStartMillis = dayStart,
            dayEndMillis = dayEnd,
            nowMillis = nowMillis,
        )

        // 4. 長時間セッション数の計算（新規）
        val longSessionCount = statsOnDate.count {
            it.durationMillis >= LONG_SESSION_THRESHOLD_MINUTES * 60_000L
        }
        val veryLongSessionCount = statsOnDate.count {
            it.durationMillis >= VERY_LONG_SESSION_THRESHOLD_MINUTES * 60_000L
        }

        // 5. アプリ別統計（既存）
        val appUsageStats = buildAppUsageStats(
            sessionsOnDate = sessionsOnDate,
            statsOnDate = statsOnDate,
            partsOnDate = partsOnDate,
        )

        // 6. 時間帯バケット（監視時間を含めるよう拡張済みの buildTimeBuckets）
        val timeBuckets = buildTimeBuckets(
            partsOnDate = partsOnDate,
            monitoringPeriodsOnDate = periodsOnDate,
            bucketSizeMinutes = bucketSizeMinutes,
            dayStartMillis = dayStartMillis,
        )

        // 7. 提案統計（必要に応じて将来追加。現状は null や別 Calculator で処理）
        val suggestionStats = null // すでにあればそれを利用

        // 8. DailyStats を構築（新フィールドを含む）
        return DailyStats(
            date = targetDate,
            monitoringTotalMinutes = monitoringTotalMinutes,
            monitoringWithTargetMinutes = monitoringWithTargetMinutes,
            sessionCount = sessionCount,
            averageSessionDurationMillis = averageDuration,
            longestSessionDurationMillis = longestDuration,
            longSessionCount = longSessionCount,
            veryLongSessionCount = veryLongSessionCount,
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
        monitoringPeriodsOnDate: List<MonitoringPeriod>,
        bucketSizeMinutes: Int,
        dayStartMillis: Long,
    ): List<TimeBucketStats> {
        val bucketCount = (24 * 60) / bucketSizeMinutes
        val buckets = MutableList(bucketCount) { index ->
            val start = index * bucketSizeMinutes
            val end = start + bucketSizeMinutes
            BucketAccum(
                startMinutesOfDay = start,
                endMinutesOfDay = end,
                usageByPackage = mutableMapOf(),
                monitoringMillis = 0L,
            )
        }

        // 1. MonitoringPeriod をバケットに反映（監視時間）
        monitoringPeriodsOnDate.forEach { period ->
            // minutes-of-day 単位に変換
            val startMinutes = ((period.startMillis - dayStartMillis) / 60_000L)
                .toInt().coerceIn(0, 24 * 60)
            val endMillis = period.endMillis ?: (dayStartMillis + 24 * 60 * 60_000L)
            val endMinutes = ((endMillis - dayStartMillis) / 60_000L)
                .toInt().coerceIn(0, 24 * 60)

            val startBucket = startMinutes / bucketSizeMinutes
            val endBucket = (endMinutes - 1).coerceAtLeast(0) / bucketSizeMinutes

            for (bucketIndex in startBucket..endBucket) {
                val bucket = buckets.getOrNull(bucketIndex) ?: continue
                val bucketStartMinutes = bucket.startMinutesOfDay
                val bucketEndMinutes = bucket.endMinutesOfDay

                val overlapStart = maxOf(bucketStartMinutes, startMinutes)
                val overlapEnd = minOf(bucketEndMinutes, endMinutes)
                if (overlapEnd <= overlapStart) continue

                val overlapMillis = (overlapEnd - overlapStart) * 60L * 1000L
                bucket.monitoringMillis += overlapMillis
            }
        }

        // 2. SessionPart（対象アプリ利用）をバケットに反映
        partsOnDate.forEach { part ->
            val startBucket = part.startMinutesOfDay / bucketSizeMinutes
            val endBucket = (part.endMinutesOfDay - 1) / bucketSizeMinutes

            for (bucketIndex in startBucket..endBucket) {
                val bucket = buckets.getOrNull(bucketIndex) ?: continue
                val overlapStart = maxOf(bucket.startMinutesOfDay, part.startMinutesOfDay)
                val overlapEnd = minOf(bucket.endMinutesOfDay, part.endMinutesOfDay)
                if (overlapEnd <= overlapStart) continue

                val overlapMillis =
                    (overlapEnd - overlapStart) * 60L * 1000L

                val usageMap = bucket.usageByPackage
                usageMap[part.packageName] =
                    (usageMap[part.packageName] ?: 0L) + overlapMillis
            }
        }

        // 3. TimeBucketStats に変換
        return buckets.map { bucket ->
            val totalUsage = bucket.usageByPackage.values.sum()
            val topPackage = bucket.usageByPackage.maxByOrNull { it.value }?.key
            val targetUsageMinutes = (totalUsage / 60_000L).toInt()
            val monitoringMinutes = (bucket.monitoringMillis / 60_000L).toInt()

            TimeBucketStats(
                startMinutesOfDay = bucket.startMinutesOfDay,
                endMinutesOfDay = bucket.endMinutesOfDay,
                monitoringMinutes = monitoringMinutes,
                targetUsageMinutes = targetUsageMinutes,
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
        var monitoringMillis: Long,
    )

    /**
     * 1 日ぶんの「Refocus が監視していた合計時間（分）」を計算する。
     *
     * @param periods   その日と重なっている MonitoringPeriod 一覧
     * @param dayStartMillis その日の 0:00（ローカル）を epoch millis にしたもの
     * @param dayEndMillis   翌日の 0:00（ローカル）
     * @param nowMillis 「いま」の時刻。今日についてはここまでしかカウントしない。
     */
    private fun sumMonitoringMinutesForDay(
        periods: List<MonitoringPeriod>,
        dayStartMillis: Long,
        dayEndMillis: Long,
        nowMillis: Long,
    ): Int {
        // 今日なら「いま」まで、それ以前の日なら 24:00 まで
        val endOfRange = minOf(dayEndMillis, nowMillis)

        var totalMillis = 0L
        for (p in periods) {
            val start = maxOf(p.startMillis, dayStartMillis)
            val rawEnd = p.endMillis ?: endOfRange
            val end = minOf(rawEnd, endOfRange)

            if (end > start) {
                totalMillis += (end - start)
            }
        }
        return (totalMillis / 60_000L).toInt()
    }


    /**
     * 1 日ぶんの「Refocus が監視していて、かつ対象アプリを使っていた時間（分）」を計算する。
     */
    private fun sumMonitoringWithTargetMinutes(
        partsOnDate: List<SessionPart>,
        periods: List<MonitoringPeriod>,
        dayStartMillis: Long,
        dayEndMillis: Long,
        nowMillis: Long,
    ): Int {
        val endOfRange = minOf(dayEndMillis, nowMillis)
        var totalMillis = 0L

        for (part in partsOnDate) {
            val partStart = maxOf(part.startDateTime.toEpochMilli(), dayStartMillis)
            val partEnd = minOf(part.endDateTime.toEpochMilli(), endOfRange)
            if (partEnd <= partStart) continue

            for (p in periods) {
                val periodStart = maxOf(p.startMillis, dayStartMillis)
                val rawPeriodEnd = p.endMillis ?: endOfRange
                val periodEnd = minOf(rawPeriodEnd, endOfRange)
                if (periodEnd <= periodStart) continue

                val overlapStart = maxOf(partStart, periodStart)
                val overlapEnd = minOf(partEnd, periodEnd)
                if (overlapEnd > overlapStart) {
                    totalMillis += (overlapEnd - overlapStart)
                }
            }
        }

        return (totalMillis / 60_000L).toInt()
    }
}
