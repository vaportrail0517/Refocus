package com.example.refocus.domain.timeline

import com.example.refocus.core.model.SessionPart
import com.example.refocus.core.model.TimeBucketStats

/**
 * Refocus が「監視できていた時間帯」を表すモデル。
 * OverlayService が起動している間などを 1 レコードとする。
 */
data class MonitoringPeriod(
    val startMillis: Long,
    val endMillis: Long?, // null = まだ継続中
)


/**
 * MonitoringPeriod と SessionPart から、
 * - 1日あたりの監視時間（分）
 * - 1日あたりの「監視中かつ対象アプリ利用中の時間」（分）
 * - 1日を等間隔バケットに分けたタイムライン統計
 * を投影（project）するユーティリティ。
 */
object MonitoringProjector {

    /**
     * 1 日ぶんの「Refocus が監視していた合計時間（分）」を計算する。
     *
     * @param periods   その日と重なっている MonitoringPeriod 一覧
     * @param dayStartMillis その日の 0:00（ローカル）を epoch millis にしたもの
     * @param dayEndMillis   翌日の 0:00（ローカル）
     * @param nowMillis 「いま」の時刻。今日についてはここまでしかカウントしない。
     */
    fun sumMonitoringMinutesForDay(
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
    fun sumMonitoringWithTargetMinutes(
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

    /**
     * 1 日を一定分幅のバケットに分割し、
     * - そのバケットにおける監視時間（分）
     * - 対象アプリ利用時間（分）
     * - 各バケットで最も使われていたアプリ など
     * を集計して TimeBucketStats のリストに変換する。
     */
    fun buildTimeBuckets(
        partsOnDate: List<SessionPart>,
        monitoringPeriodsOnDate: List<MonitoringPeriod>,
        bucketSizeMinutes: Int,
        dayStartMillis: Long,
    ): List<TimeBucketStats> {
        // 24時間を bucketSizeMinutes 分刻みで分割
        val buckets = mutableListOf<BucketAccum>()
        var minutes = 0
        while (minutes < 24 * 60) {
            val end = (minutes + bucketSizeMinutes).coerceAtMost(24 * 60)
            buckets += BucketAccum(
                startMinutesOfDay = minutes,
                endMinutesOfDay = end,
                usageByPackage = mutableMapOf(),
                monitoringMillis = 0L,
            )
            minutes = end
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
}