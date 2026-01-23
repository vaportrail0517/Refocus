package com.example.refocus.data.repository

import com.example.refocus.core.logging.RefocusLog
import com.example.refocus.core.model.TimelineEvent
import com.example.refocus.data.db.dao.TimelineEventDao
import com.example.refocus.data.db.entity.TimelineEventEntity
import com.example.refocus.data.db.mapper.TimelineEventEntityMapper

internal class SeedEventsLoader(
    private val dao: TimelineEventDao,
    private val mapper: TimelineEventEntityMapper,
) {
    private companion object {
        private const val TAG = "SeedEventsLoader"
    }

    suspend fun load(beforeMillis: Long): List<TimelineEvent> {
        // 状態復元に必要な最小限の「直前イベント」を拾う．
        // 完璧なスナップショットではなく，ウィンドウ購読の欠損を埋めるための保険．
        val seedEntities = mutableListOf<TimelineEventEntity>()

        dao
            .getLatestEventOfKindBefore(
                TimelineEventEntityMapper.KIND_SERVICE,
                beforeMillis,
            )?.let { seedEntities += it }
        dao
            .getLatestEventOfKindBefore(
                TimelineEventEntityMapper.KIND_SCREEN,
                beforeMillis,
            )?.let { seedEntities += it }
        dao
            .getLatestEventOfKindBefore(
                TimelineEventEntityMapper.KIND_FOREGROUND_APP,
                beforeMillis,
            )?.let { seedEntities += it }
        dao
            .getLatestEventOfKindBefore(
                TimelineEventEntityMapper.KIND_TARGET_APPS_CHANGED,
                beforeMillis,
            )?.let { seedEntities += it }

        // Permission は permissionKind ごとに直前 1 件が欲しい．
        // SQL 側で group by するより，件数が小さいことを前提に Kotlin 側でユニーク化する．
        val recentPerms =
            dao.getLatestEventsOfKindBefore(
                kind = TimelineEventEntityMapper.KIND_PERMISSION,
                beforeMillis = beforeMillis,
                limit = 32,
            )
        val pickedPermissionKinds = mutableSetOf<String>()
        for (e in recentPerms) {
            val k = e.permissionKind ?: continue
            if (pickedPermissionKinds.add(k)) {
                seedEntities += e
            }
        }

        RefocusLog.d(TAG) {
            val kindCounts = seedEntities.groupingBy { it.kind }.eachCount()
            val target =
                seedEntities
                    .firstOrNull { it.kind == TimelineEventEntityMapper.KIND_TARGET_APPS_CHANGED }
            val foreground =
                seedEntities
                    .firstOrNull { it.kind == TimelineEventEntityMapper.KIND_FOREGROUND_APP }
            val permKinds =
                seedEntities
                    .filter { it.kind == TimelineEventEntityMapper.KIND_PERMISSION }
                    .mapNotNull { it.permissionKind }
                    .distinct()
            val targetAge = target?.let { (beforeMillis - it.timestampMillis).coerceAtLeast(0L) }
            val fgAge = foreground?.let { (beforeMillis - it.timestampMillis).coerceAtLeast(0L) }
            "load(before=$beforeMillis): seedKinds=$kindCounts permissionKinds=$permKinds " +
                "targetAppsTs=${target?.timestampMillis} targetAppsAgeMs=$targetAge " +
                "foregroundTs=${foreground?.timestampMillis} foregroundAgeMs=$fgAge"
        }

        val mapped =
            seedEntities
                .mapNotNull { mapper.toDomain(it) }
                .sortedBy { it.timestampMillis }

        RefocusLog.d(TAG) {
            val kinds = seedEntities.mapNotNull { it.kind }.groupingBy { it }.eachCount()
            val permKinds = seedEntities.mapNotNull { it.permissionKind }.distinct().size
            val oldest = mapped.minOfOrNull { it.timestampMillis }
            val newest = mapped.maxOfOrNull { it.timestampMillis }
            "load(before=$beforeMillis): picked seed=${seedEntities.size} kinds=$kinds permissionKinds=$permKinds range=[${oldest ?: "-"}, ${newest ?: "-"}]"
        }

        return mapped
    }
}
