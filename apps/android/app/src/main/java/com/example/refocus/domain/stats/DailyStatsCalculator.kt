package com.example.refocus.domain.stats

import com.example.refocus.core.model.AppUsageStats
import com.example.refocus.core.model.DailyStats
import com.example.refocus.core.model.Session
import com.example.refocus.core.model.SessionEvent
import com.example.refocus.core.model.SessionPart
import com.example.refocus.core.model.SessionStats
import com.example.refocus.domain.timeline.MonitoringPeriod
import com.example.refocus.domain.timeline.MonitoringProjector.buildTimeBuckets
import com.example.refocus.domain.timeline.MonitoringProjector.sumMonitoringMinutesForDay
import com.example.refocus.domain.timeline.MonitoringProjector.sumMonitoringWithTargetMinutes
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
        monitoringPeriods: List<MonitoringPeriod>,
        targetDate: LocalDate,
        zoneId: ZoneId,
        nowMillis: Long,
        bucketSizeMinutes: Int = 30,
    ): DailyStats {
        // 1. この日に関係するセッション
        val statsById = sessionStats.associateBy { it.id }

        val sessionsOnDate =
            sessions.filter { session ->
                val sessionId = session.id ?: return@filter false
                val stats = statsById[sessionId] ?: return@filter false
                val startDate =
                    Instant
                        .ofEpochMilli(stats.startedAtMillis)
                        .atZone(zoneId)
                        .toLocalDate()
                val endMillisForDate = stats.endedAtMillis ?: nowMillis
                val endDate =
                    Instant
                        .ofEpochMilli(endMillisForDate)
                        .atZone(zoneId)
                        .toLocalDate()
                !targetDate.isBefore(startDate) && !targetDate.isAfter(endDate)
            }

        val statsOnDate = sessionsOnDate.mapNotNull { it.id?.let(statsById::get) }

        val sessionCount = statsOnDate.size
        val averageDuration =
            if (sessionCount == 0) {
                0L
            } else {
                statsOnDate.map { it.durationMillis }.average().toLong()
            }
        val longestDuration = statsOnDate.maxOfOrNull { it.durationMillis } ?: 0L

        // 2. 日境界（ミリ秒）
        val dayStartMillis = targetDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val dayEndMillis =
            targetDate
                .plusDays(1)
                .atStartOfDay(zoneId)
                .toInstant()
                .toEpochMilli()

        // 3. SessionPart からこの日の利用時間
        val partsOnDate = sessionParts.filter { it.date == targetDate }
        val totalUsageMillis = partsOnDate.sumOf { it.durationMillis }

        // 4. MonitoringPeriod から、この日の監視時間を集計
        val periodsOnDate =
            monitoringPeriods.filter { period ->
                val start = period.startMillis
                val end = period.endMillis ?: dayEndMillis
                end > dayStartMillis && start < dayEndMillis
            }

        val monitoringTotalMinutes =
            sumMonitoringMinutesForDay(
                periods = periodsOnDate,
                dayStartMillis = dayStartMillis,
                dayEndMillis = dayEndMillis,
                nowMillis = nowMillis,
            )
        val monitoringWithTargetMinutes =
            sumMonitoringWithTargetMinutes(
                partsOnDate = partsOnDate,
                periods = periodsOnDate,
                dayStartMillis = dayStartMillis,
                dayEndMillis = dayEndMillis,
                nowMillis = nowMillis,
            )

        // 5. 長時間セッション数
        val longSessionCount =
            statsOnDate.count {
                it.durationMillis >= LONG_SESSION_THRESHOLD_MINUTES * 60_000L
            }
        val veryLongSessionCount =
            statsOnDate.count {
                it.durationMillis >= VERY_LONG_SESSION_THRESHOLD_MINUTES * 60_000L
            }

        // 6. アプリ別統計
        val appUsageStats =
            buildAppUsageStats(
                sessionsOnDate = sessionsOnDate,
                statsOnDate = statsOnDate,
                partsOnDate = partsOnDate,
            )

        // 7. 時間帯バケット
        val timeBuckets =
            buildTimeBuckets(
                partsOnDate = partsOnDate,
                monitoringPeriodsOnDate = periodsOnDate,
                bucketSizeMinutes = bucketSizeMinutes,
                dayStartMillis = dayStartMillis,
            )

        // 8. 提案統計
        val suggestionStats =
            SuggestionStatsCalculator.buildDailyStats(
                sessions = sessionsOnDate,
                eventsBySessionId = eventsBySessionId,
                targetDate = targetDate,
                zoneId = zoneId,
            )

        // 9. DailyStats 構築
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
        val usageByPackage =
            partsOnDate
                .groupBy { it.packageName }
                .mapValues { (_, parts) -> parts.sumOf { it.durationMillis } }

        // 2. sessionsOnDate / statsOnDate から、アプリごとの
        //    sessionCount と averageSessionDuration を計算
        val statsBySessionId = statsOnDate.associateBy { it.id }
        val sessionsByPackage = sessionsOnDate.groupBy { it.packageName }

        return usageByPackage
            .map { (pkg, totalUsage) ->
                val sessionsForApp = sessionsByPackage[pkg].orEmpty()
                val sessionIds = sessionsForApp.mapNotNull { it.id }
                val statsForApp = sessionIds.mapNotNull { statsBySessionId[it] }

                val sessionCount = statsForApp.size
                val avgSessionDuration =
                    if (sessionCount == 0) {
                        0L
                    } else {
                        statsForApp.map { it.durationMillis }.average().toLong()
                    }

                AppUsageStats(
                    packageName = pkg,
                    totalUsageMillis = totalUsage,
                    averageSessionDurationMillis = avgSessionDuration,
                    sessionCount = sessionCount,
                )
            }.sortedByDescending { it.totalUsageMillis }
    }
}
